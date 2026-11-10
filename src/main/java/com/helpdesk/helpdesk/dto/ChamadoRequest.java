package com.helpdesk.helpdesk.dto;

import com.helpdesk.helpdesk.models.Prioridade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados para abrir um chamado.
 *
 * Quem abre o chamado NÃO vem aqui: é o usuário logado (vem do token).
 * - departamentoId: opcional. Se não vier, usa o departamento do usuário.
 * - prioridade: opcional. Se não vier, fica MEDIA.
 */
public record ChamadoRequest(

        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String titulo,

        @NotBlank(message = "A descrição é obrigatória.")
        @Size(max = 5000, message = "A descrição deve ter no máximo 5000 caracteres.")
        String descricao,

        @Size(max = 50, message = "A categoria deve ter no máximo 50 caracteres.")
        String categoria,

        @Size(max = 50, message = "O número de patrimônio deve ter no máximo 50 caracteres.")
        String numeroPatrimonio,

        @Size(max = 100, message = "O equipamento deve ter no máximo 100 caracteres.")
        String equipamento,

        @Size(max = 100, message = "O local deve ter no máximo 100 caracteres.")
        String local,

        @Size(max = 100, message = "O setor deve ter no máximo 100 caracteres.")
        String setor,

        @Size(max = 255, message = "O detalhe do local deve ter no máximo 255 caracteres.")
        String detalheLocal,

        Prioridade prioridade,

        Long departamentoId) {
}
