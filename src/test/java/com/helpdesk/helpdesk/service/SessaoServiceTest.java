package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessaoServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-10T12:00:00Z");
    private static final Duration DURACAO = Duration.ofHours(8);

    @Mock
    private Clock relogio;

    private SessaoService sessaoService;

    @BeforeEach
    void setUp() {
        sessaoService = new SessaoService(DURACAO, relogio);
    }

    @Test
    void buscarUsuarioId_deveAceitarTokenAntesDoVencimentoSemRenovarOPrazo() {
        when(relogio.instant()).thenReturn(INICIO);
        String token = sessaoService.criar(10L);

        when(relogio.instant()).thenReturn(INICIO.plus(DURACAO).minusSeconds(1));
        assertThat(sessaoService.buscarUsuarioId(token)).contains(10L);

        // Ter usado o token um segundo antes não adia seu vencimento.
        when(relogio.instant()).thenReturn(INICIO.plus(DURACAO));
        assertThat(sessaoService.buscarUsuarioId(token)).isEmpty();
    }

    @Test
    void buscarUsuarioId_deveRecusarTokenDepoisDoVencimento() {
        when(relogio.instant()).thenReturn(INICIO);
        String token = sessaoService.criar(10L);

        when(relogio.instant()).thenReturn(INICIO.plus(DURACAO).plusSeconds(1));

        assertThat(sessaoService.buscarUsuarioId(token)).isEmpty();
    }

    @Test
    void buscarUsuarioId_deveRecusarTokenNuloVazioOuDesconhecido() {
        assertThat(sessaoService.buscarUsuarioId(null)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId("")).isEmpty();
        assertThat(sessaoService.buscarUsuarioId("  ")).isEmpty();
        assertThat(sessaoService.buscarUsuarioId("token-inexistente")).isEmpty();
    }

    @Test
    void criar_deveRespeitarDuracaoConfigurada() {
        sessaoService = new SessaoService(Duration.ofMinutes(30), relogio);
        when(relogio.instant()).thenReturn(INICIO);
        String token = sessaoService.criar(10L);

        when(relogio.instant()).thenReturn(INICIO.plus(Duration.ofMinutes(30)));

        assertThat(sessaoService.buscarUsuarioId(token)).isEmpty();
    }

    @Test
    void encerrar_deveInvalidarApenasOTokenInformado() {
        when(relogio.instant()).thenReturn(INICIO);
        String tokenDoComputador = sessaoService.criar(10L);
        String tokenDoCelular = sessaoService.criar(10L);

        sessaoService.encerrar(tokenDoComputador);

        assertThat(sessaoService.buscarUsuarioId(tokenDoComputador)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId(tokenDoCelular)).contains(10L);
    }

    @Test
    void encerrarTodasDoUsuario_devePreservarSessoesDeOutrasPessoas() {
        when(relogio.instant()).thenReturn(INICIO);
        String primeiroToken = sessaoService.criar(10L);
        String segundoToken = sessaoService.criar(10L);
        String tokenDeOutraPessoa = sessaoService.criar(20L);

        sessaoService.encerrarTodasDoUsuario(10L);

        assertThat(sessaoService.buscarUsuarioId(primeiroToken)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId(segundoToken)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId(tokenDeOutraPessoa)).contains(20L);

        // Encerrar sessões antigas não impede um novo login.
        String novoToken = sessaoService.criar(10L);
        assertThat(sessaoService.buscarUsuarioId(novoToken)).contains(10L);
    }

    @Test
    void removerSessoesExpiradas_deveLiberarMemoriaSemRemoverSessoesValidas() {
        when(relogio.instant()).thenReturn(INICIO);
        sessaoService.criar(10L);

        when(relogio.instant()).thenReturn(INICIO.plus(Duration.ofHours(4)));
        String tokenAindaValido = sessaoService.criar(20L);

        when(relogio.instant()).thenReturn(INICIO.plus(DURACAO));
        sessaoService.removerSessoesExpiradas();

        // Aqui verificamos a memória diretamente: consultar um token também o limparia,
        // o que esconderia uma falha na tarefa de limpeza periódica.
        Map<?, ?> sessoesNaMemoria = (Map<?, ?>) ReflectionTestUtils.getField(sessaoService, "sessoes");
        assertThat(sessoesNaMemoria).hasSize(1);
        assertThat(sessaoService.buscarUsuarioId(tokenAindaValido)).contains(20L);
    }

    @Test
    void construtor_deveRecusarDuracaoZero() {
        assertThatThrownBy(() -> new SessaoService(Duration.ZERO, relogio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    void construtor_deveRecusarDuracaoNegativa() {
        assertThatThrownBy(() -> new SessaoService(Duration.ofMinutes(-1), relogio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que zero");
    }
}
