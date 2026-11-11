package com.helpdesk.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.helpdesk.helpdesk.dto.AlterarSenhaRequest;
import com.helpdesk.helpdesk.dto.UsuarioRequest;
import com.helpdesk.helpdesk.dto.UsuarioResponse;
import com.helpdesk.helpdesk.dto.UsuarioUpdateRequest;
import com.helpdesk.helpdesk.exception.AcessoNegadoException;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private DepartamentoService departamentoService;

    @Mock
    private FilaAtendimentoService filaAtendimentoService;

    @Mock
    private EntityManager entityManager;

    // BCrypt de verdade (não é mock), para testar a criptografia de fato.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UsuarioService usuarioService;
    private SessaoService sessaoService;

    private Departamento ti;

    @BeforeEach
    void setUp() {
        // Sessões reais em memória: os testes verificam se os tokens deixam mesmo de valer.
        sessaoService = new SessaoService(Duration.ofHours(8), Clock.systemUTC());
        usuarioService = new UsuarioService(usuarioRepository, departamentoService, filaAtendimentoService,
                passwordEncoder, sessaoService, entityManager);
        ti = new Departamento("TI");
        ti.setId(1L);
    }

    @Test
    void criar_deveCriptografarSenhaENormalizarDados() {
        when(departamentoService.buscarComBloqueio(1L)).thenReturn(ti);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse resposta = usuarioService.criar(new UsuarioRequest(
                " Ana ", " Ana@Email.COM ", "segredo123", "123.456.789-01", "  ", "Analista", 1L));

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(salvo.capture());

        assertThat(salvo.getValue().getPassword()).isNotEqualTo("segredo123");
        assertThat(passwordEncoder.matches("segredo123", salvo.getValue().getPassword())).isTrue();
        assertThat(resposta.email()).isEqualTo("ana@email.com");
        assertThat(resposta.cpf()).isEqualTo("12345678901");
        assertThat(resposta.matricula()).isNull();
        assertThat(resposta.role()).isEqualTo(Role.USER);
    }

    @Test
    void criar_deveRecusarEmailDuplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("ana@email.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.criar(new UsuarioRequest(
                "Ana", "ana@email.com", "segredo123", null, null, null, 1L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("e-mail");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criar_deveRecusarDepartamentoDesativado() {
        ti.setAtivo(false);
        when(departamentoService.buscarComBloqueio(1L)).thenReturn(ti);

        assertThatThrownBy(() -> usuarioService.criar(new UsuarioRequest(
                "Ana", "ana@email.com", "segredo123", null, null, null, 1L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desativado");
    }

    @Test
    void atualizar_naoDeveExigirDepartamentoAtivoQuandoNaoTroca() {
        ti.setAtivo(false);
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        UsuarioResponse resposta = usuarioService.atualizar(10L, new UsuarioUpdateRequest(
                "Ana Maria", "ana@email.com", null, null, null, 1L));

        assertThat(resposta.name()).isEqualTo("Ana Maria");
    }

    @Test
    void atualizar_deveRecusarTransferenciaParaDepartamentoDesativado() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        Departamento destino = new Departamento("Financeiro");
        destino.setId(2L);
        destino.setAtivo(false);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(departamentoService.buscarComBloqueio(2L)).thenReturn(destino);

        assertThatThrownBy(() -> usuarioService.atualizar(10L,
                new UsuarioUpdateRequest("Ana", "ana@email.com", null, null, null, 2L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desativado");

        assertThat(usuario.getDepartamento()).isSameAs(ti);
    }

    @Test
    void alterarSenha_deveRecusarSenhaAtualErrada() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        String token = sessaoService.criar(10L);

        assertThatThrownBy(() -> usuarioService.alterarSenha(10L,
                new AlterarSenhaRequest("senhaErrada", "novaSenha123")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("incorreta");

        assertThat(sessaoService.buscarUsuarioId(token)).contains(10L);
    }

    @Test
    void alterarSenha_deveGravarNovaSenhaCriptografada() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        usuarioService.alterarSenha(10L, new AlterarSenhaRequest("123456", "novaSenha123"));

        assertThat(passwordEncoder.matches("novaSenha123", usuario.getPassword())).isTrue();
    }

    @Test
    void alterarSenha_deveEncerrarTodasAsSessoesApenasDoUsuario() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        String tokenDoComputador = sessaoService.criar(10L);
        String tokenDoCelular = sessaoService.criar(10L);
        String tokenDeOutraPessoa = sessaoService.criar(20L);

        usuarioService.alterarSenha(10L, new AlterarSenhaRequest("123456", "novaSenha123"));

        assertThat(sessaoService.buscarUsuarioId(tokenDoComputador)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId(tokenDoCelular)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId(tokenDeOutraPessoa)).contains(20L);
    }

    @Test
    void alterarSenha_deveManterSessoesQuandoANovaSenhaForIgualAAtual() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        String token = sessaoService.criar(10L);

        assertThatThrownBy(() -> usuarioService.alterarSenha(10L,
                new AlterarSenhaRequest("123456", "123456")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("diferente");

        assertThat(sessaoService.buscarUsuarioId(token)).contains(10L);
    }

    @Test
    void alterarRole_devePromoverParaTecnico() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        UsuarioResponse resposta = usuarioService.alterarRole(10L, Role.TECHNICIAN, 99L);

        assertThat(resposta.role()).isEqualTo(Role.TECHNICIAN);
    }

    @Test
    void alterarRole_deveRecusarUsuarioDesativado() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        usuario.setAtivo(false);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> usuarioService.alterarRole(10L, Role.TECHNICIAN, 99L))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void alterarRole_naoDeveRebaixarOUltimoAdmin() {
        Usuario admin = usuarioComId(1L, Role.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.alterarRole(1L, Role.USER, 99L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("último administrador");

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void alterarRole_podeRebaixarAdminQuandoHaOutro() {
        Usuario admin = usuarioComId(1L, Role.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN)).thenReturn(2L);

        UsuarioResponse resposta = usuarioService.alterarRole(1L, Role.TECHNICIAN, 99L);

        assertThat(resposta.role()).isEqualTo(Role.TECHNICIAN);
    }

    @Test
    void desativar_naoDeveDesativarOUltimoAdmin() {
        Usuario admin = usuarioComId(1L, Role.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN)).thenReturn(1L);
        String token = sessaoService.criar(1L);

        assertThatThrownBy(() -> usuarioService.desativar(1L, 99L))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(admin.isAtivo()).isTrue();
        assertThat(sessaoService.buscarUsuarioId(token)).contains(1L);
    }

    @Test
    void desativar_podeDesativarAdminQuandoHaOutroAtivo() {
        Usuario admin = usuarioComId(1L, Role.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRoleAndAtivoTrue(Role.ADMIN)).thenReturn(2L);
        String token = sessaoService.criar(1L);

        usuarioService.desativar(1L, 99L);

        assertThat(admin.isAtivo()).isFalse();
        assertThat(sessaoService.buscarUsuarioId(token)).isEmpty();
    }

    @Test
    void buscarPorId_usuarioVeOsProprioDados() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        UsuarioResponse resposta = usuarioService.buscarPorId(10L, 10L);

        assertThat(resposta.id()).isEqualTo(10L);
    }

    @Test
    void buscarPorId_usuarioComumNaoVeDadosDeOutro() {
        Usuario logado = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(logado));

        assertThatThrownBy(() -> usuarioService.buscarPorId(20L, 10L))
                .isInstanceOf(AcessoNegadoException.class);

        verify(usuarioRepository, never()).findById(20L);
    }

    @Test
    void buscarPorId_tecnicoVeDadosDeOutro() {
        Usuario tecnico = usuarioComId(10L, Role.TECHNICIAN);
        Usuario outro = usuarioComId(20L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(tecnico));
        when(usuarioRepository.findById(20L)).thenReturn(Optional.of(outro));

        UsuarioResponse resposta = usuarioService.buscarPorId(20L, 10L);

        assertThat(resposta.id()).isEqualTo(20L);
    }

    // ---------- técnico que deixa de atender ----------

    @Test
    void alterarRole_rebaixarTecnicoDevolveChamadosAFila() {
        Usuario tecnico = usuarioComId(10L, Role.TECHNICIAN);
        Usuario admin = usuarioComId(99L, Role.ADMIN);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(tecnico));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.of(admin));

        usuarioService.alterarRole(10L, Role.USER, 99L);

        verify(filaAtendimentoService).devolverChamadosDoTecnico(tecnico, admin, "deixou de ser técnico");
        assertThat(tecnico.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void desativar_tecnicoDevolveChamadosAFila() {
        Usuario tecnico = usuarioComId(10L, Role.TECHNICIAN);
        Usuario admin = usuarioComId(99L, Role.ADMIN);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(tecnico));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.of(admin));

        usuarioService.desativar(10L, 99L);

        verify(filaAtendimentoService).devolverChamadosDoTecnico(tecnico, admin, "técnico desativado");
        assertThat(tecnico.isAtivo()).isFalse();
    }

    @Test
    void desativar_usuarioComumNaoMexeNaFila() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        usuarioService.desativar(10L, 99L);

        verifyNoInteractions(filaAtendimentoService);
    }

    @Test
    void desativar_deveEncerrarTodasAsSessoesSemRestauraLasNaReativacao() {
        Usuario usuario = usuarioComId(10L, Role.USER);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        String tokenDoComputador = sessaoService.criar(10L);
        String tokenDoCelular = sessaoService.criar(10L);
        String tokenDeOutraPessoa = sessaoService.criar(20L);

        usuarioService.desativar(10L, 99L);
        assertThat(usuario.isAtivo()).isFalse();

        // Nenhum dos tokens antigos foi usado durante a desativação.
        // Mesmo assim, eles precisam continuar inválidos depois de reativar a conta.
        usuarioService.ativar(10L);

        assertThat(usuario.isAtivo()).isTrue();
        assertThat(sessaoService.buscarUsuarioId(tokenDoComputador)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId(tokenDoCelular)).isEmpty();
        assertThat(sessaoService.buscarUsuarioId(tokenDeOutraPessoa)).contains(20L);
    }

    private Usuario usuarioComId(Long id, Role role) {
        Usuario usuario = new Usuario("Ana", "ana@email.com", passwordEncoder.encode("123456"), null, ti);
        usuario.setId(id);
        usuario.setRole(role);
        return usuario;
    }
}
