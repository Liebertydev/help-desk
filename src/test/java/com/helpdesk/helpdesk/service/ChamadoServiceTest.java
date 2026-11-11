package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class ChamadoServiceTest {

    @Mock
    private ChamadoRepository chamadoRepository;

    @Mock
    private HistoricoChamadoRepository historicoRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private DepartamentoService departamentoService;

    @InjectMocks
    private ChamadoService chamadoService;

    private Departamento rh;
    private Usuario joao;
    private Usuario tecnico;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        rh = new Departamento("RH");
        rh.setId(1L);

        joao = usuario(10L, "João", Role.USER);
        tecnico = usuario(20L, "Carlos", Role.TECHNICIAN);
        admin = usuario(99L, "Admin", Role.ADMIN);

        // lenient: nem todo teste usa esses usuários como logados,
        // e o Mockito não deve reclamar desses stubs quando eles sobrarem.
        lenient().when(usuarioService.buscarEntidade(10L)).thenReturn(joao);
        lenient().when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);
        lenient().when(usuarioService.buscarEntidade(99L)).thenReturn(admin);
    }

    // ---------- abrir ----------

    @Test
    void abrir_deveUsarDepartamentoDoUsuarioEPrioridadeMediaPorPadrao() {
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);
        when(chamadoRepository.save(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));

        ChamadoResponse resposta = chamadoService.abrir(requestAbertura(null), 10L);

        assertThat(resposta.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(resposta.prioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(resposta.departamentoNome()).isEqualTo("RH");
        assertThat(resposta.tecnicoId()).isNull();
    }

    @Test
    void abrir_deveRecusarUsuarioDesativado() {
        joao.setAtivo(false);
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        assertThatThrownBy(() -> chamadoService.abrir(requestAbertura(null), 10L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desativado");

        verify(chamadoRepository, never()).save(any());
    }

    // ---------- atribuirTecnico ----------

    @ParameterizedTest
    @EnumSource(value = Prioridade.class, names = { "BAIXA", "ALTA", "URGENTE" })
    void abrir_usuarioComumNaoPodeDefinirOutraPrioridade(Prioridade prioridade) {
        assertThatThrownBy(() -> chamadoService.abrir(aberturaComPrioridade(prioridade), 10L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("prioridade");
        verify(chamadoRepository, never()).save(any());
        verify(historicoRepository, never()).save(any());
    }

    @Test
    void abrir_usuarioComumPodeInformarPrioridadeMedia() {
        when(chamadoRepository.save(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(chamadoService.abrir(aberturaComPrioridade(Prioridade.MEDIA), 10L).prioridade())
                .isEqualTo(Prioridade.MEDIA);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "TECHNICIAN", "ADMIN" })
    void abrir_equipePodeDefinirPrioridade(Role role) {
        joao.setRole(role);
        when(chamadoRepository.save(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(chamadoService.abrir(aberturaComPrioridade(Prioridade.URGENTE), 10L).prioridade())
                .isEqualTo(Prioridade.URGENTE);
    }

    @Test
    void atribuirTecnico_deveRecusarUsuarioQueNaoETecnico() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.buscarPorIdParaAtribuicao(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(1L, 10L, 99L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não é um técnico");
    }

    @Test
    void atribuirTecnico_deveAtribuirTecnicoAtivo() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.buscarPorIdParaAtribuicao(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);

        ChamadoResponse resposta = chamadoService.atribuirTecnico(1L, 20L, 20L);

        assertThat(resposta.tecnicoNome()).isEqualTo("Carlos");
    }

    @Test
    void atribuirTecnico_deveInformarQuandoOChamadoNaoExiste() {
        when(chamadoRepository.buscarPorIdParaAtribuicao(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(99L, 20L, 20L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");

        verify(historicoRepository, never()).save(any());
    }

    @Test
    void atribuirTecnico_naoDeveAtribuirTecnicoDesativado() {
        tecnico.setAtivo(false);
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.buscarPorIdParaAtribuicao(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(1L, 20L, 99L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desativado");

        assertThat(chamado.getTecnico()).isNull();
        verify(historicoRepository, never()).save(any());
    }

    @Test
    void atribuirTecnico_naoDeveAlterarChamadoFechado() {
        Chamado chamado = chamado(StatusChamado.FECHADO, tecnico);
        when(chamadoRepository.buscarPorIdParaAtribuicao(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(1L, 20L, 99L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("FECHADO");

        verify(historicoRepository, never()).save(any());
    }

    // ---------- alterarStatus ----------

    @Test
    void alterarStatus_deveExigirTecnicoParaIrParaEmAndamento() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.alterarStatus(1L, StatusChamado.EM_ANDAMENTO, 99L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("técnico");
    }

    @Test
    void alterarStatus_deveRecusarPularEtapas() {
        Chamado chamado = chamado(StatusChamado.ABERTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.alterarStatus(1L, StatusChamado.RESOLVIDO, 20L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ABERTO para RESOLVIDO");
    }

    @Test
    void alterarStatus_devePermitirReabrirChamadoResolvido() {
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);

        ChamadoResponse resposta = chamadoService.alterarStatus(1L, StatusChamado.EM_ANDAMENTO, 20L);

        assertThat(resposta.status()).isEqualTo(StatusChamado.EM_ANDAMENTO);
    }

    @Test
    void alterarStatus_naoDevePermitirSairDeFechado() {
        Chamado chamado = chamado(StatusChamado.FECHADO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.alterarStatus(1L, StatusChamado.EM_ANDAMENTO, 20L))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void alterarStatus_reabrirComTecnicoIndisponivelVoltaParaAFila() {
        tecnico.setAtivo(false);
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        ChamadoResponse resposta = chamadoService.alterarStatus(1L, StatusChamado.EM_ANDAMENTO, 10L);

        assertThat(resposta.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(resposta.tecnicoId()).isNull();
        verify(historicoRepository, times(2)).save(any(HistoricoChamado.class));
    }

    @Test
    void alterarStatus_reabrirComTecnicoRebaixadoVoltaParaAFila() {
        tecnico.setRole(Role.USER);
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        ChamadoResponse resposta = chamadoService.alterarStatus(1L, StatusChamado.EM_ANDAMENTO, 10L);

        assertThat(resposta.status()).isEqualTo(StatusChamado.ABERTO);
    }

    // ---------- alterarStatus pelo dono do chamado ----------

    @Test
    void alterarStatus_donoPodeFecharChamadoResolvido() {
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        ChamadoResponse resposta = chamadoService.alterarStatus(1L, StatusChamado.FECHADO, 10L);

        assertThat(resposta.status()).isEqualTo(StatusChamado.FECHADO);
        assertThat(historicoSalvo().getAutor()).isEqualTo(joao);
    }

    @Test
    void alterarStatus_donoPodeReabrirChamadoResolvido() {
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        ChamadoResponse resposta = chamadoService.alterarStatus(1L, StatusChamado.EM_ANDAMENTO, 10L);

        assertThat(resposta.status()).isEqualTo(StatusChamado.EM_ANDAMENTO);
    }

    @Test
    void alterarStatus_donoNaoPodeMudarAntesDeResolvido() {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        assertThatThrownBy(() -> chamadoService.alterarStatus(1L, StatusChamado.RESOLVIDO, 10L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("RESOLVIDO");

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.EM_ANDAMENTO);
    }

    @Test
    void alterarStatus_outroUsuarioComumNaoPodeMudar() {
        Usuario ana = usuario(30L, "Ana", Role.USER);
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(30L)).thenReturn(ana);

        assertThatThrownBy(() -> chamadoService.alterarStatus(1L, StatusChamado.FECHADO, 30L))
                .isInstanceOf(AcessoNegadoException.class);

        verify(historicoRepository, never()).save(any());
    }

    // ---------- atualizar ----------

    @Test
    void atualizar_deveRecusarChamadoFechado() {
        Chamado chamado = chamado(StatusChamado.FECHADO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        ChamadoUpdateRequest request = new ChamadoUpdateRequest(
                "Novo título", "Nova descrição", null, null, null, null, null, null, Prioridade.ALTA, null);

        assertThatThrownBy(() -> chamadoService.atualizar(1L, request, 10L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("fechado");
    }

    // ---------- acesso ao chamado ----------

    @Test
    void buscarPorId_donoPodeVerOProprioChamado() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        ChamadoResponse resposta = chamadoService.buscarPorId(1L, 10L);

        assertThat(resposta.usuarioNome()).isEqualTo("João");
    }

    @Test
    void buscarPorId_outroUsuarioComumNaoPodeVer() {
        Usuario ana = usuario(30L, "Ana", Role.USER);
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(30L)).thenReturn(ana);

        assertThatThrownBy(() -> chamadoService.buscarPorId(1L, 30L))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void buscarPorId_tecnicoPodeVerChamadoDeOutraPessoa() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);

        ChamadoResponse resposta = chamadoService.buscarPorId(1L, 20L);

        assertThat(resposta.id()).isEqualTo(1L);
    }

    @Test
    void atualizar_outroUsuarioComumNaoPodeEditar() {
        Usuario ana = usuario(30L, "Ana", Role.USER);
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(30L)).thenReturn(ana);

        ChamadoUpdateRequest request = new ChamadoUpdateRequest(
                "Título alterado", "Descrição", null, null, null, null, null, null, Prioridade.ALTA, null);

        assertThatThrownBy(() -> chamadoService.atualizar(1L, request, 30L))
                .isInstanceOf(AcessoNegadoException.class);

        assertThat(chamado.getTitulo()).isEqualTo("Título");
    }

    // ---------- histórico ----------

    @Test
    void abrir_deveRegistrarAberturaNoHistorico() {
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);
        when(chamadoRepository.save(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));

        chamadoService.abrir(requestAbertura(null), 10L);

        HistoricoChamado registro = historicoSalvo();
        assertThat(registro.getTipo()).isEqualTo(TipoHistorico.ABERTURA);
        assertThat(registro.getAutor()).isEqualTo(joao);
    }

    @Test
    void alterarStatus_deveRegistrarMudancaNoHistorico() {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);

        chamadoService.alterarStatus(1L, StatusChamado.RESOLVIDO, 20L);

        HistoricoChamado registro = historicoSalvo();
        assertThat(registro.getTipo()).isEqualTo(TipoHistorico.STATUS);
        assertThat(registro.getMensagem()).isEqualTo("Status alterado de EM_ANDAMENTO para RESOLVIDO.");
        assertThat(registro.getAutor()).isEqualTo(tecnico);
    }

    @Test
    void atribuirTecnico_deveRegistrarTrocaDeTecnico() {
        Usuario mariana = usuario(21L, "Mariana", Role.TECHNICIAN);
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.buscarPorIdParaAtribuicao(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(21L)).thenReturn(mariana);

        chamadoService.atribuirTecnico(1L, 21L, 99L);

        assertThat(historicoSalvo().getMensagem()).isEqualTo("Técnico alterado de Carlos para Mariana.");
    }

    @Test
    void atribuirTecnico_deveRecusarOMesmoTecnico() {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.buscarPorIdParaAtribuicao(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(1L, 20L, 99L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já é o técnico");
    }

    @Test
    void comentar_donoPodeComentar() {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);
        when(historicoRepository.save(any(HistoricoChamado.class))).thenAnswer(inv -> inv.getArgument(0));

        HistoricoChamadoResponse resposta = chamadoService.comentar(1L, new ComentarioRequest("  Continua igual.  "), 10L);

        assertThat(resposta.tipo()).isEqualTo(TipoHistorico.COMENTARIO);
        assertThat(resposta.mensagem()).isEqualTo("Continua igual.");
        assertThat(resposta.autorNome()).isEqualTo("João");
    }

    @Test
    void comentar_deveRecusarChamadoFechado() {
        Chamado chamado = chamado(StatusChamado.FECHADO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.comentar(1L, new ComentarioRequest("Oi"), 10L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("fechado");

        verify(historicoRepository, never()).save(any());
    }

    @Test
    void comentar_outroUsuarioComumNaoPodeComentar() {
        Usuario ana = usuario(30L, "Ana", Role.USER);
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(30L)).thenReturn(ana);

        assertThatThrownBy(() -> chamadoService.comentar(1L, new ComentarioRequest("Oi"), 30L))
                .isInstanceOf(AcessoNegadoException.class);
    }

    // ---------- atualizar: prioridade e previsão ----------

    @Test
    void atualizar_donoPodeEditarMantendoPrioridadeEPrevisao() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        chamado.setPrioridade(Prioridade.MEDIA);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        ChamadoResponse resposta = chamadoService.atualizar(1L, edicao(Prioridade.MEDIA, null), 10L);

        assertThat(resposta.titulo()).isEqualTo("Título editado");
    }

    @Test
    void atualizar_donoNaoPodeMudarPrioridade() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        chamado.setPrioridade(Prioridade.MEDIA);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        assertThatThrownBy(() -> chamadoService.atualizar(1L, edicao(Prioridade.URGENTE, null), 10L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("prioridade");

        assertThat(chamado.getPrioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(chamado.getTitulo()).isEqualTo("Título");
    }

    @Test
    void atualizar_donoNaoPodeMudarPrevisao() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        assertThatThrownBy(() -> chamadoService.atualizar(1L,
                edicao(chamado.getPrioridade(), LocalDate.now().plusDays(1)), 10L))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void atualizar_tecnicoPodeMudarPrioridadeEPrevisao() {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        LocalDate amanha = LocalDate.now().plusDays(1);

        ChamadoResponse resposta = chamadoService.atualizar(1L, edicao(Prioridade.URGENTE, amanha), 20L);

        assertThat(resposta.prioridade()).isEqualTo(Prioridade.URGENTE);
        assertThat(resposta.dataPrevisaoResolucao()).isEqualTo(amanha);
    }

    @Test
    void atualizar_naoAceitaNovaPrevisaoNoPassado() {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.atualizar(1L,
                edicao(chamado.getPrioridade(), LocalDate.now().minusDays(1)), 20L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("passado");
    }

    @Test
    void atualizar_aceitaManterPrevisaoQueJaPassou() {
        LocalDate anteontem = LocalDate.now().minusDays(2);
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, tecnico);
        chamado.setDataPrevisaoResolucao(anteontem);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        ChamadoResponse resposta = chamadoService.atualizar(1L, edicao(chamado.getPrioridade(), anteontem), 10L);

        assertThat(resposta.titulo()).isEqualTo("Título editado");
        assertThat(resposta.dataPrevisaoResolucao()).isEqualTo(anteontem);
    }

    // ---------- só o técnico atribuído (ou admin) mexe no chamado ----------

    @Test
    void alterarStatus_outroTecnicoNaoPodeMudar() {
        Usuario mariana = usuario(21L, "Mariana", Role.TECHNICIAN);
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(21L)).thenReturn(mariana);

        assertThatThrownBy(() -> chamadoService.alterarStatus(1L, StatusChamado.RESOLVIDO, 21L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("técnico atribuído");

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.EM_ANDAMENTO);
    }

    @Test
    void alterarStatus_adminPodeMudarChamadoDeQualquerTecnico() {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        ChamadoResponse resposta = chamadoService.alterarStatus(1L, StatusChamado.RESOLVIDO, 99L);

        assertThat(resposta.status()).isEqualTo(StatusChamado.RESOLVIDO);
    }

    @Test
    void atualizar_outroTecnicoNaoPodeEditar() {
        Usuario mariana = usuario(21L, "Mariana", Role.TECHNICIAN);
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(21L)).thenReturn(mariana);

        assertThatThrownBy(() -> chamadoService.atualizar(1L, edicao(chamado.getPrioridade(), null), 21L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("técnico atribuído");

        assertThat(chamado.getTitulo()).isEqualTo("Título");
    }

    @Test
    void atribuirTecnico_tecnicoNaoPodeAssumirChamadoDeOutro() {
        Usuario mariana = usuario(21L, "Mariana", Role.TECHNICIAN);
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.buscarPorIdParaAtribuicao(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(21L)).thenReturn(mariana);

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(1L, 21L, 21L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já tem técnico");

        assertThat(chamado.getTecnico()).isEqualTo(tecnico);
        verify(historicoRepository, never()).save(any());
    }

    @Test
    void atribuirTecnico_tecnicoNaoPodeAtribuirOutroTecnico() {
        Usuario mariana = usuario(21L, "Mariana", Role.TECHNICIAN);
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.buscarPorIdParaAtribuicao(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(1L, 21L, 20L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("assumir chamados para si");

        assertThat(chamado.getTecnico()).isNull();
    }

    // ---------- auxiliares ----------

    private ChamadoUpdateRequest edicao(Prioridade prioridade, LocalDate previsao) {
        return new ChamadoUpdateRequest("Título editado", "Descrição", null, null, null, null, null, null,
                prioridade, previsao);
    }

    private HistoricoChamado historicoSalvo() {
        ArgumentCaptor<HistoricoChamado> captor = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historicoRepository).save(captor.capture());
        return captor.getValue();
    }

    private ChamadoRequest requestAbertura(Long departamentoId) {
        return new ChamadoRequest("Impressora não imprime", "Papel atolado.", Categoria.IMPRESSORA,
                "PAT-1", "Impressora", null, null, null, null, departamentoId);
    }

    private ChamadoRequest aberturaComPrioridade(Prioridade prioridade) {
        return new ChamadoRequest("Sem internet", "A rede caiu.", Categoria.REDE,
                null, null, null, null, null, prioridade, null);
    }

    private Usuario usuario(Long id, String nome, Role role) {
        Usuario usuario = new Usuario(nome, nome.toLowerCase() + "@email.com", "hash", null, rh);
        usuario.setId(id);
        usuario.setRole(role);
        return usuario;
    }

    private Chamado chamado(StatusChamado status, Usuario tecnicoAtribuido) {
        Chamado chamado = new Chamado("Título", "Descrição", null, null, null, joao, tecnicoAtribuido, rh);
        chamado.setId(1L);
        chamado.setStatus(status);
        return chamado;
    }
}
