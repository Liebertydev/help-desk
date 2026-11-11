package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

/** Usa exclusivamente o mesmo banco PostgreSQL isolado do teste de concorrência de chamados. */
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
class AdministradorConcorrenciaTest {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private PlatformTransactionManager gerenciadorDeTransacoes;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long primeiroAdminId;
    private Long segundoAdminId;

    @BeforeEach
    void prepararDados() {
        // O banco é exclusivo deste teste. Cada combinação começa com dois admins ativos.
        usuarioRepository.deleteAllInBatch();
        departamentoRepository.deleteAllInBatch();
        Departamento departamento = departamentoRepository.save(new Departamento("TI do teste"));

        // O ponto de bloqueio também precisa funcionar quando o primeiro usuário é comum
        // e está desativado. Ele não deve depender do conjunto atual de admins ativos.
        Usuario primeiroUsuario = new Usuario("Primeiro usuário", "primeiro@teste.com", "hash", null, departamento);
        primeiroUsuario.setAtivo(false);
        usuarioRepository.save(primeiroUsuario);

        primeiroAdminId = criarAdmin("Carlos", departamento).getId();
        segundoAdminId = criarAdmin("Mariana", departamento).getId();
    }

    @ParameterizedTest
    @CsvSource({
            "DESATIVAR, DESATIVAR",
            "REBAIXAR, REBAIXAR",
            "DESATIVAR, REBAIXAR",
            "REBAIXAR, DESATIVAR"
    })
    void devePreservarUmAdminAtivoEmOperacoesSimultaneas(Operacao primeira, Operacao segunda) throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> segundaTentativa = transacao.execute(status -> {
                executarOperacao(primeira, primeiroAdminId);

                Future<?> tentativa = executor.submit(() -> executarOperacao(segunda, segundoAdminId));
                // O primeiro admin ainda não concluiu sua transação. Confirmamos que a
                // segunda operação está esperando antes de permitir o primeiro commit.
                aguardarSegundaTransacaoNoBanco();
                return tentativa;
            });

            String acao = segunda == Operacao.DESATIVAR ? "desativar" : "rebaixar";
            assertThatThrownBy(() -> segundaTentativa.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RegraNegocioException.class)
                    .hasRootCauseMessage("Não é possível " + acao + " o último administrador ativo do sistema.");
        }

        Usuario primeiro = usuarioRepository.findById(primeiroAdminId).orElseThrow();
        assertThat(primeiro.isAtivo() && primeiro.getRole() == Role.ADMIN).isFalse();
        garantirQueOAdminPermaneceuAtivo(segundoAdminId);
    }

    @Test
    void deveRecarregarContaConsultadaAntesDeEsperarPeloBloqueio() throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);
        transacao.executeWithoutResult(status -> {
            Usuario segundo = usuarioRepository.findById(segundoAdminId).orElseThrow();
            segundo.setRole(Role.USER);
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> segundaTentativa = transacao.execute(status -> {
                // O primeiro admin transfere a administração para a segunda conta.
                // As duas mudanças ainda não foram confirmadas para outras transações.
                usuarioService.alterarRole(segundoAdminId, Role.ADMIN, primeiroAdminId);
                usuarioService.alterarRole(primeiroAdminId, Role.USER, primeiroAdminId);

                Future<?> tentativa = executor.submit(() -> transacao.executeWithoutResult(outroStatus -> {
                    Usuario leituraAntiga = usuarioRepository.findById(segundoAdminId).orElseThrow();
                    assertThat(leituraAntiga.getRole()).isEqualTo(Role.USER);

                    // Antes de esperar, esta transação ainda enxerga USER. Depois da espera,
                    // a conta já será o único ADMIN e não poderá ser desativada.
                    usuarioService.desativar(segundoAdminId, primeiroAdminId);
                }));
                aguardarSegundaTransacaoNoBanco();
                return tentativa;
            });

            assertThatThrownBy(() -> segundaTentativa.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RegraNegocioException.class)
                    .hasRootCauseMessage("Não é possível desativar o último administrador ativo do sistema.");
        }

        garantirQueOAdminPermaneceuAtivo(segundoAdminId);
    }

    private Usuario criarAdmin(String nome, Departamento departamento) {
        Usuario admin = new Usuario(nome, nome.toLowerCase() + "@teste.com", "hash", null, departamento);
        admin.setRole(Role.ADMIN);
        return usuarioRepository.save(admin);
    }

    private void executarOperacao(Operacao operacao, Long usuarioId) {
        if (operacao == Operacao.DESATIVAR) {
            usuarioService.desativar(usuarioId, usuarioId);
        } else {
            usuarioService.alterarRole(usuarioId, Role.USER, usuarioId);
        }
    }

    private void garantirQueOAdminPermaneceuAtivo(Long adminId) {
        Usuario admin = usuarioRepository.findById(adminId).orElseThrow();
        assertThat(admin.isAtivo()).isTrue();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN)).isEqualTo(1);
    }

    private void aguardarSegundaTransacaoNoBanco() {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (System.nanoTime() < limite) {
            // Verifica uma espera causada pela conexão atual, sem presumir que uma
            // thread já chegou ao banco apenas por ter sido iniciada.
            Boolean segundaTransacaoEsperando = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1 from pg_locks
                        where not granted
                          and pg_backend_pid() = any(pg_blocking_pids(pid))
                    )
                    """, Boolean.class);
            if (Boolean.TRUE.equals(segundaTransacaoEsperando)) {
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
        DESATIVAR,
        REBAIXAR
    }
}
