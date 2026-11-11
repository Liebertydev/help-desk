package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.helpdesk.helpdesk.dto.RejeitarSolicitacaoRequest;
import com.helpdesk.helpdesk.dto.SolicitacaoTecnicoResponse;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.SolicitacaoTecnico;
import com.helpdesk.helpdesk.models.StatusSolicitacao;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.SolicitacaoRepository;

@ExtendWith(MockitoExtension.class)
class SolicitacaoServiceTest {

    @Mock
    private SolicitacaoRepository solicitacaoRepository;

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private SolicitacaoService solicitacaoService;

    private Usuario pedro;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        Departamento adm = new Departamento("Administrativo");
        adm.setId(1L);
        pedro = new Usuario("Pedro", "pedro@email.com", "hash", null, adm);
        pedro.setId(10L);
        admin = new Usuario("Administrador", "admin@email.com", "hash", null, adm);
        admin.setId(99L);
        admin.setRole(Role.ADMIN);
    }

    @Test
    void solicitar_deveCriarSolicitacaoPendente() {
        when(usuarioService.buscarParaSolicitacao(10L)).thenReturn(pedro);
        when(solicitacaoRepository.save(any(SolicitacaoTecnico.class))).thenAnswer(inv -> inv.getArgument(0));

        SolicitacaoTecnicoResponse resposta = solicitacaoService.solicitar(10L);

        assertThat(resposta.status()).isEqualTo(StatusSolicitacao.PENDENTE);
        assertThat(resposta.usuarioNome()).isEqualTo("Pedro");
    }

    @Test
    void solicitar_deveRecusarQuemJaETecnico() {
        pedro.setRole(Role.TECHNICIAN);
        when(usuarioService.buscarParaSolicitacao(10L)).thenReturn(pedro);

        assertThatThrownBy(() -> solicitacaoService.solicitar(10L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("usuários comuns");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void solicitar_deveRecusarSeJaHouverPendente() {
        when(usuarioService.buscarParaSolicitacao(10L)).thenReturn(pedro);
        when(solicitacaoRepository.existsByUsuarioIdAndStatus(10L, StatusSolicitacao.PENDENTE)).thenReturn(true);

        assertThatThrownBy(() -> solicitacaoService.solicitar(10L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("pendente");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void solicitar_deveRecusarUsuarioDesativado() {
        pedro.setAtivo(false);
        when(usuarioService.buscarParaSolicitacao(10L)).thenReturn(pedro);

        assertThatThrownBy(() -> solicitacaoService.solicitar(10L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desativado");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void aprovar_devePromoverUsuarioATecnico() {
        SolicitacaoTecnico solicitacao = solicitacao(StatusSolicitacao.PENDENTE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));
        when(usuarioService.buscarEntidade(99L)).thenReturn(admin);

        SolicitacaoTecnicoResponse resposta = solicitacaoService.aprovar(1L, 99L, null);

        assertThat(resposta.status()).isEqualTo(StatusSolicitacao.APROVADA);
        assertThat(resposta.avaliadoPorNome()).isEqualTo("Administrador");
        assertThat(resposta.avaliadoEm()).isNotNull();
        assertThat(resposta.observacao()).isNull();
        verify(usuarioService).alterarRole(10L, Role.TECHNICIAN, 99L);
    }

    @Test
    void aprovar_naoDeveAprovarSeAPromocaoFalhar() {
        SolicitacaoTecnico solicitacao = solicitacao(StatusSolicitacao.PENDENTE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));
        when(usuarioService.buscarEntidade(99L)).thenReturn(admin);
        when(usuarioService.alterarRole(10L, Role.TECHNICIAN, 99L))
                .thenThrow(new RegraNegocioException("Não é possível alterar o role de um usuário desativado."));

        assertThatThrownBy(() -> solicitacaoService.aprovar(1L, 99L, null))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.PENDENTE);
        assertThat(solicitacao.getAvaliadoPor()).isNull();
    }

    @Test
    void rejeitar_deveRegistrarMotivoEAdmin() {
        SolicitacaoTecnico solicitacao = solicitacao(StatusSolicitacao.PENDENTE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));
        when(usuarioService.buscarEntidade(99L)).thenReturn(admin);

        SolicitacaoTecnicoResponse resposta = solicitacaoService.rejeitar(1L, 99L,
                new RejeitarSolicitacaoRequest("  Ainda não concluiu o treinamento.  "));

        assertThat(resposta.status()).isEqualTo(StatusSolicitacao.REJEITADA);
        assertThat(resposta.observacao()).isEqualTo("Ainda não concluiu o treinamento.");
        assertThat(resposta.avaliadoPorId()).isEqualTo(99L);
    }

    @Test
    void rejeitar_deveRecusarSolicitacaoJaAvaliada() {
        SolicitacaoTecnico solicitacao = solicitacao(StatusSolicitacao.APROVADA);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> solicitacaoService.rejeitar(1L, 99L, new RejeitarSolicitacaoRequest("Motivo")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("APROVADA");
    }

    private SolicitacaoTecnico solicitacao(StatusSolicitacao status) {
        SolicitacaoTecnico solicitacao = new SolicitacaoTecnico(pedro);
        solicitacao.setId(1L);
        solicitacao.setStatus(status);
        return solicitacao;
    }
}
