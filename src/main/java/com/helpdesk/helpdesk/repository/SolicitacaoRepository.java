package com.helpdesk.helpdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.SolicitacaoTecnico;

public interface SolicitacaoRepository extends JpaRepository<SolicitacaoTecnico, Long> {

}
