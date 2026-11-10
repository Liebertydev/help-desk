package com.helpdesk.helpdesk.dto;

import java.time.LocalDate;

import com.helpdesk.helpdesk.models.Categoria;
import com.helpdesk.helpdesk.models.Prioridade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados editáveis de um chamado.
 * Status e técnico ficam de fora: cada um tem sua própria operação no service.
 * Prioridade e previsão de resolução só podem ser ALTERADAS por técnicos e admins;
 * o dono do chamado deve reenviar os valores atuais.
 */
public record ChamadoUpdateRequest(

        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String titulo,

        @NotBlank(message = "A descrição é obrigatória.")
        @Size(max = 5000, message = "A descrição deve ter no máximo 5000 caracteres.")
        String descricao,

        Categoria categoria,

        @Size(max = 50, message = "O número de patrimônio deve ter no máximo 50 caracteres.")
        String numeroPatrimonio,

        @Size(max = 100, message = "O equipamento deve ter no máximo 100 caracteres.")
        String equipamento,

        @Size(max = 100, message = "O local deve ter no máximo 100 caracteres.")
        String local,

        @Size(max = 100, message = "A sala deve ter no máximo 100 caracteres.")
        String sala,

        @Size(max = 255, message = "O detalhe do local deve ter no máximo 255 caracteres.")
        String detalheLocal,

        @NotNull(message = "A prioridade é obrigatória.")
        Prioridade prioridade,

        // Sem @FutureOrPresent aqui: uma previsão antiga que só está sendo MANTIDA
        // é válida. O service recusa data no passado apenas quando ela muda.
        LocalDate dataPrevisaoResolucao) {
}
