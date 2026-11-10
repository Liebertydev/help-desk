package com.helpdesk.helpdesk.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.ChamadoRequest;
import com.helpdesk.helpdesk.dto.ChamadoResponse;
import com.helpdesk.helpdesk.dto.ChamadoUpdateRequest;
import com.helpdesk.helpdesk.exception.RecursoNaoEncontradoException;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Prioridade;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.StatusChamado;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.ChamadoRepository;

/**
 * Regras de negócio de chamado.
 *
 * Fluxo de status:
 *
 *   ABERTO → EM_ANDAMENTO → RESOLVIDO → FECHADO
 *                 ↑______________|
 *          (reabrir, se o usuário não aceitar a solução)
 *
 * - EM_ANDAMENTO exige um técnico atribuído.
 * - FECHADO é final: o chamado não pode mais ser alterado.
 */
@Service
public class ChamadoService {

    // Para cada status, os status para os quais ele pode ir.
    // Para mudar o fluxo, basta alterar este mapa.
    private static final Map<StatusChamado, Set<StatusChamado>> TRANSICOES_PERMITIDAS = Map.of(
            StatusChamado.ABERTO, Set.of(StatusChamado.EM_ANDAMENTO),
            StatusChamado.EM_ANDAMENTO, Set.of(StatusChamado.RESOLVIDO),
            StatusChamado.RESOLVIDO, Set.of(StatusChamado.FECHADO, StatusChamado.EM_ANDAMENTO),
            StatusChamado.FECHADO, Set.of());

    private final ChamadoRepository chamadoRepository;
    private final UsuarioService usuarioService;
    private final DepartamentoService departamentoService;

    public ChamadoService(ChamadoRepository chamadoRepository,
            UsuarioService usuarioService,
            DepartamentoService departamentoService) {
        this.chamadoRepository = chamadoRepository;
        this.usuarioService = usuarioService;
        this.departamentoService = departamentoService;
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public List<ChamadoResponse> listar(StatusChamado status) {
        List<Chamado> chamados = status == null
                ? chamadoRepository.findAllByOrderByCreatedAtDesc()
                : chamadoRepository.findByStatusOrderByCreatedAtDesc(status);

        return paraResponse(chamados);
    }

    /** Chamados abertos por um usuário ("meus chamados"). */
    @Transactional(readOnly = true)
    public List<ChamadoResponse> listarPorUsuario(Long usuarioId) {
        usuarioService.buscarEntidade(usuarioId); // 404 se o usuário não existir
        return paraResponse(chamadoRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId));
    }

    /** Chamados atribuídos a um técnico (fila de trabalho). */
    @Transactional(readOnly = true)
    public List<ChamadoResponse> listarPorTecnico(Long tecnicoId) {
        usuarioService.buscarEntidade(tecnicoId);
        return paraResponse(chamadoRepository.findByTecnicoIdOrderByCreatedAtDesc(tecnicoId));
    }

    @Transactional(readOnly = true)
    public ChamadoResponse buscarPorId(Long id) {
        return ChamadoResponse.from(buscarEntidade(id));
    }

    // ---------- Abertura e edição ----------

    @Transactional
    public ChamadoResponse abrir(ChamadoRequest request) {
        Usuario usuario = usuarioService.buscarEntidade(request.usuarioId());

        if (!usuario.isAtivo()) {
            throw new RegraNegocioException("Usuário desativado não pode abrir chamados.");
        }

        // Sem departamento informado, o chamado vai para o departamento de quem abriu.
        Departamento departamento = request.departamentoId() == null
                ? usuario.getDepartamento()
                : departamentoService.buscarEntidade(request.departamentoId());

        if (!departamento.isAtivo()) {
            throw new RegraNegocioException("O departamento \"" + departamento.getNome() + "\" está desativado.");
        }

        Chamado chamado = new Chamado(
                request.titulo().trim(),
                request.descricao().trim(),
                textoOuNulo(request.categoria()),
                textoOuNulo(request.numeroPatrimonio()),
                textoOuNulo(request.equipamento()),
                usuario,
                null, // técnico é atribuído depois
                departamento);
        chamado.setLocal(textoOuNulo(request.local()));
        chamado.setSetor(textoOuNulo(request.setor()));
        chamado.setDetalheLocal(textoOuNulo(request.detalheLocal()));
        chamado.setPrioridade(request.prioridade() == null ? Prioridade.MEDIA : request.prioridade());
        // status já nasce ABERTO (valor padrão na entidade)

        return ChamadoResponse.from(chamadoRepository.save(chamado));
    }

    @Transactional
    public ChamadoResponse atualizar(Long id, ChamadoUpdateRequest request) {
        Chamado chamado = buscarEntidade(id);
        garantirQueNaoEstaFechado(chamado);

        chamado.setTitulo(request.titulo().trim());
        chamado.setDescricao(request.descricao().trim());
        chamado.setCategoria(textoOuNulo(request.categoria()));
        chamado.setNumeroPatrimonio(textoOuNulo(request.numeroPatrimonio()));
        chamado.setEquipamento(textoOuNulo(request.equipamento()));
        chamado.setLocal(textoOuNulo(request.local()));
        chamado.setSetor(textoOuNulo(request.setor()));
        chamado.setDetalheLocal(textoOuNulo(request.detalheLocal()));
        chamado.setPrioridade(request.prioridade());
        chamado.setDataPrevisaoResolucao(request.dataPrevisaoResolucao());

        return ChamadoResponse.from(chamado);
    }

    // ---------- Atendimento ----------

    /** Atribui (ou troca) o técnico responsável. */
    @Transactional
    public ChamadoResponse atribuirTecnico(Long id, Long tecnicoId) {
        Chamado chamado = buscarEntidade(id);

        if (chamado.getStatus() == StatusChamado.RESOLVIDO || chamado.getStatus() == StatusChamado.FECHADO) {
            throw new RegraNegocioException("Não é possível trocar o técnico de um chamado " + chamado.getStatus() + ".");
        }

        Usuario tecnico = usuarioService.buscarEntidade(tecnicoId);

        if (tecnico.getRole() != Role.TECHNICIAN) {
            throw new RegraNegocioException("O usuário \"" + tecnico.getName() + "\" não é um técnico.");
        }
        if (!tecnico.isAtivo()) {
            throw new RegraNegocioException("O técnico \"" + tecnico.getName() + "\" está desativado.");
        }

        chamado.setTecnico(tecnico);
        return ChamadoResponse.from(chamado);
    }

    @Transactional
    public ChamadoResponse alterarStatus(Long id, StatusChamado novoStatus) {
        if (novoStatus == null) {
            throw new RegraNegocioException("O status é obrigatório.");
        }

        Chamado chamado = buscarEntidade(id);
        StatusChamado atual = chamado.getStatus();

        if (!TRANSICOES_PERMITIDAS.get(atual).contains(novoStatus)) {
            throw new RegraNegocioException("Não é permitido mudar o status de " + atual + " para " + novoStatus + ".");
        }
        if (novoStatus == StatusChamado.EM_ANDAMENTO && chamado.getTecnico() == null) {
            throw new RegraNegocioException("Atribua um técnico antes de colocar o chamado EM_ANDAMENTO.");
        }

        chamado.setStatus(novoStatus);
        return ChamadoResponse.from(chamado);
    }

    @Transactional(readOnly = true)
    public Chamado buscarEntidade(Long id) {
        return chamadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado", id));
    }

    // ---------- Auxiliares ----------

    private void garantirQueNaoEstaFechado(Chamado chamado) {
        if (chamado.getStatus() == StatusChamado.FECHADO) {
            throw new RegraNegocioException("Chamado fechado não pode ser alterado.");
        }
    }

    private List<ChamadoResponse> paraResponse(List<Chamado> chamados) {
        return chamados.stream()
                .map(ChamadoResponse::from)
                .toList();
    }

    private String textoOuNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
