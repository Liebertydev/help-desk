package com.helpdesk.helpdesk.dto;

import java.time.LocalDateTime;

import com.helpdesk.helpdesk.models.SolicitacaoTecnico;
import com.helpdesk.helpdesk.models.StatusSolicitacao;

public record SolicitacaoTecnicoResponse(
        Long id,
        Long usuarioId,
        String usuarioNome,
        String usuarioEmail,
        StatusSolicitacao status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SolicitacaoTecnicoResponse from(SolicitacaoTecnico solicitacao) {
        return new SolicitacaoTecnicoResponse(
                solicitacao.getId(),
                solicitacao.getUsuario().getId(),
                solicitacao.getUsuario().getName(),
                solicitacao.getUsuario().getEmail(),
                solicitacao.getStatus(),
                solicitacao.getCreatedAt(),
                solicitacao.getUpdatedAt());
    }
}
