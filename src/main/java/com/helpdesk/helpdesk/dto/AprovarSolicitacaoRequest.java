package com.helpdesk.helpdesk.dto;

import jakarta.validation.constraints.Size;

/** Corpo opcional da aprovação. */
public record AprovarSolicitacaoRequest(

        @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres.")
        String observacao) {
}
