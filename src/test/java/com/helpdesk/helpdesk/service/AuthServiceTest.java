package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.helpdesk.helpdesk.dto.LoginRequest;
import com.helpdesk.helpdesk.dto.LoginResponse;
import com.helpdesk.helpdesk.exception.NaoAutenticadoException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-10T12:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Clock relogio;

    private SessaoService sessaoService;
    private AuthService authService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        sessaoService = new SessaoService(Duration.ofHours(8), relogio);
        authService = new AuthService(usuarioRepository, passwordEncoder, sessaoService);

        Departamento departamento = new Departamento("TI");
        departamento.setId(1L);
        usuario = new Usuario("Ana", "ana@email.com", "hash-da-senha", null, departamento);
        usuario.setId(10L);
    }

    @Test
    void login_deveCriarSessaoParaCredenciaisValidas() {
        when(usuarioRepository.findByEmailIgnoreCase("ana@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("123456", "hash-da-senha")).thenReturn(true);
        when(relogio.instant()).thenReturn(INICIO);

        LoginResponse resposta = authService.login(new LoginRequest(" ana@email.com ", "123456"));

        assertThat(resposta.usuario().id()).isEqualTo(10L);
        assertThat(sessaoService.buscarUsuarioId(resposta.token())).contains(10L);
    }

    @Test
    void usuarioDoToken_deveExigirNovoLoginQuandoASessaoExpirar() {
        when(relogio.instant()).thenReturn(INICIO);
        String token = sessaoService.criar(10L);

        when(relogio.instant()).thenReturn(INICIO.plus(Duration.ofHours(8)));

        assertThatThrownBy(() -> authService.usuarioDoToken(token))
                .isInstanceOf(NaoAutenticadoException.class)
                .hasMessageContaining("Faça login");
    }

    @Test
    void logout_deveImpedirReutilizacaoDoToken() {
        when(relogio.instant()).thenReturn(INICIO);
        String token = sessaoService.criar(10L);

        authService.logout(token);

        assertThatThrownBy(() -> authService.usuarioDoToken(token))
                .isInstanceOf(NaoAutenticadoException.class);
    }

    @Test
    void usuarioDoToken_deveEncerrarTodasAsSessoesDaContaDesativada() {
        when(relogio.instant()).thenReturn(INICIO);
        String primeiroToken = sessaoService.criar(10L);
        String segundoToken = sessaoService.criar(10L);
        usuario.setAtivo(false);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.usuarioDoToken(primeiroToken))
                .isInstanceOf(NaoAutenticadoException.class)
                .hasMessageContaining("desativado");

        assertThat(sessaoService.buscarUsuarioId(primeiroToken)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId(segundoToken)).isEmpty();
    }
}
