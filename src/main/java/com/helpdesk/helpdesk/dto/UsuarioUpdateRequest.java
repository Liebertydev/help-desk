package com.helpdesk.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados para editar um usuário.
 * Senha e role ficam de fora: cada um tem sua própria operação no service.
 */
public record UsuarioUpdateRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String name,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        String email,

        @Pattern(regexp = "^(\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})?$", message = "CPF deve ter 11 dígitos.")
        String cpf,

        @Size(max = 30, message = "A matrícula deve ter no máximo 30 caracteres.")
        String matricula,

        @Size(max = 100, message = "A ocupação deve ter no máximo 100 caracteres.")
        String ocupacao,

        @NotNull(message = "O departamento é obrigatório.")
        Long departamentoId) {
}
