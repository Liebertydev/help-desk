package com.helpdesk.helpdesk.dto;

import java.time.LocalDateTime;

import com.helpdesk.helpdesk.models.HistoricoChamado;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.TipoHistorico;

/**
 * O role do autor vai junto para o front poder diferenciar
 * mensagens do usuário e do técnico (ex.: cores diferentes).
 */
public record HistoricoChamadoResponse(
        Long id,
        TipoHistorico tipo,
        String mensagem,
        Long autorId,
        String autorNome,
        Role autorRole,
        LocalDateTime createdAt) {

    public static HistoricoChamadoResponse from(HistoricoChamado historico) {
        return new HistoricoChamadoResponse(
                historico.getId(),
                historico.getTipo(),
                historico.getMensagem(),
                historico.getAutor().getId(),
                historico.getAutor().getName(),
                historico.getAutor().getRole(),
                historico.getCreatedAt());
    }
}
