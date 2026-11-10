package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @BeforeEach
    void setUp() {
        rh = new Departamento("RH");
        rh.setId(1L);

        joao = usuario(10L, "João", Role.USER);
        tecnico = usuario(20L, "Carlos", Role.TECHNICIAN);

        // lenient: nem todo teste usa o técnico como usuário logado,
        // e o Mockito não deve reclamar desse stub quando ele sobrar.
        lenient().when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);
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

    @Test
    void atribuirTecnico_deveRecusarUsuarioQueNaoETecnico() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(10L)).thenReturn(joao);

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(1L, 10L, 20L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não é um técnico");
    }

    @Test
    void atribuirTecnico_deveAtribuirTecnicoAtivo() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);

        ChamadoResponse resposta = chamadoService.atribuirTecnico(1L, 20L, 20L);

        assertThat(resposta.tecnicoNome()).isEqualTo("Carlos");
    }

    // ---------- alterarStatus ----------

    @Test
    void alterarStatus_deveExigirTecnicoParaIrParaEmAndamento() {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));

        assertThatThrownBy(() -> chamadoService.alterarStatus(1L, StatusChamado.EM_ANDAMENTO, 20L))
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
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(21L)).thenReturn(mariana);
        when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);

        chamadoService.atribuirTecnico(1L, 21L, 20L);

        assertThat(historicoSalvo().getMensagem()).isEqualTo("Técnico alterado de Carlos para Mariana.");
    }

    @Test
    void atribuirTecnico_deveRecusarOMesmoTecnico() {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, tecnico);
        when(chamadoRepository.findById(1L)).thenReturn(Optional.of(chamado));
        when(usuarioService.buscarEntidade(20L)).thenReturn(tecnico);

        assertThatThrownBy(() -> chamadoService.atribuirTecnico(1L, 20L, 20L))
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

    // ---------- auxiliares ----------

    private HistoricoChamado historicoSalvo() {
        ArgumentCaptor<HistoricoChamado> captor = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historicoRepository).save(captor.capture());
        return captor.getValue();
    }

    private ChamadoRequest requestAbertura(Long departamentoId) {
        return new ChamadoRequest("Impressora não imprime", "Papel atolado.", Categoria.IMPRESSORA,
                "PAT-1", "Impressora", null, null, null, null, departamentoId);
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
