package com.helpdesk.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados que o cliente envia para criar ou atualizar um departamento.
 * As anotações de validação são verificadas no controller com @Valid.
 */
public record DepartamentoRequest(

        @NotBlank(message = "O nome do departamento é obrigatório.")
        @Size(max = 100, message = "O nome do departamento deve ter no máximo 100 caracteres.")
        String nome) {
}
