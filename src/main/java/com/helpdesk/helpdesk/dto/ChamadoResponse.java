package com.helpdesk.helpdesk.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.Prioridade;
import com.helpdesk.helpdesk.models.StatusChamado;
import com.helpdesk.helpdesk.models.Usuario;

/**
 * Dados do chamado que a API devolve.
 * Usuário, técnico e departamento vão "achatados" (id + nome) em vez do objeto inteiro.
 */
public record ChamadoResponse(
        Long id,
        String titulo,
        String descricao,
        String categoria,
        String numeroPatrimonio,
        String equipamento,
        String local,
        String setor,
        String detalheLocal,
        StatusChamado status,
        Prioridade prioridade,
        LocalDate dataPrevisaoResolucao,
        Long usuarioId,
        String usuarioNome,
        Long tecnicoId,
        String tecnicoNome,
        Long departamentoId,
        String departamentoNome,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ChamadoResponse from(Chamado chamado) {
        Usuario tecnico = chamado.getTecnico();

        return new ChamadoResponse(
                chamado.getId(),
                chamado.getTitulo(),
                chamado.getDescricao(),
                chamado.getCategoria(),
                chamado.getNumeroPatrimonio(),
                chamado.getEquipamento(),
                chamado.getLocal(),
                chamado.getSetor(),
                chamado.getDetalheLocal(),
                chamado.getStatus(),
                chamado.getPrioridade(),
                chamado.getDataPrevisaoResolucao(),
                chamado.getUsuario().getId(),
                chamado.getUsuario().getName(),
                tecnico == null ? null : tecnico.getId(),
                tecnico == null ? null : tecnico.getName(),
                chamado.getDepartamento().getId(),
                chamado.getDepartamento().getNome(),
                chamado.getCreatedAt(),
                chamado.getUpdatedAt());
    }
}
