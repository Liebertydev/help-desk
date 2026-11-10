package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.helpdesk.helpdesk.dto.DepartamentoRequest;
import com.helpdesk.helpdesk.dto.DepartamentoResponse;
import com.helpdesk.helpdesk.exception.RecursoNaoEncontradoException;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

/**
 * Teste unitário: o repository é simulado (mock) com Mockito,
 * então o teste roda sem banco de dados.
 */
@ExtendWith(MockitoExtension.class)
class DepartamentoServiceTest {

    @Mock
    private DepartamentoRepository departamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private DepartamentoService departamentoService;

    @Test
    void criar_deveSalvarComNomeSemEspacosExtras() {
        when(departamentoRepository.existsByNomeIgnoreCase("Jurídico")).thenReturn(false);
        when(departamentoRepository.save(any(Departamento.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartamentoResponse resposta = departamentoService.criar(new DepartamentoRequest("  Jurídico  "));

        assertThat(resposta.nome()).isEqualTo("Jurídico");
        assertThat(resposta.ativo()).isTrue();
    }

    @Test
    void criar_deveRecusarNomeDuplicado() {
        when(departamentoRepository.existsByNomeIgnoreCase("Financeiro")).thenReturn(true);

        assertThatThrownBy(() -> departamentoService.criar(new DepartamentoRequest("Financeiro")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Já existe");

        verify(departamentoRepository, never()).save(any());
    }

    @Test
    void atualizar_devePermitirManterOMesmoNome() {
        Departamento existente = departamentoComId(1L, "RH");
        when(departamentoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(departamentoRepository.existsByNomeIgnoreCaseAndIdNot("Recursos Humanos", 1L)).thenReturn(false);

        DepartamentoResponse resposta = departamentoService.atualizar(1L, new DepartamentoRequest("Recursos Humanos"));

        assertThat(resposta.nome()).isEqualTo("Recursos Humanos");
    }

    @Test
    void buscarPorId_deveLancarQuandoNaoExiste() {
        when(departamentoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departamentoService.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    void desativar_deveMarcarComoInativo() {
        Departamento existente = departamentoComId(1L, "TI");
        when(departamentoRepository.findById(1L)).thenReturn(Optional.of(existente));

        departamentoService.desativar(1L);

        assertThat(existente.isAtivo()).isFalse();
    }

    @Test
    void desativar_deveRecusarQuandoJaEstaInativo() {
        Departamento existente = departamentoComId(1L, "TI");
        existente.setAtivo(false);
        when(departamentoRepository.findById(1L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> departamentoService.desativar(1L))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void desativar_deveRecusarQuandoHaUsuariosAtivos() {
        Departamento existente = departamentoComId(1L, "TI");
        when(departamentoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.countByDepartamentoIdAndAtivoTrue(1L)).thenReturn(3L);

        assertThatThrownBy(() -> departamentoService.desativar(1L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("3 usuário(s) ativo(s)");

        assertThat(existente.isAtivo()).isTrue();
    }

    private Departamento departamentoComId(Long id, String nome) {
        Departamento departamento = new Departamento(nome);
        departamento.setId(id);
        return departamento;
    }
}
