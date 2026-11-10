package com.helpdesk.helpdesk.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.StatusChamado;

/**
 * @EntityGraph busca usuário, técnico e departamento na MESMA consulta.
 * Sem ele, como os relacionamentos são LAZY, listar 50 chamados geraria
 * 1 consulta para os chamados + até 150 consultas extras (problema "N+1").
 */
public interface ChamadoRepository extends JpaRepository<Chamado, Long> {

    @EntityGraph(attributePaths = { "usuario", "tecnico", "departamento" })
    List<Chamado> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "usuario", "tecnico", "departamento" })
    List<Chamado> findByStatusOrderByCreatedAtDesc(StatusChamado status);

    // "Meus chamados" (de quem abriu).
    @EntityGraph(attributePaths = { "usuario", "tecnico", "departamento" })
    List<Chamado> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);

    // Fila do técnico.
    @EntityGraph(attributePaths = { "usuario", "tecnico", "departamento" })
    List<Chamado> findByTecnicoIdOrderByCreatedAtDesc(Long tecnicoId);

    // Chamados de um técnico em certos status (usado ao devolver chamados à fila).
    List<Chamado> findByTecnicoIdAndStatusIn(Long tecnicoId, Collection<StatusChamado> status);
}
