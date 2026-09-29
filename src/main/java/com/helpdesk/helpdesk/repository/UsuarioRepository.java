package com.helpdesk.helpdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

}
