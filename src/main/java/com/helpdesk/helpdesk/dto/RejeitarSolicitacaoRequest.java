package com.helpdesk.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejeitarSolicitacaoRequest(

        @NotBlank(message = "Informe o motivo da rejeição.")
        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres.")
        String motivo) {
}
