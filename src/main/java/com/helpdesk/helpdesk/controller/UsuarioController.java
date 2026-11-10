package com.helpdesk.helpdesk.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.auth.AutenticacaoInterceptor;
import com.helpdesk.helpdesk.auth.PermitirRoles;
import com.helpdesk.helpdesk.auth.Publico;
import com.helpdesk.helpdesk.dto.AlterarRoleRequest;
import com.helpdesk.helpdesk.dto.AlterarSenhaRequest;
import com.helpdesk.helpdesk.dto.UsuarioRequest;
import com.helpdesk.helpdesk.dto.UsuarioResponse;
import com.helpdesk.helpdesk.dto.UsuarioUpdateRequest;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * Rotas "/me" agem sempre sobre o usuário logado: assim ninguém edita
 * os dados ou a senha de outra pessoa trocando o id na URL.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Técnicos também listam, para ver a equipe ao atribuir chamados.
    // GET /api/usuarios?role=TECHNICIAN
    @PermitirRoles({ Role.TECHNICIAN, Role.ADMIN })
    @GetMapping
    public List<UsuarioResponse> listar(@RequestParam(required = false) Role role) {
        return usuarioService.listar(role);
    }

    // Dados pessoais: usuário comum só vê os próprios; técnico e admin veem todos (regra no service).
    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(
            @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO_ID) Long usuarioLogadoId,
            @PathVariable Long id) {
        return usuarioService.buscarPorId(id, usuarioLogadoId);
    }

    // Público: é o cadastro (auto-registro). Todo usuário novo nasce USER.
    @Publico
    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@RequestBody @Valid UsuarioRequest request) {
        UsuarioResponse criado = usuarioService.criar(request);
        return ResponseEntity.created(URI.create("/api/usuarios/" + criado.id())).body(criado);
    }

    // O próprio usuário edita os seus dados.
    @PutMapping("/me")
    public UsuarioResponse atualizarMeusDados(
            @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO_ID) Long usuarioLogadoId,
            @RequestBody @Valid UsuarioUpdateRequest request) {
        return usuarioService.atualizar(usuarioLogadoId, request);
    }

    // O próprio usuário troca a sua senha (precisa informar a atual).
    @PatchMapping("/me/senha")
    public ResponseEntity<Void> alterarMinhaSenha(
            @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO_ID) Long usuarioLogadoId,
            @RequestBody @Valid AlterarSenhaRequest request) {
        usuarioService.alterarSenha(usuarioLogadoId, request);
        return ResponseEntity.noContent().build();
    }

    // O admin edita os dados de qualquer usuário.
    @PermitirRoles(Role.ADMIN)
    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @RequestBody @Valid UsuarioUpdateRequest request) {
        return usuarioService.atualizar(id, request);
    }

    @PermitirRoles(Role.ADMIN)
    @PatchMapping("/{id}/role")
    public UsuarioResponse alterarRole(@PathVariable Long id, @RequestBody @Valid AlterarRoleRequest request) {
        return usuarioService.alterarRole(id, request.role());
    }

    @PermitirRoles(Role.ADMIN)
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        usuarioService.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @PermitirRoles(Role.ADMIN)
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(@PathVariable Long id) {
        usuarioService.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
