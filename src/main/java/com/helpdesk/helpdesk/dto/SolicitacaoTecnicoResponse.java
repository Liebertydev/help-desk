package com.helpdesk.helpdesk.dto;

import java.time.LocalDateTime;

import com.helpdesk.helpdesk.models.SolicitacaoTecnico;
import com.helpdesk.helpdesk.models.StatusSolicitacao;
import com.helpdesk.helpdesk.models.Usuario;

public record SolicitacaoTecnicoResponse(
        Long id,
        Long usuarioId,
        String usuarioNome,
        String usuarioEmail,
        StatusSolicitacao status,
        Long avaliadoPorId,
        String avaliadoPorNome,
        LocalDateTime avaliadoEm,
        String observacao,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SolicitacaoTecnicoResponse from(SolicitacaoTecnico solicitacao) {
        Usuario admin = solicitacao.getAvaliadoPor();

        return new SolicitacaoTecnicoResponse(
                solicitacao.getId(),
                solicitacao.getUsuario().getId(),
                solicitacao.getUsuario().getName(),
                solicitacao.getUsuario().getEmail(),
                solicitacao.getStatus(),
                admin == null ? null : admin.getId(),
                admin == null ? null : admin.getName(),
                solicitacao.getAvaliadoEm(),
                solicitacao.getObservacao(),
                solicitacao.getCreatedAt(),
                solicitacao.getUpdatedAt());
    }
}
