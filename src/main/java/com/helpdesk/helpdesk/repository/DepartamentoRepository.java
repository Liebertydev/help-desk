package com.helpdesk.helpdesk.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.Departamento;

public interface DepartamentoRepository extends JpaRepository<Departamento, Long> {

    // O Spring gera a consulta a partir do nome do método.
    List<Departamento> findByAtivoTrueOrderByNomeAsc();

    List<Departamento> findAllByOrderByNomeAsc();

    // Usado ao criar: já existe algum departamento com esse nome?
    boolean existsByNomeIgnoreCase(String nome);

    // Usado ao atualizar: existe OUTRO departamento (id diferente) com esse nome?
    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
