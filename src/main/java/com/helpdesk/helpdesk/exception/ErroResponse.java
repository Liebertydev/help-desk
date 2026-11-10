package com.helpdesk.helpdesk.exception;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Formato padrão de erro devolvido pela API. Exemplo:
 *
 * {
 *   "timestamp": "2026-11-10T17:30:00",
 *   "status": 409,
 *   "erro": "Conflict",
 *   "mensagem": "Já existe um usuário com este e-mail.",
 *   "caminho": "/api/usuarios"
 * }
 *
 * "campos" só aparece em erros de validação: { "email": "E-mail inválido." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        Map<String, String> campos) {
}
