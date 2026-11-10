package com.helpdesk.helpdesk.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.HistoricoChamado;

public interface HistoricoChamadoRepository extends JpaRepository<HistoricoChamado, Long> {

    // Do mais antigo para o mais novo (ordem de leitura de uma conversa).
    // O id desempata registros criados no mesmo instante.
    @EntityGraph(attributePaths = "autor")
    List<HistoricoChamado> findByChamadoIdOrderByCreatedAtAscIdAsc(Long chamadoId);
}
