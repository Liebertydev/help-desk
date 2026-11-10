package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.AlterarSenhaRequest;
import com.helpdesk.helpdesk.dto.UsuarioRequest;
import com.helpdesk.helpdesk.dto.UsuarioResponse;
import com.helpdesk.helpdesk.dto.UsuarioUpdateRequest;
import com.helpdesk.helpdesk.exception.RecursoNaoEncontradoException;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

/**
 * Regras de negócio de usuário.
 *
 * Observação: ainda não há autenticação, então o service não sabe QUEM está
 * fazendo a ação. Quando o login existir, o controller deve garantir que só
 * ADMIN pode chamar alterarRole, ativar e desativar.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final DepartamentoService departamentoService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
            DepartamentoService departamentoService,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.departamentoService = departamentoService;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(Role role) {
        List<Usuario> usuarios = role == null
                ? usuarioRepository.findAllByOrderByNameAsc()
                : usuarioRepository.findByRoleOrderByNameAsc(role);

        return usuarios.stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return UsuarioResponse.from(buscarEntidade(id));
    }

    // ---------- Cadastro e edição ----------

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        String email = normalizarEmail(request.email());
        String cpf = normalizarCpf(request.cpf());
        String matricula = textoOuNulo(request.matricula());

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe um usuário com este e-mail.");
        }
        if (cpf != null && usuarioRepository.existsByCpf(cpf)) {
            throw new RegraNegocioException("Já existe um usuário com este CPF.");
        }
        if (matricula != null && usuarioRepository.existsByMatricula(matricula)) {
            throw new RegraNegocioException("Já existe um usuário com esta matrícula.");
        }

        Departamento departamento = buscarDepartamentoAtivo(request.departamentoId());

        // A senha é criptografada ANTES de chegar ao banco.
        Usuario usuario = new Usuario(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                textoOuNulo(request.ocupacao()),
                departamento);
        usuario.setCpf(cpf);
        usuario.setMatricula(matricula);

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioUpdateRequest request) {
        Usuario usuario = buscarEntidade(id);

        String email = normalizarEmail(request.email());
        String cpf = normalizarCpf(request.cpf());
        String matricula = textoOuNulo(request.matricula());

        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new RegraNegocioException("Já existe um usuário com este e-mail.");
        }
        if (cpf != null && usuarioRepository.existsByCpfAndIdNot(cpf, id)) {
            throw new RegraNegocioException("Já existe um usuário com este CPF.");
        }
        if (matricula != null && usuarioRepository.existsByMatriculaAndIdNot(matricula, id)) {
            throw new RegraNegocioException("Já existe um usuário com esta matrícula.");
        }

        // Só exige departamento ativo se ele estiver sendo TROCADO:
        // quem já está num departamento desativado pode continuar editando o resto.
        if (!usuario.getDepartamento().getId().equals(request.departamentoId())) {
            usuario.setDepartamento(buscarDepartamentoAtivo(request.departamentoId()));
        }

        usuario.setName(request.name().trim());
        usuario.setEmail(email);
        usuario.setCpf(cpf);
        usuario.setMatricula(matricula);
        usuario.setOcupacao(textoOuNulo(request.ocupacao()));

        return UsuarioResponse.from(usuario);
    }

    @Transactional
    public void alterarSenha(Long id, AlterarSenhaRequest request) {
        Usuario usuario = buscarEntidade(id);

        // matches() compara a senha digitada com o hash salvo no banco.
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getPassword())) {
            throw new RegraNegocioException("A senha atual está incorreta.");
        }
        if (passwordEncoder.matches(request.novaSenha(), usuario.getPassword())) {
            throw new RegraNegocioException("A nova senha deve ser diferente da atual.");
        }

        usuario.setPassword(passwordEncoder.encode(request.novaSenha()));
    }

    // ---------- Ações de administrador ----------

    /**
     * Também será usado pelo SolicitacaoService ao aprovar uma solicitação
     * (USER -> TECHNICIAN).
     */
    @Transactional
    public UsuarioResponse alterarRole(Long id, Role novoRole) {
        if (novoRole == null) {
            throw new RegraNegocioException("O role é obrigatório.");
        }

        Usuario usuario = buscarEntidade(id);

        if (usuario.getRole() == novoRole) {
            throw new RegraNegocioException("O usuário já possui o role " + novoRole + ".");
        }
        if (!usuario.isAtivo()) {
            throw new RegraNegocioException("Não é possível alterar o role de um usuário desativado.");
        }

        usuario.setRole(novoRole);
        return UsuarioResponse.from(usuario);
    }

    /** Exclusão lógica: o usuário continua no banco por causa dos chamados ligados a ele. */
    @Transactional
    public void desativar(Long id) {
        Usuario usuario = buscarEntidade(id);

        if (!usuario.isAtivo()) {
            throw new RegraNegocioException("O usuário já está desativado.");
        }

        usuario.setAtivo(false);
    }

    @Transactional
    public void ativar(Long id) {
        Usuario usuario = buscarEntidade(id);

        if (usuario.isAtivo()) {
            throw new RegraNegocioException("O usuário já está ativo.");
        }

        usuario.setAtivo(true);
    }

    /** Público porque os services de Chamado e Solicitação vão precisar dele. */
    @Transactional(readOnly = true)
    public Usuario buscarEntidade(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }

    // ---------- Auxiliares ----------

    private Departamento buscarDepartamentoAtivo(Long departamentoId) {
        Departamento departamento = departamentoService.buscarEntidade(departamentoId);

        if (!departamento.isAtivo()) {
            throw new RegraNegocioException("O departamento \"" + departamento.getNome() + "\" está desativado.");
        }
        return departamento;
    }

    // E-mail sempre minúsculo e sem espaços: "Ana@X.com " e "ana@x.com" são o mesmo e-mail.
    private String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    // Guarda só os dígitos: "123.456.789-01" e "12345678901" são o mesmo CPF.
    private String normalizarCpf(String cpf) {
        String texto = textoOuNulo(cpf);
        return texto == null ? null : texto.replaceAll("\\D", "");
    }

    // Campo opcional vazio vira null. Sem isso, dois usuários com matrícula ""
    // violariam a restrição unique do banco.
    private String textoOuNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
