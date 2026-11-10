package com.helpdesk.helpdesk.dto;

import java.time.LocalDate;

import com.helpdesk.helpdesk.models.Prioridade;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados editáveis de um chamado.
 * Status e técnico ficam de fora: cada um tem sua própria operação no service.
 */
public record ChamadoUpdateRequest(

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

        @NotNull(message = "A prioridade é obrigatória.")
        Prioridade prioridade,

        @FutureOrPresent(message = "A previsão de resolução não pode estar no passado.")
        LocalDate dataPrevisaoResolucao) {
}
