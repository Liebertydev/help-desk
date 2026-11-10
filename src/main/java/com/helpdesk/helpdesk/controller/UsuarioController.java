package com.helpdesk.helpdesk.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.AlterarRoleRequest;
import com.helpdesk.helpdesk.dto.AlterarSenhaRequest;
import com.helpdesk.helpdesk.dto.UsuarioRequest;
import com.helpdesk.helpdesk.dto.UsuarioResponse;
import com.helpdesk.helpdesk.dto.UsuarioUpdateRequest;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // GET /api/usuarios            → todos
    // GET /api/usuarios?role=TECHNICIAN → só técnicos
    @GetMapping
    public List<UsuarioResponse> listar(@RequestParam(required = false) Role role) {
        return usuarioService.listar(role);
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(@PathVariable Long id) {
        return usuarioService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@RequestBody @Valid UsuarioRequest request) {
        UsuarioResponse criado = usuarioService.criar(request);
        return ResponseEntity.created(URI.create("/api/usuarios/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @RequestBody @Valid UsuarioUpdateRequest request) {
        return usuarioService.atualizar(id, request);
    }

    @PatchMapping("/{id}/senha")
    public ResponseEntity<Void> alterarSenha(@PathVariable Long id, @RequestBody @Valid AlterarSenhaRequest request) {
        usuarioService.alterarSenha(id, request);
        return ResponseEntity.noContent().build();
    }

    // Ação de ADMIN (a restrição entra junto com o login).
    @PatchMapping("/{id}/role")
    public UsuarioResponse alterarRole(@PathVariable Long id, @RequestBody @Valid AlterarRoleRequest request) {
        return usuarioService.alterarRole(id, request.role());
    }

    // Ação de ADMIN.
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        usuarioService.desativar(id);
        return ResponseEntity.noContent().build();
    }

    // Ação de ADMIN.
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(@PathVariable Long id) {
        usuarioService.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
