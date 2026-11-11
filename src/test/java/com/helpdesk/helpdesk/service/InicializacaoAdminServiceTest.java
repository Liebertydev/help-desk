package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class InicializacaoAdminServiceTest {

    private static ValidatorFactory validatorFactory;

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private DepartamentoRepository departamentoRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private InicializacaoAdminService service;

    @BeforeAll
    static void criarValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void fecharValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        service = new InicializacaoAdminService(usuarioRepository, departamentoRepository,
                passwordEncoder, validatorFactory.getValidator(), jdbcTemplate);
    }

    @Test
    void deveReutilizarDepartamentoAtivoENormalizarNomeEEmail() {
        Departamento departamento = new Departamento("TI");
        when(departamentoRepository.findByNomeIgnoreCase("TI")).thenReturn(Optional.of(departamento));
        when(passwordEncoder.encode("SenhaInicial!123")).thenReturn("hash-seguro");

        assertThat(service.inicializar(" Admin ", " Admin@Empresa.COM ", "SenhaInicial!123", " TI ")).isTrue();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario admin = captor.getValue();
        assertThat(admin.getName()).isEqualTo("Admin");
        assertThat(admin.getEmail()).isEqualTo("admin@empresa.com");
        assertThat(admin.getPassword()).isEqualTo("hash-seguro");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isAtivo()).isTrue();
        assertThat(admin.getDepartamento()).isSameAs(departamento);
        verify(departamentoRepository, never()).save(any());
    }

    @Test
    void devePreservarContasExistentesMesmoSemCredenciaisDeInicializacao() {
        when(usuarioRepository.count()).thenReturn(1L);
        when(usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN)).thenReturn(1L);

        assertThat(service.inicializar(null, null, null, null)).isFalse();

        verify(usuarioRepository, never()).save(any());
        verify(departamentoRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void naoDevePromoverContasExistentesQuandoNaoHaAdminAtivo() {
        when(usuarioRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.inicializar("Admin", "admin@empresa.com", "SenhaInicial!123", "TI"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("não altera contas existentes");

        verify(usuarioRepository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("senhasInvalidas")
    void deveRecusarSenhaAusenteCurtaOuAcimaDoLimiteEmBytes(String senha) {
        assertThatThrownBy(() -> service.inicializar("Admin", "admin@empresa.com", senha, "TI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BOOTSTRAP_ADMIN_SENHA deve ter pelo menos 12 caracteres e no máximo 72 bytes em UTF-8.");

        verify(departamentoRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    static Stream<String> senhasInvalidas() {
        return Stream.of(null, "", "123456", "a".repeat(73), "é".repeat(37));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "invalido", "a@" })
    void deveRecusarEmailInvalido(String email) {
        assertThatThrownBy(() -> service.inicializar("Admin", email, "SenhaInicial!123", "TI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveExigirNomeEDepartamento() {
        assertThatThrownBy(() -> service.inicializar(" ", "admin@empresa.com", "SenhaInicial!123", "TI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_NOME");
        assertThatThrownBy(() -> service.inicializar("Admin", "admin@empresa.com", "SenhaInicial!123", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAP_DEPARTAMENTO");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveRecusarDepartamentoDesativadoSemReativaLo() {
        Departamento departamento = new Departamento("TI");
        departamento.setAtivo(false);
        when(departamentoRepository.findByNomeIgnoreCase("TI")).thenReturn(Optional.of(departamento));

        assertThatThrownBy(() -> service.inicializar("Admin", "admin@empresa.com", "SenhaInicial!123", "TI"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("desativado");
        assertThat(departamento.isAtivo()).isFalse();
        verify(usuarioRepository, never()).save(any());
    }
}
