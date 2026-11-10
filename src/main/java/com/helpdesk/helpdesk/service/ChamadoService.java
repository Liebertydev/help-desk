package com.helpdesk.helpdesk.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.ChamadoRequest;
import com.helpdesk.helpdesk.dto.ChamadoResponse;
import com.helpdesk.helpdesk.dto.ChamadoUpdateRequest;
import com.helpdesk.helpdesk.dto.ComentarioRequest;
import com.helpdesk.helpdesk.dto.HistoricoChamadoResponse;
import com.helpdesk.helpdesk.exception.AcessoNegadoException;
import com.helpdesk.helpdesk.exception.RecursoNaoEncontradoException;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Categoria;
import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.HistoricoChamado;
import com.helpdesk.helpdesk.models.Prioridade;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.StatusChamado;
import com.helpdesk.helpdesk.models.TipoHistorico;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.ChamadoRepository;
import com.helpdesk.helpdesk.repository.HistoricoChamadoRepository;

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
 * - Técnicos e admins seguem o fluxo inteiro. O dono do chamado só age depois
 *   que ele foi RESOLVIDO: aceita a solução (FECHADO) ou reabre (EM_ANDAMENTO).
 * - FECHADO é final: o chamado não pode mais ser alterado nem receber comentários.
 *
 * Histórico: abertura, mudanças de status e trocas de técnico são registradas
 * automaticamente; usuário e técnico também podem comentar.
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
    private final HistoricoChamadoRepository historicoRepository;
    private final UsuarioService usuarioService;
    private final DepartamentoService departamentoService;

    public ChamadoService(ChamadoRepository chamadoRepository,
            HistoricoChamadoRepository historicoRepository,
            UsuarioService usuarioService,
            DepartamentoService departamentoService) {
        this.chamadoRepository = chamadoRepository;
        this.historicoRepository = historicoRepository;
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

    /** @param usuarioLogadoId quem está pedindo: precisa ser o dono, técnico ou admin. */
    @Transactional(readOnly = true)
    public ChamadoResponse buscarPorId(Long id, Long usuarioLogadoId) {
        Chamado chamado = buscarEntidade(id);
        garantirAcesso(chamado, usuarioLogadoId);
        return ChamadoResponse.from(chamado);
    }

    // ---------- Abertura e edição ----------

    /** @param usuarioId quem está abrindo (o usuário logado). */
    @Transactional
    public ChamadoResponse abrir(ChamadoRequest request, Long usuarioId) {
        Usuario usuario = usuarioService.buscarEntidade(usuarioId);

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
                request.categoria(),
                textoOuNulo(request.numeroPatrimonio()),
                textoOuNulo(request.equipamento()),
                usuario,
                null, // técnico é atribuído depois
                departamento);
        chamado.setLocal(textoOuNulo(request.local()));
        chamado.setSala(textoOuNulo(request.sala()));
        chamado.setDetalheLocal(textoOuNulo(request.detalheLocal()));
        chamado.setPrioridade(request.prioridade() == null ? Prioridade.MEDIA : request.prioridade());
        // status já nasce ABERTO (valor padrão na entidade)

        Chamado salvo = chamadoRepository.save(chamado);
        registrar(salvo, usuario, TipoHistorico.ABERTURA, "Chamado aberto.");
        return ChamadoResponse.from(salvo);
    }

    /** @param usuarioLogadoId quem está editando: precisa ser o dono, técnico ou admin. */
    @Transactional
    public ChamadoResponse atualizar(Long id, ChamadoUpdateRequest request, Long usuarioLogadoId) {
        Chamado chamado = buscarEntidade(id);
        garantirAcesso(chamado, usuarioLogadoId);
        garantirQueNaoEstaFechado(chamado);

        boolean mudouPrioridade = request.prioridade() != chamado.getPrioridade();
        boolean mudouPrevisao = !Objects.equals(request.dataPrevisaoResolucao(), chamado.getDataPrevisaoResolucao());

        // Prioridade e previsão são decisões do atendimento: o dono pode editar
        // o resto do chamado, mas não pode, por exemplo, marcar o próprio chamado como URGENTE.
        if ((mudouPrioridade || mudouPrevisao) && !ehEquipe(usuarioService.buscarEntidade(usuarioLogadoId))) {
            throw new AcessoNegadoException(
                    "Só técnicos e administradores podem alterar a prioridade e a previsão de resolução.");
        }
        if (mudouPrevisao && request.dataPrevisaoResolucao() != null
                && request.dataPrevisaoResolucao().isBefore(LocalDate.now())) {
            throw new RegraNegocioException("A previsão de resolução não pode estar no passado.");
        }

        chamado.setTitulo(request.titulo().trim());
        chamado.setDescricao(request.descricao().trim());
        chamado.setCategoria(request.categoria() == null ? Categoria.OUTROS : request.categoria());
        chamado.setNumeroPatrimonio(textoOuNulo(request.numeroPatrimonio()));
        chamado.setEquipamento(textoOuNulo(request.equipamento()));
        chamado.setLocal(textoOuNulo(request.local()));
        chamado.setSala(textoOuNulo(request.sala()));
        chamado.setDetalheLocal(textoOuNulo(request.detalheLocal()));
        chamado.setPrioridade(request.prioridade());
        chamado.setDataPrevisaoResolucao(request.dataPrevisaoResolucao());

        return salvarEResponder(chamado);
    }

    // ---------- Atendimento ----------

    /**
     * Atribui (ou troca) o técnico responsável.
     * @param usuarioLogadoId quem está fazendo a ação (vai para o histórico).
     */
    @Transactional
    public ChamadoResponse atribuirTecnico(Long id, Long tecnicoId, Long usuarioLogadoId) {
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

        Usuario anterior = chamado.getTecnico();
        if (anterior != null && anterior.getId().equals(tecnico.getId())) {
            throw new RegraNegocioException("\"" + tecnico.getName() + "\" já é o técnico deste chamado.");
        }

        chamado.setTecnico(tecnico);
        registrar(chamado, usuarioService.buscarEntidade(usuarioLogadoId), TipoHistorico.TECNICO,
                anterior == null
                        ? "Técnico atribuído: " + tecnico.getName() + "."
                        : "Técnico alterado de " + anterior.getName() + " para " + tecnico.getName() + ".");
        return salvarEResponder(chamado);
    }

    /** @param usuarioLogadoId quem está fazendo a ação (vai para o histórico). */
    @Transactional
    public ChamadoResponse alterarStatus(Long id, StatusChamado novoStatus, Long usuarioLogadoId) {
        if (novoStatus == null) {
            throw new RegraNegocioException("O status é obrigatório.");
        }

        Chamado chamado = buscarEntidade(id);
        StatusChamado atual = chamado.getStatus();
        Usuario autor = usuarioService.buscarEntidade(usuarioLogadoId);

        garantirPermissaoDeStatus(chamado, autor);

        if (!TRANSICOES_PERMITIDAS.get(atual).contains(novoStatus)) {
            throw new RegraNegocioException("Não é permitido mudar o status de " + atual + " para " + novoStatus + ".");
        }
        if (novoStatus == StatusChamado.EM_ANDAMENTO && chamado.getTecnico() == null) {
            throw new RegraNegocioException("Atribua um técnico antes de colocar o chamado EM_ANDAMENTO.");
        }

        chamado.setStatus(novoStatus);
        registrar(chamado, autor, TipoHistorico.STATUS,
                "Status alterado de " + atual + " para " + novoStatus + ".");
        return salvarEResponder(chamado);
    }

    // ---------- Histórico ----------

    /** Quem pode ver o chamado pode comentar, desde que ele não esteja FECHADO. */
    @Transactional
    public HistoricoChamadoResponse comentar(Long id, ComentarioRequest request, Long usuarioLogadoId) {
        Chamado chamado = buscarEntidade(id);
        garantirAcesso(chamado, usuarioLogadoId);

        if (chamado.getStatus() == StatusChamado.FECHADO) {
            throw new RegraNegocioException("Chamado fechado não aceita novos comentários.");
        }

        HistoricoChamado comentario = registrar(chamado, usuarioService.buscarEntidade(usuarioLogadoId),
                TipoHistorico.COMENTARIO, request.mensagem().trim());
        return HistoricoChamadoResponse.from(comentario);
    }

    /** Linha do tempo completa do chamado, do mais antigo para o mais novo. */
    @Transactional(readOnly = true)
    public List<HistoricoChamadoResponse> listarHistorico(Long id, Long usuarioLogadoId) {
        Chamado chamado = buscarEntidade(id);
        garantirAcesso(chamado, usuarioLogadoId);

        return historicoRepository.findByChamadoIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(HistoricoChamadoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Chamado buscarEntidade(Long id) {
        return chamadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado", id));
    }

    // ---------- Auxiliares ----------

    private HistoricoChamado registrar(Chamado chamado, Usuario autor, TipoHistorico tipo, String mensagem) {
        return historicoRepository.save(new HistoricoChamado(chamado, autor, tipo, mensagem));
    }

    /**
     * Usuário comum só acessa os chamados que ele mesmo abriu.
     * Técnicos e admins acessam qualquer chamado (fazem o atendimento).
     */
    private void garantirAcesso(Chamado chamado, Long usuarioLogadoId) {
        if (chamado.getUsuario().getId().equals(usuarioLogadoId)) {
            return;
        }

        if (!ehEquipe(usuarioService.buscarEntidade(usuarioLogadoId))) {
            throw new AcessoNegadoException("Você só pode acessar os chamados que abriu.");
        }
    }

    /**
     * Técnicos e admins podem mudar o status em qualquer etapa.
     * O dono do chamado só decide depois que o técnico marcou como RESOLVIDO:
     * a partir daí, o mapa de transições só permite FECHADO ou EM_ANDAMENTO,
     * que são exatamente "aceitar a solução" e "reabrir".
     */
    private void garantirPermissaoDeStatus(Chamado chamado, Usuario autor) {
        if (ehEquipe(autor)) {
            return;
        }
        if (!chamado.getUsuario().getId().equals(autor.getId())) {
            throw new AcessoNegadoException("Você só pode acessar os chamados que abriu.");
        }
        if (chamado.getStatus() != StatusChamado.RESOLVIDO) {
            throw new AcessoNegadoException(
                    "Você só pode fechar ou reabrir o chamado depois que o técnico marcá-lo como RESOLVIDO.");
        }
    }

    // "Equipe de atendimento": técnicos e admins.
    private boolean ehEquipe(Usuario usuario) {
        return usuario.getRole() == Role.TECHNICIAN || usuario.getRole() == Role.ADMIN;
    }

    private void garantirQueNaoEstaFechado(Chamado chamado) {
        if (chamado.getStatus() == StatusChamado.FECHADO) {
            throw new RegraNegocioException("Chamado fechado não pode ser alterado.");
        }
    }

    /**
     * O @UpdateTimestamp só é preenchido quando o Hibernate grava no banco (flush),
     * o que normalmente acontece no fim da transação, DEPOIS de a resposta ser montada.
     * O flush() antecipa a gravação para a resposta já sair com o updatedAt novo.
     */
    private ChamadoResponse salvarEResponder(Chamado chamado) {
        chamadoRepository.flush();
        return ChamadoResponse.from(chamado);
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
