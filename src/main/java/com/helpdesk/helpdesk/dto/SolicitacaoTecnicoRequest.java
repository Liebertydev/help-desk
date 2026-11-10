package com.helpdesk.helpdesk.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Pedido de um usuário para virar técnico.
 * Enquanto não houver login, o id do usuário vem no corpo da requisição.
 */
public record SolicitacaoTecnicoRequest(

        @NotNull(message = "O usuário é obrigatório.")
        Long usuarioId) {
}
