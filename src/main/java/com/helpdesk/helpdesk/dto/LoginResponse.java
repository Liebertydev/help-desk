package com.helpdesk.helpdesk.dto;

/**
 * Resposta do login. O front guarda o token e o envia em toda requisição:
 *   Authorization: Bearer <token>
 */
public record LoginResponse(String token, UsuarioResponse usuario) {
}
