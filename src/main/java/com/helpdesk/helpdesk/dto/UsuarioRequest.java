package com.helpdesk.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados para cadastrar um usuário.
 * O role não vem do cliente: todo usuário novo começa como USER.
 */
public record UsuarioRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String name,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres.")
        String password,

        // Opcional. Aceita com ou sem máscara: 12345678901 ou 123.456.789-01.
        @Pattern(regexp = "^(\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})?$", message = "CPF deve ter 11 dígitos.")
        String cpf,

        @Size(max = 30, message = "A matrícula deve ter no máximo 30 caracteres.")
        String matricula,

        @Size(max = 100, message = "A ocupação deve ter no máximo 100 caracteres.")
        String ocupacao,

        @NotNull(message = "O departamento é obrigatório.")
        Long departamentoId) {
}
