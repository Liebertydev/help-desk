package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.AprovarSolicitacaoRequest;
import com.helpdesk.helpdesk.dto.RejeitarSolicitacaoRequest;
import com.helpdesk.helpdesk.dto.SolicitacaoTecnicoResponse;
import com.helpdesk.helpdesk.exception.RecursoNaoEncontradoException;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.SolicitacaoTecnico;
import com.helpdesk.helpdesk.models.StatusSolicitacao;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.SolicitacaoRepository;

/**
 * Solicitações de usuários para virar técnico.
 *
 * Fluxo: PENDENTE → APROVADA (o usuário vira TECHNICIAN)
 *                 → REJEITADA (o usuário pode pedir de novo depois)
 *
 * Aprovar e rejeitar são ações de ADMIN (a checagem entra junto com o login).
 */
@Service
public class SolicitacaoService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final UsuarioService usuarioService;

    public SolicitacaoService(SolicitacaoRepository solicitacaoRepository, UsuarioService usuarioService) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.usuarioService = usuarioService;
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public List<SolicitacaoTecnicoResponse> listar(StatusSolicitacao status) {
        List<SolicitacaoTecnico> solicitacoes = status == null
                ? solicitacaoRepository.findAllByOrderByCreatedAtDesc()
                : solicitacaoRepository.findByStatusOrderByCreatedAtDesc(status);

        return paraResponse(solicitacoes);
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoTecnicoResponse> listarPorUsuario(Long usuarioId) {
        usuarioService.buscarEntidade(usuarioId); // 404 se o usuário não existir
        return paraResponse(solicitacaoRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId));
    }

    @Transactional(readOnly = true)
    public SolicitacaoTecnicoResponse buscarPorId(Long id) {
        return SolicitacaoTecnicoResponse.from(buscarEntidade(id));
    }

    // ---------- Ações ----------

    /** @param usuarioId quem está pedindo (o usuário logado). */
    @Transactional
    public SolicitacaoTecnicoResponse solicitar(Long usuarioId) {
        Usuario usuario = usuarioService.buscarEntidade(usuarioId);

        if (!usuario.isAtivo()) {
            throw new RegraNegocioException("Usuário desativado não pode fazer solicitações.");
        }
        // Só USER pode pedir: técnico já é técnico, e admin já tem mais permissões.
        if (usuario.getRole() != Role.USER) {
            throw new RegraNegocioException("Apenas usuários comuns podem solicitar acesso de técnico.");
        }
        if (solicitacaoRepository.existsByUsuarioIdAndStatus(usuario.getId(), StatusSolicitacao.PENDENTE)) {
            throw new RegraNegocioException("Já existe uma solicitação pendente para este usuário.");
        }

        SolicitacaoTecnico salva = solicitacaoRepository.save(new SolicitacaoTecnico(usuario));
        return SolicitacaoTecnicoResponse.from(salva);
    }

    /**
     * Aprova e promove o usuário a TECHNICIAN.
     * As duas mudanças estão na mesma transação: se a promoção falhar
     * (ex.: usuário desativado), a solicitação também NÃO fica aprovada.
     */
    @Transactional
    public SolicitacaoTecnicoResponse aprovar(Long id, Long adminId, AprovarSolicitacaoRequest request) {
        SolicitacaoTecnico solicitacao = buscarPendente(id);
        Usuario admin = usuarioService.buscarEntidade(adminId);

        usuarioService.alterarRole(solicitacao.getUsuario().getId(), Role.TECHNICIAN);
        // A observação da aprovação é opcional (o corpo da requisição pode nem vir).
        String observacao = request == null ? null : textoOuNulo(request.observacao());
        solicitacao.avaliar(StatusSolicitacao.APROVADA, admin, observacao);

        return salvarEResponder(solicitacao);
    }

    /** O motivo é obrigatório (validado no DTO): o usuário precisa saber por que foi recusado. */
    @Transactional
    public SolicitacaoTecnicoResponse rejeitar(Long id, Long adminId, RejeitarSolicitacaoRequest request) {
        SolicitacaoTecnico solicitacao = buscarPendente(id);
        Usuario admin = usuarioService.buscarEntidade(adminId);

        solicitacao.avaliar(StatusSolicitacao.REJEITADA, admin, request.motivo().trim());

        return salvarEResponder(solicitacao);
    }

    // ---------- Auxiliares ----------

    private SolicitacaoTecnico buscarPendente(Long id) {
        SolicitacaoTecnico solicitacao = buscarEntidade(id);

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new RegraNegocioException("Esta solicitação já foi " + solicitacao.getStatus() + ".");
        }
        return solicitacao;
    }

    private SolicitacaoTecnico buscarEntidade(Long id) {
        return solicitacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação", id));
    }

    // flush() antecipa a gravação para a resposta já sair com o updatedAt novo
    // (mesmo motivo explicado no ChamadoService).
    private SolicitacaoTecnicoResponse salvarEResponder(SolicitacaoTecnico solicitacao) {
        solicitacaoRepository.flush();
        return SolicitacaoTecnicoResponse.from(solicitacao);
    }

    private String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private List<SolicitacaoTecnicoResponse> paraResponse(List<SolicitacaoTecnico> solicitacoes) {
        return solicitacoes.stream()
                .map(SolicitacaoTecnicoResponse::from)
                .toList();
    }
}
