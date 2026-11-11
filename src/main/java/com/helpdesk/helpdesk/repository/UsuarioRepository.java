package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;

import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // As alterações administrativas usam a mesma linha como ponto fixo de bloqueio.
    // O primeiro usuário continua existindo mesmo se for desativado ou trocar de role,
    // porque a aplicação não apaga usuários. Bloquear só os admins ativos não seria
    // um ponto fixo: esse conjunto muda justamente durante as operações protegidas.
    // Sem EntityGraph: o bloqueio deve atingir apenas essa linha de usuário.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findFirstByOrderByIdAsc();

    // A linha do usuário já existe mesmo antes da primeira solicitação.
    // Bloqueá-la faz duas solicitações da mesma pessoa esperarem uma pela outra,
    // sem impedir que pessoas diferentes façam suas solicitações ao mesmo tempo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select usuario from Usuario usuario where usuario.id = :id")
    Optional<Usuario> buscarPorIdParaSolicitacao(@Param("id") Long id);

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
