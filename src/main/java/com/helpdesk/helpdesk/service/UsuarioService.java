package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.AlterarSenhaRequest;
import com.helpdesk.helpdesk.dto.UsuarioRequest;
import com.helpdesk.helpdesk.dto.UsuarioResponse;
import com.helpdesk.helpdesk.dto.UsuarioUpdateRequest;
import com.helpdesk.helpdesk.exception.AcessoNegadoException;
import com.helpdesk.helpdesk.exception.RecursoNaoEncontradoException;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;

/**
 * Regras de negócio de usuário.
 *
 * Quem pode chamar cada ação (ex.: só ADMIN altera roles) é definido nos
 * controllers com @PermitirRoles. Regras que dependem de QUEM pede e de QUAL
 * usuário é acessado (ex.: ver dados pessoais) ficam aqui no service.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final DepartamentoService departamentoService;
    private final FilaAtendimentoService filaAtendimentoService;
    private final PasswordEncoder passwordEncoder;
    private final SessaoService sessaoService;
    private final EntityManager entityManager;

    public UsuarioService(UsuarioRepository usuarioRepository,
            DepartamentoService departamentoService,
            FilaAtendimentoService filaAtendimentoService,
            PasswordEncoder passwordEncoder,
            SessaoService sessaoService,
            EntityManager entityManager) {
        this.usuarioRepository = usuarioRepository;
        this.departamentoService = departamentoService;
        this.filaAtendimentoService = filaAtendimentoService;
        this.passwordEncoder = passwordEncoder;
        this.sessaoService = sessaoService;
        this.entityManager = entityManager;
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

    /**
     * A resposta tem dados pessoais (CPF, matrícula, e-mail), então um usuário
     * comum só vê os próprios dados. Técnicos e admins veem qualquer usuário,
     * porque precisam deles no atendimento e na administração.
     *
     * @param usuarioLogadoId quem está pedindo.
     */
    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id, Long usuarioLogadoId) {
        if (!id.equals(usuarioLogadoId)) {
            Role role = buscarEntidade(usuarioLogadoId).getRole();
            if (role != Role.TECHNICIAN && role != Role.ADMIN) {
                throw new AcessoNegadoException("Você só pode ver os seus próprios dados.");
            }
        }
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
        // Todos os dispositivos, inclusive o atual, precisam entrar com a nova senha.
        sessaoService.encerrarTodasDoUsuario(id);
    }

    // ---------- Ações de administrador ----------

    /**
     * Também é usado pelo SolicitacaoService ao aprovar uma solicitação (USER -> TECHNICIAN).
     * Se o usuário deixa de ser técnico, os chamados pendentes dele voltam para a fila.
     *
     * @param usuarioLogadoId quem está alterando (vai para o histórico dos chamados).
     */
    @Transactional
    public UsuarioResponse alterarRole(Long id, Role novoRole, Long usuarioLogadoId) {
        if (novoRole == null) {
            throw new RegraNegocioException("O role é obrigatório.");
        }

        Usuario usuario = buscarParaAlteracaoAdministrativa(id);

        if (usuario.getRole() == novoRole) {
            throw new RegraNegocioException("O usuário já possui o role " + novoRole + ".");
        }
        if (!usuario.isAtivo()) {
            throw new RegraNegocioException("Não é possível alterar o role de um usuário desativado.");
        }
        if (usuario.getRole() == Role.ADMIN) {
            garantirQueNaoEOUltimoAdmin("rebaixar");
        }

        // Só TECHNICIAN pode ser atribuído a chamados: quem deixa de ser técnico
        // (inclusive promovido a ADMIN) não pode continuar com chamados pendentes.
        if (usuario.getRole() == Role.TECHNICIAN) {
            filaAtendimentoService.devolverChamadosDoTecnico(usuario, buscarEntidade(usuarioLogadoId),
                    "deixou de ser técnico");
        }

        usuario.setRole(novoRole);
        return UsuarioResponse.from(usuario);
    }

    /**
     * Exclusão lógica: o usuário continua no banco por causa dos chamados ligados a ele.
     * Se for técnico, os chamados pendentes dele voltam para a fila.
     *
     * @param usuarioLogadoId quem está desativando (vai para o histórico dos chamados).
     */
    @Transactional
    public void desativar(Long id, Long usuarioLogadoId) {
        Usuario usuario = buscarParaAlteracaoAdministrativa(id);

        if (!usuario.isAtivo()) {
            throw new RegraNegocioException("O usuário já está desativado.");
        }
        if (usuario.getRole() == Role.ADMIN) {
            garantirQueNaoEOUltimoAdmin("desativar");
        }
        if (usuario.getRole() == Role.TECHNICIAN) {
            filaAtendimentoService.devolverChamadosDoTecnico(usuario, buscarEntidade(usuarioLogadoId),
                    "técnico desativado");
        }

        usuario.setAtivo(false);
        // Reativar a conta depois não pode tornar seus tokens antigos válidos novamente.
        sessaoService.encerrarTodasDoUsuario(id);
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

    private Usuario buscarParaAlteracaoAdministrativa(Long id) {
        // Duas operações sobre admins DIFERENTES também precisam esperar uma pela outra.
        // Por isso, ambas bloqueiam o primeiro usuário antes de ler a conta e contar admins.
        // O banco mantém esse bloqueio até o commit ou rollback da transação inteira.
        usuarioRepository.findFirstByOrderByIdAsc();

        Usuario usuario = buscarEntidade(id);
        // Uma transação pode ter carregado essa conta antes de esperar pelo bloqueio.
        // refresh() descarta essa leitura antiga e busca o role e o estado ativo atuais.
        entityManager.refresh(usuario);
        return usuario;
    }

    /**
     * O sistema precisa ter sempre pelo menos um ADMIN ativo: sem ele,
     * ninguém mais conseguiria promover usuários nem aprovar solicitações.
     * Esta contagem só é feita depois de obter o bloqueio administrativo compartilhado.
     */
    private void garantirQueNaoEOUltimoAdmin(String acao) {
        if (usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN) <= 1) {
            throw new RegraNegocioException("Não é possível " + acao + " o último administrador ativo do sistema.");
        }
    }

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
