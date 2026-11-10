package com.helpdesk.helpdesk.dto;

import com.helpdesk.helpdesk.models.StatusChamado;

import jakarta.validation.constraints.NotNull;

public record AlterarStatusChamadoRequest(

        @NotNull(message = "O status é obrigatório.")
        StatusChamado status) {
}
