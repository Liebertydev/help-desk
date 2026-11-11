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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.SolicitacaoTecnico;
import com.helpdesk.helpdesk.models.StatusSolicitacao;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.SolicitacaoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL",
        matches = "jdbc:postgresql://.+/helpdesk_concorrencia_test(?:\\?.*)?")
@SpringBootTest(properties = {
        "spring.config.import=", "spring.profiles.active=test",
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '10s'",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@DirtiesContext
class SolicitacaoPerfilConcorrenciaTest {

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private SolicitacaoService solicitacaoService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private SolicitacaoRepository solicitacaoRepository;
    @Autowired
    private DepartamentoRepository departamentoRepository;
    @Autowired
    private PlatformTransactionManager gerenciadorDeTransacoes;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long adminId;
    private Long usuarioId;
    private Long solicitacaoId;

    @BeforeEach
    void prepararDados() {
        solicitacaoRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        departamentoRepository.deleteAllInBatch();
        Departamento departamento = departamentoRepository.save(new Departamento("TI do teste"));
        Usuario admin = new Usuario("Admin", "admin@teste.com", "hash", null, departamento);
        admin.setRole(Role.ADMIN);
        adminId = usuarioRepository.save(admin).getId();
        usuarioId = usuarioRepository.save(new Usuario("Ana", "ana@teste.com", "hash", null, departamento)).getId();
        solicitacaoId = solicitacaoService.solicitar(usuarioId).id();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "TECHNICIAN", "ADMIN" })
    void promocaoDiretaDeveEncerrarPendenciaSemPermitirNovaAvaliacao(Role role) {
        usuarioService.alterarRole(usuarioId, role, adminId);

        SolicitacaoTecnico solicitacao = solicitacaoRepository.findById(solicitacaoId).orElseThrow();
        assertThat(solicitacao.getStatus()).isEqualTo(role == Role.TECHNICIAN
                ? StatusSolicitacao.APROVADA : StatusSolicitacao.REJEITADA);
        assertThat(solicitacao.getAvaliadoPor().getId()).isEqualTo(adminId);
        assertThat(solicitacao.getAvaliadoEm()).isNotNull();
        assertThat(solicitacao.getObservacao()).contains(role.name());
        assertThatThrownBy(() -> solicitacaoService.aprovar(solicitacaoId, adminId, null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("já foi");
        assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getRole()).isEqualTo(role);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "TECHNICIAN", "ADMIN" })
    void pedidoLegadoPendenteNaoDeveAlterarPerfilQueJaMudou(Role role) {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);
        // Simula um pedido antigo, anterior ao encerramento automático de pendências.
        transacao.executeWithoutResult(status -> usuarioRepository.findById(usuarioId).orElseThrow().setRole(role));

        assertThatThrownBy(() -> solicitacaoService.aprovar(solicitacaoId, adminId, null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("perfil USER");
        assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getRole()).isEqualTo(role);
        assertThat(solicitacaoRepository.findById(solicitacaoId).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.PENDENTE);
    }

    @Test
    void aprovacaoNormalDeveConcluirPerfilEAvaliacaoJuntos() {
        assertThat(solicitacaoService.aprovar(solicitacaoId, adminId, null).status())
                .isEqualTo(StatusSolicitacao.APROVADA);
        assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getRole()).isEqualTo(Role.TECHNICIAN);
    }

    @Test
    void avaliacaoSimultaneaAPromocaoDeveRelerPedidoSemRebaixarAdmin() throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> tentativa = transacao.execute(status -> {
                usuarioService.alterarRole(usuarioId, Role.ADMIN, adminId);
                Future<?> segunda = executor.submit(() -> transacao.executeWithoutResult(outroStatus -> {
                    // Essa leitura ainda vê PENDENTE; a avaliação deve atualizar o estado após esperar.
                    assertThat(solicitacaoRepository.findById(solicitacaoId).orElseThrow().getStatus())
                            .isEqualTo(StatusSolicitacao.PENDENTE);
                    solicitacaoService.aprovar(solicitacaoId, adminId, null);
                }));
                aguardarSegundaTransacaoNoBanco();
                return segunda;
            });
            assertThatThrownBy(() -> tentativa.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RegraNegocioException.class);
        }
        assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(solicitacaoRepository.findById(solicitacaoId).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.REJEITADA);
    }

    private void aguardarSegundaTransacaoNoBanco() {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            Boolean esperando = jdbcTemplate.queryForObject("""
                    select exists (select 1 from pg_locks where not granted
                        and pg_backend_pid() = any(pg_blocking_pids(pid)))
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
        throw new AssertionError("A avaliação não chegou ao bloqueio do PostgreSQL em 5 segundos.");
    }

    @Test
    void promocaoDeveEncerrarPedidoCriadoEnquantoEsperavaPeloUsuario() throws Exception {
        solicitacaoRepository.deleteAllInBatch();
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> promocao = transacao.execute(status -> {
                solicitacaoId = solicitacaoService.solicitar(usuarioId).id();
                Future<?> tentativa = executor.submit(() -> usuarioService.alterarRole(usuarioId, Role.ADMIN, adminId));
                // A promoção precisa esperar o pedido concluir antes de gravar o perfil
                // e consultar suas pendências. Caso contrário, esse pedido ficaria esquecido.
                aguardarSegundaTransacaoNoBanco();
                return tentativa;
            });
            promocao.get(10, TimeUnit.SECONDS);
        }
        assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(solicitacaoRepository.findById(solicitacaoId).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.REJEITADA);
    }
}
