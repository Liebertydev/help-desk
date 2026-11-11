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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.helpdesk.helpdesk.dto.ChamadoResponse;
import com.helpdesk.helpdesk.dto.HistoricoChamadoResponse;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.TipoHistorico;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.ChamadoRepository;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

/**
 * Teste com PostgreSQL real e duas transações independentes.
 * Só executa com uma URL explícita para o banco helpdesk_concorrencia_test.
 * As tabelas desse banco de teste são criadas e apagadas: não usar dados reais nele.
 */
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
class ChamadoConcorrenciaTest {

    @Autowired
    private ChamadoService chamadoService;

    @Autowired
    private ChamadoRepository chamadoRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlatformTransactionManager gerenciadorDeTransacoes;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long chamadoId;
    private Long solicitanteId;
    private Long primeiroTecnicoId;
    private Long segundoTecnicoId;

    @BeforeEach
    void prepararDados() {
        Departamento departamento = departamentoRepository.save(new Departamento("TI do teste"));
        Usuario solicitante = criarUsuario("Solicitante", Role.USER, departamento);
        Usuario primeiroTecnico = criarUsuario("Carlos", Role.TECHNICIAN, departamento);
        Usuario segundoTecnico = criarUsuario("Mariana", Role.TECHNICIAN, departamento);
        Chamado chamado = chamadoRepository.save(new Chamado("Sem internet", "A rede caiu.",
                null, null, null, solicitante, null, departamento));

        chamadoId = chamado.getId();
        solicitanteId = solicitante.getId();
        primeiroTecnicoId = primeiroTecnico.getId();
        segundoTecnicoId = segundoTecnico.getId();
    }

    @Test
    void apenasUmTecnicoDeveConseguirAssumirOMesmoChamado() throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<ChamadoResponse> segundaTentativa = transacao.execute(status -> {
                // A primeira atribuição já gravou, mas a transação ainda não terminou.
                // Assim, o primeiro técnico mantém o bloqueio enquanto o segundo tenta assumir.
                chamadoService.atribuirTecnico(chamadoId, primeiroTecnicoId, primeiroTecnicoId);

                Future<ChamadoResponse> tentativa = executor.submit(() ->
                        chamadoService.atribuirTecnico(chamadoId, segundoTecnicoId, segundoTecnicoId));

                // Só liberamos o primeiro técnico depois de confirmar no PostgreSQL
                // que a segunda transação chegou ao banco e está esperando seu bloqueio.
                aguardarSegundaTransacaoNoBanco();
                return tentativa;
            });

            // A primeira transação terminou. A segunda deve reler o chamado já atribuído
            // e recusar a operação, sem sobrescrever o técnico ou criar outro histórico.
            assertThatThrownBy(() -> segundaTentativa.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RegraNegocioException.class)
                    .hasRootCauseMessage(
                            "Este chamado já tem técnico. Atualize a fila. Para transferir, peça a um administrador.");
        }

        ChamadoResponse chamado = chamadoService.buscarPorId(chamadoId, solicitanteId);
        assertThat(chamado.tecnicoId()).isEqualTo(primeiroTecnicoId);
        assertThat(chamadoService.listarHistorico(chamadoId, solicitanteId))
                .filteredOn(evento -> evento.tipo() == TipoHistorico.TECNICO)
                .extracting(HistoricoChamadoResponse::autorId)
                .containsExactly(primeiroTecnicoId);
    }

    private Usuario criarUsuario(String nome, Role role, Departamento departamento) {
        Usuario usuario = new Usuario(nome, nome.toLowerCase() + "@teste.com", "hash", null, departamento);
        usuario.setRole(role);
        return usuarioRepository.save(usuario);
    }

    private void aguardarSegundaTransacaoNoBanco() {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (System.nanoTime() < limite) {
            // pg_blocking_pids informa quem está bloqueando uma transação.
            // Procuramos uma espera causada pela conexão atual (a primeira atribuição).
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
        throw new AssertionError("A segunda atribuição não chegou ao bloqueio do PostgreSQL em 5 segundos.");
    }
}
