package com.helpdesk.helpdesk.dto;

import com.helpdesk.helpdesk.models.Departamento;

/**
 * Dados do departamento que a API devolve.
 * Usar um DTO em vez da entidade evita expor campos internos
 * e problemas com relacionamentos LAZY na conversão para JSON.
 */
public record DepartamentoResponse(Long id, String nome, boolean ativo) {

    public static DepartamentoResponse from(Departamento departamento) {
        return new DepartamentoResponse(
                departamento.getId(),
                departamento.getNome(),
                departamento.isAtivo());
    }
}
