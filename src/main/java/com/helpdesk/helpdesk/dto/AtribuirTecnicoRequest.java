package com.helpdesk.helpdesk.dto;

import jakarta.validation.constraints.NotNull;

public record AtribuirTecnicoRequest(

        @NotNull(message = "O técnico é obrigatório.")
        Long tecnicoId) {
}
