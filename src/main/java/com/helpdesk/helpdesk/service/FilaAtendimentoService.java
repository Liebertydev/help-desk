package com.helpdesk.helpdesk.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.HistoricoChamado;
import com.helpdesk.helpdesk.models.StatusChamado;
import com.helpdesk.helpdesk.models.TipoHistorico;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.ChamadoRepository;
import com.helpdesk.helpdesk.repository.HistoricoChamadoRepository;

/**
 * Devolve à fila os chamados de um técnico que deixou de atender
 * (foi desativado ou deixou de ser TECHNICIAN).
 *
 * Fica separado do ChamadoService de propósito: o UsuarioService precisa chamar
 * este código, e o ChamadoService já depende do UsuarioService. Se o UsuarioService
 * dependesse do ChamadoService, haveria uma dependência circular, que o Spring recusa.
 */
@Service
public class FilaAtendimentoService {

    // Chamados que ainda precisam de alguém atendendo.
    // RESOLVIDO e FECHADO mantêm o técnico, como registro de quem resolveu.
    private static final Set<StatusChamado> PENDENTES = Set.of(StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO);

    private final ChamadoRepository chamadoRepository;
    private final HistoricoChamadoRepository historicoRepository;

    public FilaAtendimentoService(ChamadoRepository chamadoRepository,
            HistoricoChamadoRepository historicoRepository) {
        this.chamadoRepository = chamadoRepository;
        this.historicoRepository = historicoRepository;
    }

    /**
     * @param tecnico quem deixou de atender.
     * @param autor   quem fez a ação (o admin), para o histórico.
     * @param motivo  ex.: "técnico desativado".
     * @return quantos chamados voltaram para a fila.
     */
    @Transactional
    public int devolverChamadosDoTecnico(Usuario tecnico, Usuario autor, String motivo) {
        List<Chamado> chamados = chamadoRepository.findByTecnicoIdAndStatusIn(tecnico.getId(), PENDENTES);

        for (Chamado chamado : chamados) {
            chamado.setTecnico(null);
            registrar(chamado, autor, TipoHistorico.TECNICO,
                    "Técnico " + tecnico.getName() + " removido do chamado (" + motivo + "). Chamado devolvido à fila.");

            // EM_ANDAMENTO exige técnico, então o chamado volta para ABERTO.
            if (chamado.getStatus() == StatusChamado.EM_ANDAMENTO) {
                chamado.setStatus(StatusChamado.ABERTO);
                registrar(chamado, autor, TipoHistorico.STATUS, "Status alterado de EM_ANDAMENTO para ABERTO.");
            }
        }
        return chamados.size();
    }

    private void registrar(Chamado chamado, Usuario autor, TipoHistorico tipo, String mensagem) {
        historicoRepository.save(new HistoricoChamado(chamado, autor, tipo, mensagem));
    }
}
