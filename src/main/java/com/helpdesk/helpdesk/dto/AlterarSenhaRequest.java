package com.helpdesk.helpdesk.dto;

import com.helpdesk.helpdesk.validation.MaximoBytesUtf8;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaRequest(

        @NotBlank(message = "A senha atual é obrigatória.")
        String senhaAtual,

        @NotBlank(message = "A nova senha é obrigatória.")
        @Size(min = 6, message = "A nova senha deve ter pelo menos 6 caracteres.")
        @MaximoBytesUtf8(value = 72, message = "A nova senha deve ter no máximo 72 bytes em UTF-8.")
        String novaSenha) {
}
