package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    List<Usuario> findAllByOrderByNameAsc();

    List<Usuario> findByRoleOrderByNameAsc(Role role);

    // Vai ser usado no login.
    Optional<Usuario> findByEmailIgnoreCase(String email);

    // Verificações de duplicidade ao criar...
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCpf(String cpf);

    boolean existsByMatricula(String matricula);

    // ...e ao atualizar (ignorando o próprio usuário).
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    boolean existsByMatriculaAndIdNot(String matricula, Long id);
}
