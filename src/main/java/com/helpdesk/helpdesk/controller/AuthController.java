package com.helpdesk.helpdesk.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.auth.AutenticacaoInterceptor;
import com.helpdesk.helpdesk.auth.Publico;
import com.helpdesk.helpdesk.dto.LoginRequest;
import com.helpdesk.helpdesk.dto.LoginResponse;
import com.helpdesk.helpdesk.dto.UsuarioResponse;
import com.helpdesk.helpdesk.service.AuthService;
import com.helpdesk.helpdesk.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    public AuthController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    // Body: { "email": "admin@helpdesk.com", "senha": "123456" }
    @Publico
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization.substring("Bearer ".length()).trim());
        return ResponseEntity.noContent().build();
    }

    // Dados de quem está logado (o front usa para saber o nome e o role).
    @GetMapping("/me")
    public UsuarioResponse me(@RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO_ID) Long usuarioLogadoId) {
        return usuarioService.buscarPorId(usuarioLogadoId, usuarioLogadoId);
    }
}
