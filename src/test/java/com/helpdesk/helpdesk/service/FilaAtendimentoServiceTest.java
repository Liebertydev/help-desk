package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.HistoricoChamado;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.StatusChamado;
import com.helpdesk.helpdesk.models.TipoHistorico;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.ChamadoRepository;
import com.helpdesk.helpdesk.repository.HistoricoChamadoRepository;

@ExtendWith(MockitoExtension.class)
class FilaAtendimentoServiceTest {

    @Mock
    private ChamadoRepository chamadoRepository;

    @Mock
    private HistoricoChamadoRepository historicoRepository;

    @InjectMocks
    private FilaAtendimentoService filaAtendimentoService;

    @Test
    void devolverChamados_removeTecnicoEVoltaEmAndamentoParaAberto() {
        Departamento ti = new Departamento("TI");
        Usuario joao = usuario(10L, "João", Role.USER, ti);
        Usuario carlos = usuario(20L, "Carlos", Role.TECHNICIAN, ti);
        Usuario admin = usuario(99L, "Admin", Role.ADMIN, ti);

        Chamado aberto = new Chamado("A", "x", null, null, null, joao, carlos, ti);
        Chamado emAndamento = new Chamado("B", "x", null, null, null, joao, carlos, ti);
        emAndamento.setStatus(StatusChamado.EM_ANDAMENTO);

        when(chamadoRepository.findByTecnicoIdAndStatusIn(eq(20L), anyCollection()))
                .thenReturn(List.of(aberto, emAndamento));

        int devolvidos = filaAtendimentoService.devolverChamadosDoTecnico(carlos, admin, "técnico desativado");

        assertThat(devolvidos).isEqualTo(2);
        assertThat(aberto.getTecnico()).isNull();
        assertThat(aberto.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(emAndamento.getTecnico()).isNull();
        assertThat(emAndamento.getStatus()).isEqualTo(StatusChamado.ABERTO);

        // 2 remoções de técnico + 1 mudança de status (só o que estava EM_ANDAMENTO).
        ArgumentCaptor<HistoricoChamado> captor = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historicoRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(HistoricoChamado::getTipo)
                .containsExactly(TipoHistorico.TECNICO, TipoHistorico.TECNICO, TipoHistorico.STATUS);
        assertThat(captor.getAllValues()).allMatch(h -> h.getAutor() == admin);
        assertThat(captor.getAllValues().get(0).getMensagem()).contains("Carlos", "técnico desativado");
    }

    @Test
    void devolverChamados_semChamadosPendentesNaoRegistraNada() {
        Departamento ti = new Departamento("TI");
        Usuario carlos = usuario(20L, "Carlos", Role.TECHNICIAN, ti);
        when(chamadoRepository.findByTecnicoIdAndStatusIn(eq(20L), anyCollection())).thenReturn(List.of());

        int devolvidos = filaAtendimentoService.devolverChamadosDoTecnico(carlos, carlos, "técnico desativado");

        assertThat(devolvidos).isZero();
        verify(historicoRepository, times(0)).save(any());
    }

    private Usuario usuario(Long id, String nome, Role role, Departamento departamento) {
        Usuario usuario = new Usuario(nome, nome.toLowerCase() + "@email.com", "hash", null, departamento);
        usuario.setId(id);
        usuario.setRole(role);
        return usuario;
    }
}
