package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // @EntityGraph: traz o departamento na mesma consulta. Sem ele, como o
    // relacionamento é LAZY, cada departamento diferente gerava uma consulta extra (N+1).
    @EntityGraph(attributePaths = "departamento")
    List<Usuario> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "departamento")
    List<Usuario> findByRoleOrderByNameAsc(Role role);

    // Quantos admins ativos existem (o sistema nunca pode ficar sem nenhum).
    long countByRoleAndAtivoTrue(Role role);

    // Quantos usuários ativos há num departamento (usado antes de desativá-lo).
    long countByDepartamentoIdAndAtivoTrue(Long departamentoId);

    // Usado no login.
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
