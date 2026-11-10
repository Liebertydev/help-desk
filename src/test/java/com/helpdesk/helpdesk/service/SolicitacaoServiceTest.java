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

    @BeforeEach
    void setUp() {
        Departamento adm = new Departamento("Administrativo");
        adm.setId(1L);
        pedro = new Usuario("Pedro", "pedro@email.com", "hash", null, adm);
        pedro.setId(10L);
    }

    @Test
    void solicitar_deveCriarSolicitacaoPendente() {
        when(usuarioService.buscarEntidade(10L)).thenReturn(pedro);
        when(solicitacaoRepository.save(any(SolicitacaoTecnico.class))).thenAnswer(inv -> inv.getArgument(0));

        SolicitacaoTecnicoResponse resposta = solicitacaoService.solicitar(10L);

        assertThat(resposta.status()).isEqualTo(StatusSolicitacao.PENDENTE);
        assertThat(resposta.usuarioNome()).isEqualTo("Pedro");
    }

    @Test
    void solicitar_deveRecusarQuemJaETecnico() {
        pedro.setRole(Role.TECHNICIAN);
        when(usuarioService.buscarEntidade(10L)).thenReturn(pedro);

        assertThatThrownBy(() -> solicitacaoService.solicitar(10L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("usuários comuns");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void solicitar_deveRecusarSeJaHouverPendente() {
        when(usuarioService.buscarEntidade(10L)).thenReturn(pedro);
        when(solicitacaoRepository.existsByUsuarioIdAndStatus(10L, StatusSolicitacao.PENDENTE)).thenReturn(true);

        assertThatThrownBy(() -> solicitacaoService.solicitar(10L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("pendente");
    }

    @Test
    void aprovar_devePromoverUsuarioATecnico() {
        SolicitacaoTecnico solicitacao = solicitacao(StatusSolicitacao.PENDENTE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));

        SolicitacaoTecnicoResponse resposta = solicitacaoService.aprovar(1L);

        assertThat(resposta.status()).isEqualTo(StatusSolicitacao.APROVADA);
        verify(usuarioService).alterarRole(10L, Role.TECHNICIAN);
    }

    @Test
    void aprovar_naoDeveAprovarSeAPromocaoFalhar() {
        SolicitacaoTecnico solicitacao = solicitacao(StatusSolicitacao.PENDENTE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));
        when(usuarioService.alterarRole(10L, Role.TECHNICIAN))
                .thenThrow(new RegraNegocioException("Não é possível alterar o role de um usuário desativado."));

        assertThatThrownBy(() -> solicitacaoService.aprovar(1L))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.PENDENTE);
    }

    @Test
    void rejeitar_deveRecusarSolicitacaoJaAvaliada() {
        SolicitacaoTecnico solicitacao = solicitacao(StatusSolicitacao.APROVADA);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> solicitacaoService.rejeitar(1L))
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
