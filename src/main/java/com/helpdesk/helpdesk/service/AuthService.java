package com.helpdesk.helpdesk.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.dto.LoginRequest;
import com.helpdesk.helpdesk.dto.LoginResponse;
import com.helpdesk.helpdesk.dto.UsuarioResponse;
import com.helpdesk.helpdesk.exception.NaoAutenticadoException;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

@Service
public class AuthService {

    // Mesma mensagem para e-mail inexistente e senha errada:
    // assim ninguém descobre quais e-mails estão cadastrados.
    private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessaoService sessaoService;

    public AuthService(UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            SessaoService sessaoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessaoService = sessaoService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new NaoAutenticadoException(CREDENCIAIS_INVALIDAS));

        if (!passwordEncoder.matches(request.senha(), usuario.getPassword())) {
            throw new NaoAutenticadoException(CREDENCIAIS_INVALIDAS);
        }
        if (!usuario.isAtivo()) {
            throw new NaoAutenticadoException("Usuário desativado.");
        }

        String token = sessaoService.criar(usuario.getId());
        return new LoginResponse(token, UsuarioResponse.from(usuario));
    }

    public void logout(String token) {
        sessaoService.encerrar(token);
    }

    /**
     * Usado pelo interceptor a cada requisição. Busca o usuário no banco toda vez,
     * então um usuário desativado perde o acesso na hora, mesmo com token válido.
     */
    @Transactional(readOnly = true)
    public Usuario usuarioDoToken(String token) {
        Long usuarioId = sessaoService.buscarUsuarioId(token)
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));

        if (!usuario.isAtivo()) {
            // Também descarta os demais tokens se a conta foi desativada fora deste fluxo.
            sessaoService.encerrarTodasDoUsuario(usuarioId);
            throw new NaoAutenticadoException("Usuário desativado.");
        }
        return usuario;
    }
}
