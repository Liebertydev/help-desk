package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.helpdesk.helpdesk.dto.LoginRequest;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

/** Testa a inicialização no PostgreSQL isolado, sem ativar credenciais reais de produção. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL",
        matches = "jdbc:postgresql://.+/helpdesk_concorrencia_test(?:\\?.*)?")
@SpringBootTest(properties = {
        "spring.config.import=", "spring.profiles.active=test",
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '10s'",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "app.bootstrap.habilitado=false"
})
@DirtiesContext
class InicializacaoAdminConcorrenciaTest {

    @Autowired
    private InicializacaoAdminService service;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DepartamentoRepository departamentoRepository;
    @Autowired
    private AuthService authService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PlatformTransactionManager gerenciadorDeTransacoes;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBancoDeTeste() {
        usuarioRepository.deleteAllInBatch();
        departamentoRepository.deleteAllInBatch();
    }

    @Test
    void deveCriarAdminComLoginValidoEPreservarContaNaReexecucao() {
        assertThat(service.inicializar(" Admin ", " Admin@Empresa.COM ", "SenhaInicial!123", " TI ")).isTrue();
        Usuario admin = usuarioRepository.findAllByOrderByNameAsc().getFirst();
        String hashOriginal = admin.getPassword();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isAtivo()).isTrue();
        assertThat(admin.getDepartamento().getNome()).isEqualTo("TI");
        assertThat(hashOriginal).isNotEqualTo("SenhaInicial!123");
        assertThat(passwordEncoder.matches("SenhaInicial!123", hashOriginal)).isTrue();
        assertThat(authService.login(new LoginRequest("admin@empresa.com", "SenhaInicial!123")).token()).isNotBlank();

        assertThat(service.inicializar("Outro", "outro@empresa.com", "OutraSenha!456", "Outro departamento")).isFalse();
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(departamentoRepository.count()).isEqualTo(1);
        assertThat(usuarioRepository.findById(admin.getId()).orElseThrow().getPassword()).isEqualTo(hashOriginal);
    }

    @Test
    void rollbackDeveDesfazerAdminEDepartamentoJuntos() {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            service.inicializar("Admin", "admin@empresa.com", "SenhaInicial!123", "TI");
            throw new IllegalStateException("Falha simulada antes do commit.");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("Falha simulada");

        assertThat(usuarioRepository.count()).isZero();
        assertThat(departamentoRepository.count()).isZero();
    }

    @Test
    void duasInstanciasNaoDevemCriarDoisAdministradores() throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacoes);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Boolean> segundaTentativa = transacao.execute(status -> {
                assertThat(service.inicializar("Primeiro", "primeiro@empresa.com", "SenhaInicial!123", "TI")).isTrue();
                Future<Boolean> tentativa = executor.submit(() ->
                        service.inicializar("Segundo", "segundo@empresa.com", "OutraSenha!456", "Outro departamento"));
                aguardarSegundaTransacaoNoBanco();
                return tentativa;
            });
            assertThat(segundaTentativa.get(10, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN)).isEqualTo(1);
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(departamentoRepository.count()).isEqualTo(1);
        assertThat(usuarioRepository.existsByEmailIgnoreCase("primeiro@empresa.com")).isTrue();
    }

    private void aguardarSegundaTransacaoNoBanco() {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            Boolean esperando = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1 from pg_locks where not granted
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
        throw new AssertionError("A segunda inicialização não chegou ao bloqueio do PostgreSQL em 5 segundos.");
    }
}
