package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.DepartamentoRequest;
import com.helpdesk.helpdesk.dto.DepartamentoResponse;
import com.helpdesk.helpdesk.exception.RecursoNaoEncontradoException;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;

/**
 * Regras de negócio de departamento.
 *
 * O controller só recebe a requisição e chama este service;
 * quem decide se a operação é permitida é o service.
 */
@Service
public class DepartamentoService {

    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    // Injeção pelo construtor: o Spring entrega os repositories automaticamente.
    // (UsuarioRepository, e não UsuarioService: o UsuarioService já depende deste
    // service, e depender dele de volta criaria uma dependência circular.)
    public DepartamentoService(DepartamentoRepository departamentoRepository,
            UsuarioRepository usuarioRepository,
            EntityManager entityManager) {
        this.departamentoRepository = departamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
    }

    // readOnly = true: avisa o banco que é só leitura (mais leve).
    @Transactional(readOnly = true)
    public List<DepartamentoResponse> listar(boolean apenasAtivos) {
        List<Departamento> departamentos = apenasAtivos
                ? departamentoRepository.findByAtivoTrueOrderByNomeAsc()
                : departamentoRepository.findAllByOrderByNomeAsc();

        return departamentos.stream()
                .map(DepartamentoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartamentoResponse buscarPorId(Long id) {
        return DepartamentoResponse.from(buscarEntidade(id));
    }

    @Transactional
    public DepartamentoResponse criar(DepartamentoRequest request) {
        String nome = request.nome().trim();

        if (departamentoRepository.existsByNomeIgnoreCase(nome)) {
            throw new RegraNegocioException("Já existe um departamento com o nome \"" + nome + "\".");
        }

        Departamento salvo = departamentoRepository.save(new Departamento(nome));
        return DepartamentoResponse.from(salvo);
    }

    @Transactional
    public DepartamentoResponse atualizar(Long id, DepartamentoRequest request) {
        Departamento departamento = buscarEntidade(id);
        String nome = request.nome().trim();

        if (departamentoRepository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new RegraNegocioException("Já existe um departamento com o nome \"" + nome + "\".");
        }

        departamento.setNome(nome);
        // Não precisa chamar save(): dentro de @Transactional o Hibernate
        // grava as alterações da entidade sozinho ao final do método.
        return DepartamentoResponse.from(departamento);
    }

    /**
     * Exclusão lógica: o departamento é só desativado, não apagado,
     * porque usuários e chamados antigos continuam apontando para ele.
     * Só é permitida quando não há mais usuários ativos no departamento.
     */
    @Transactional
    public void desativar(Long id) {
        // A contagem só acontece após obter o mesmo bloqueio usado pelo cadastro
        // e pela transferência. Assim, nenhum usuário entra entre a contagem e o commit.
        Departamento departamento = buscarComBloqueio(id);

        if (!departamento.isAtivo()) {
            throw new RegraNegocioException("O departamento já está desativado.");
        }

        // Usuários de um departamento desativado não conseguem abrir chamados
        // (a não ser escolhendo outro). Por isso, eles precisam ser movidos antes.
        long usuariosAtivos = usuarioRepository.countByDepartamentoIdAndAtivoTrue(id);
        if (usuariosAtivos > 0) {
            throw new RegraNegocioException("Não é possível desativar o departamento \"" + departamento.getNome()
                    + "\": ele tem " + usuariosAtivos + " usuário(s) ativo(s). Mova-os para outro departamento antes.");
        }

        departamento.setAtivo(false);
    }

    @Transactional
    public void ativar(Long id) {
        Departamento departamento = buscarEntidade(id);

        if (departamento.isAtivo()) {
            throw new RegraNegocioException("O departamento já está ativo.");
        }

        departamento.setAtivo(true);
    }

    /**
     * Busca a entidade ou lança 404. É público porque os services de
     * Usuario e Chamado vão precisar dele para validar o departamento.
     */
    @Transactional(readOnly = true)
    public Departamento buscarEntidade(Long id) {
        return departamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Departamento", id));
    }

    /** O bloqueio permanece até o fim da transação de quem chamou este método. */
    @Transactional
    public Departamento buscarComBloqueio(Long id) {
        Departamento departamento = departamentoRepository.buscarPorIdComBloqueio(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Departamento", id));
        // Uma leitura anterior da mesma transação pode estar desatualizada após a espera.
        entityManager.refresh(departamento);
        return departamento;
    }
}
