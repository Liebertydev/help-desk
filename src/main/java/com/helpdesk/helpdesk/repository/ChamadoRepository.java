package com.helpdesk.helpdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.Chamado;

public interface ChamadoRepository extends JpaRepository<Chamado, Long> {

}
