package com.helpdesk.helpdesk.dto;

import com.helpdesk.helpdesk.models.Role;

import jakarta.validation.constraints.NotNull;

public record AlterarRoleRequest(

        @NotNull(message = "O role é obrigatório.")
        Role role) {
}
