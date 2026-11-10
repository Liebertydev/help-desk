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

/**
 * Regras de negócio de departamento.
 *
 * O controller só recebe a requisição e chama este service;
 * quem decide se a operação é permitida é o service.
 */
@Service
public class DepartamentoService {

    private final DepartamentoRepository departamentoRepository;

    // Injeção pelo construtor: o Spring entrega o repository automaticamente.
    public DepartamentoService(DepartamentoRepository departamentoRepository) {
        this.departamentoRepository = departamentoRepository;
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
     */
    @Transactional
    public void desativar(Long id) {
        Departamento departamento = buscarEntidade(id);

        if (!departamento.isAtivo()) {
            throw new RegraNegocioException("O departamento já está desativado.");
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
}
