package com.helpdesk.helpdesk.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.StatusChamado;

import jakarta.persistence.LockModeType;

/**
 * @EntityGraph busca usuário, técnico e departamento na MESMA consulta.
 * Sem ele, como os relacionamentos são LAZY, listar 50 chamados geraria
 * 1 consulta para os chamados + até 150 consultas extras (problema "N+1").
 */
public interface ChamadoRepository extends JpaRepository<Chamado, Long> {

    // Mantém o chamado bloqueado até a transação de atribuição terminar.
    // Outra atribuição para o mesmo chamado espera e depois lê os dados atualizados.
    // Sem EntityGraph aqui: bloqueamos apenas o chamado, sem joins com técnico opcional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select chamado from Chamado chamado where chamado.id = :id")
    Optional<Chamado> buscarPorIdParaAtribuicao(@Param("id") Long id);

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
