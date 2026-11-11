package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.helpdesk.helpdesk.dto.UsuarioRequest;
import com.helpdesk.helpdesk.dto.UsuarioUpdateRequest;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

/** Verifica as duas ordens da disputa no PostgreSQL exclusivo de testes. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL",
        matches = "jdbc:postgresql://.+/helpdesk_concorrencia_test(?:\\?.*)?")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.profiles.active=test",
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '10s'",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DirtiesContext
class DepartamentoConcorrenciaTest {

    @Autowired
    private DepartamentoService departamentoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlatformTransactionManager gerenciadorDeTransacoes;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long origemId;
    private Long destinoId;
    private Long usuarioId;

    @BeforeEach
    void prepararDados() {
        usuarioRepository.deleteAllInBatch();
        departamentoRepository.deleteAllInBatch();
        Departamento origem = departamentoRepository.save(new Departamento("Origem"));
        Departamento destino = departamentoRepository.save(new Departamento("Destino"));
        Usuario usuario = usuarioRepository.save(new Usuario("Ana", "ana@teste.com", "hash", null, origem));
        origemId = origem.getId();
        destinoId = destino.getId();
        usuarioId = usuario.getId();
    }

    @ParameterizedTest
    @CsvSource({ "CADASTRO, true", "TRANSFERENCIA, true", "REATIVACAO, true",
            "CADASTRO, false", "TRANSFERENCIA, false", "REATIVACAO, false" })
    void deveManterDepartamentoEUsuariosConsistentes(Operacao operacao, boolean desativarPrimeiro) throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);

        if (operacao == Operacao.REATIVACAO) {
            transacao.executeWithoutResult(status -> {
                Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
                usuario.setAtivo(false);
                usuario.setDepartamento(departamentoRepository.findById(destinoId).orElseThrow());
            });
        }

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> segundaTentativa = transacao.execute(status -> {
                if (desativarPrimeiro) {
                    departamentoService.desativar(destinoId);
                } else {
                    vincularUsuario(operacao);
                }

                Future<?> tentativa = executor.submit(() -> transacao.executeWithoutResult(outroStatus -> {
                    // A leitura anterior à espera também precisa ser atualizada depois do bloqueio.
                    departamentoService.buscarEntidade(destinoId);
                    if (desativarPrimeiro) {
                        vincularUsuario(operacao);
                    } else {
                        departamentoService.desativar(destinoId);
                    }
                }));
                aguardarSegundaTransacaoNoBanco();
                return tentativa;
            });

            String mensagem = desativarPrimeiro
                    ? "O departamento \"Destino\" está desativado."
                    : "Não é possível desativar o departamento \"Destino\": ele tem 1 usuário(s) ativo(s). "
                            + "Mova-os para outro departamento antes.";
            assertThatThrownBy(() -> segundaTentativa.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RegraNegocioException.class)
                    .hasRootCauseMessage(mensagem);
        }

        Departamento destino = departamentoRepository.findById(destinoId).orElseThrow();
        assertThat(destino.isAtivo()).isEqualTo(!desativarPrimeiro);
        assertThat(usuarioRepository.countByDepartamentoIdAndAtivoTrue(destinoId))
                .isEqualTo(desativarPrimeiro ? 0 : 1);
        if (desativarPrimeiro) {
            // A transferência recusada preserva a origem; o cadastro recusado não cria conta.
            Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
            if (operacao == Operacao.REATIVACAO) {
                assertThat(usuario.isAtivo()).isFalse();
            } else {
                assertThat(usuario.getDepartamento().getId()).isEqualTo(origemId);
            }
            assertThat(usuarioRepository.existsByEmailIgnoreCase("novo@teste.com")).isFalse();
        }
    }

    private void vincularUsuario(Operacao operacao) {
        if (operacao == Operacao.CADASTRO) {
            usuarioService.criar(new UsuarioRequest("Novo usuário", "novo@teste.com", "segredo123",
                    null, null, null, destinoId));
        } else if (operacao == Operacao.TRANSFERENCIA) {
            usuarioService.atualizar(usuarioId, new UsuarioUpdateRequest("Ana", "ana@teste.com",
                    null, null, null, destinoId));
        } else {
            usuarioService.ativar(usuarioId);
        }
    }

    private void aguardarSegundaTransacaoNoBanco() {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            // Confirma que a segunda operação espera pelo bloqueio da conexão atual.
            Boolean esperando = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1 from pg_locks
                        where not granted
                          and pg_backend_pid() = any(pg_blocking_pids(pid))
                    )
                    """, Boolean.class);
            if (Boolean.TRUE.equals(esperando)) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("O teste foi interrompido enquanto aguardava o bloqueio.", ex);
            }
        }
        throw new AssertionError("A segunda operação não chegou ao bloqueio do PostgreSQL em 5 segundos.");
    }

    private enum Operacao {
        CADASTRO,
        TRANSFERENCIA,
        REATIVACAO
    }
}
