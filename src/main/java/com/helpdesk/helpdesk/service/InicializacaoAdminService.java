package com.helpdesk.helpdesk.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.UsuarioRequest;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

import jakarta.validation.Validator;

/** Cria somente o primeiro administrador. Não promove nem redefine contas existentes. */
@Service
public class InicializacaoAdminService {

    private static final Logger log = LoggerFactory.getLogger(InicializacaoAdminService.class);

    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final JdbcTemplate jdbcTemplate;

    public InicializacaoAdminService(UsuarioRepository usuarioRepository,
            DepartamentoRepository departamentoRepository, PasswordEncoder passwordEncoder,
            Validator validator, JdbcTemplate jdbcTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.departamentoRepository = departamentoRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean inicializar(String nome, String email, String senha, String nomeDepartamento) {
        // No banco vazio ainda não existe uma linha que possa servir como bloqueio.
        // Este bloqueio de tabelas é exclusivo da inicialização: duas instâncias não
        // podem verificar o banco vazio e criar dois primeiros administradores.
        // A mesma transação confirma o departamento e o admin, ou desfaz os dois.
        jdbcTemplate.execute("LOCK TABLE departamento, users IN SHARE ROW EXCLUSIVE MODE");

        if (usuarioRepository.count() > 0) {
            if (usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN) == 0) {
                throw new IllegalStateException(
                        "O banco já possui usuários, mas não tem administrador ativo. "
                        + "A inicialização não altera contas existentes; revise esse banco manualmente.");
            }
            log.info("Inicialização ignorada: já existem usuários e administrador ativo. Nenhuma conta foi alterada.");
            return false;
        }

        nome = normalizarTexto(nome);
        email = normalizarTexto(email).toLowerCase(Locale.ROOT);
        nomeDepartamento = normalizarTexto(nomeDepartamento);
        validarConfiguracao(nome, email, senha, nomeDepartamento);

        Departamento departamento = departamentoRepository.findByNomeIgnoreCase(nomeDepartamento).orElse(null);
        if (departamento == null) {
            departamento = departamentoRepository.save(new Departamento(nomeDepartamento));
        } else if (!departamento.isAtivo()) {
            throw new IllegalStateException("O departamento informado para a inicialização está desativado.");
        }

        Usuario admin = new Usuario(nome, email, passwordEncoder.encode(senha), null, departamento);
        admin.setRole(Role.ADMIN);
        usuarioRepository.save(admin);

        // Credenciais, inclusive a senha e seu hash, nunca são registradas neste fluxo.
        log.info("Primeiro administrador criado. Desabilite BOOTSTRAP_HABILITADO e remova a senha da configuração.");
        return true;
    }

    private void validarConfiguracao(String nome, String email, String senha, String nomeDepartamento) {
        if (nome.isBlank() || nome.length() > 150) {
            throw new IllegalArgumentException("BOOTSTRAP_ADMIN_NOME é obrigatório e deve ter até 150 caracteres.");
        }
        if (email.length() > 255 || !validator.validateValue(UsuarioRequest.class, "email", email).isEmpty()) {
            throw new IllegalArgumentException("BOOTSTRAP_ADMIN_EMAIL deve conter um e-mail válido de até 255 caracteres.");
        }
        // BCrypt limita bytes UTF-8, não caracteres. A senha não é aparada com trim().
        if (senha == null || senha.isBlank() || senha.length() < 12
                || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "BOOTSTRAP_ADMIN_SENHA deve ter pelo menos 12 caracteres e no máximo 72 bytes em UTF-8.");
        }
        if (nomeDepartamento.isBlank() || nomeDepartamento.length() > 100) {
            throw new IllegalArgumentException("BOOTSTRAP_DEPARTAMENTO é obrigatório e deve ter até 100 caracteres.");
        }
    }

    private String normalizarTexto(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
