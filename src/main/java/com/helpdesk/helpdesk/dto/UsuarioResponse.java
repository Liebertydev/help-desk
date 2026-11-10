package com.helpdesk.helpdesk.dto;

import java.time.LocalDateTime;

import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;

/**
 * Dados do usuário que a API devolve. A senha NUNCA sai daqui.
 */
public record UsuarioResponse(
        Long id,
        String name,
        String email,
        String cpf,
        String matricula,
        String ocupacao,
        boolean ativo,
        Role role,
        Long departamentoId,
        String departamentoNome,
        LocalDateTime createdAt) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getName(),
                usuario.getEmail(),
                usuario.getCpf(),
                usuario.getMatricula(),
                usuario.getOcupacao(),
                usuario.isAtivo(),
                usuario.getRole(),
                usuario.getDepartamento().getId(),
                usuario.getDepartamento().getNome(),
                usuario.getCreatedAt());
    }
}
