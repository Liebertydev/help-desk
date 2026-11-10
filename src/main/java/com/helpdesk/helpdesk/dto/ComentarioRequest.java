package com.helpdesk.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComentarioRequest(

        @NotBlank(message = "O comentário não pode ficar vazio.")
        @Size(max = 2000, message = "O comentário deve ter no máximo 2000 caracteres.")
        String mensagem) {
}
