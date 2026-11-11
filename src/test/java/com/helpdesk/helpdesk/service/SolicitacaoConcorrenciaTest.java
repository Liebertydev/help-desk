package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.helpdesk.helpdesk.dto.SolicitacaoTecnicoResponse;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.StatusSolicitacao;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.SolicitacaoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

/** Executa duas solicitações reais no PostgreSQL exclusivo de testes, sem importar o .env. */
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
class SolicitacaoConcorrenciaTest {

    @Autowired
    private SolicitacaoService solicitacaoService;

    @Autowired
    private SolicitacaoRepository solicitacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private PlatformTransactionManager gerenciadorDeTransacoes;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveCriarSomenteUmaSolicitacaoPendenteParaOMesmoUsuario() throws Exception {
        Departamento departamento = departamentoRepository.save(new Departamento("TI do teste"));
        Usuario usuario = usuarioRepository.save(new Usuario("Ana", "ana@teste.com", "hash", null, departamento));
        Long usuarioId = usuario.getId();
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<SolicitacaoTecnicoResponse> segundaTentativa = transacao.execute(status -> {
                solicitacaoService.solicitar(usuarioId);

                // A primeira solicitação ainda não foi confirmada. A segunda chega ao
                // banco neste momento, quando uma consulta comum ainda não enxergaria a primeira.
                Future<SolicitacaoTecnicoResponse> tentativa = executor.submit(() ->
                        solicitacaoService.solicitar(usuarioId));
                aguardarSegundaTransacaoNoBanco();
                return tentativa;
            });

            assertThatThrownBy(() -> segundaTentativa.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RegraNegocioException.class)
                    .hasRootCauseMessage("Já existe uma solicitação pendente para este usuário.");
        }

        assertThat(solicitacaoRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId))
                .hasSize(1)
                .allMatch(solicitacao -> solicitacao.getStatus() == StatusSolicitacao.PENDENTE);
    }

    private void aguardarSegundaTransacaoNoBanco() {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            // Confirma a espera real no banco, em vez de presumir que a thread já começou.
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
        throw new AssertionError("A segunda solicitação não chegou ao bloqueio do PostgreSQL em 5 segundos.");
    }
}
