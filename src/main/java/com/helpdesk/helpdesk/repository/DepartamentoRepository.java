package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.models.Departamento;

import jakarta.persistence.LockModeType;

public interface DepartamentoRepository extends JpaRepository<Departamento, Long> {

    // Cadastro, transferência e desativação precisam verificar o mesmo departamento
    // sem permitir que outra dessas operações altere a situação antes do commit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select departamento from Departamento departamento where departamento.id = :id")
    Optional<Departamento> buscarPorIdComBloqueio(@Param("id") Long id);

    // O Spring gera a consulta a partir do nome do método.
    List<Departamento> findByAtivoTrueOrderByNomeAsc();

    List<Departamento> findAllByOrderByNomeAsc();

    // Usado ao criar: já existe algum departamento com esse nome?
    boolean existsByNomeIgnoreCase(String nome);

    // Usado ao atualizar: existe OUTRO departamento (id diferente) com esse nome?
    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
