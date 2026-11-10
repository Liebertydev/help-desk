package com.helpdesk.helpdesk.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.SolicitacaoTecnico;
import com.helpdesk.helpdesk.models.StatusSolicitacao;

public interface SolicitacaoRepository extends JpaRepository<SolicitacaoTecnico, Long> {

    // @EntityGraph: traz o usuário na mesma consulta (evita N+1, igual ao ChamadoRepository).
    @EntityGraph(attributePaths = "usuario")
    List<SolicitacaoTecnico> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "usuario")
    List<SolicitacaoTecnico> findByStatusOrderByCreatedAtDesc(StatusSolicitacao status);

    @EntityGraph(attributePaths = "usuario")
    List<SolicitacaoTecnico> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);

    // O usuário já tem uma solicitação aguardando avaliação?
    boolean existsByUsuarioIdAndStatus(Long usuarioId, StatusSolicitacao status);
}
