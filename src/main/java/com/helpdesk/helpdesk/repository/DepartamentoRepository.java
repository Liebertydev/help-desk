package com.helpdesk.helpdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.Departamento;

public interface DepartamentoRepository extends JpaRepository<Departamento, Long> {

}
