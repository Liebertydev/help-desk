package com.helpdesk.helpdesk.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.auth.AutenticacaoInterceptor;
import com.helpdesk.helpdesk.auth.PermitirRoles;
import com.helpdesk.helpdesk.dto.SolicitacaoTecnicoResponse;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.StatusSolicitacao;
import com.helpdesk.helpdesk.service.SolicitacaoService;

@RestController
@RequestMapping("/api/solicitacoes")
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    public SolicitacaoController(SolicitacaoService solicitacaoService) {
        this.solicitacaoService = solicitacaoService;
    }

    // Fila do admin. GET /api/solicitacoes?status=PENDENTE
    @PermitirRoles(Role.ADMIN)
    @GetMapping
    public List<SolicitacaoTecnicoResponse> listar(@RequestParam(required = false) StatusSolicitacao status) {
        return solicitacaoService.listar(status);
    }

    // As solicitações do usuário logado.
    @GetMapping("/minhas")
    public List<SolicitacaoTecnicoResponse> listarMinhas(
            @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO_ID) Long usuarioLogadoId) {
        return solicitacaoService.listarPorUsuario(usuarioLogadoId);
    }

    @PermitirRoles(Role.ADMIN)
    @GetMapping("/usuario/{usuarioId}")
    public List<SolicitacaoTecnicoResponse> listarPorUsuario(@PathVariable Long usuarioId) {
        return solicitacaoService.listarPorUsuario(usuarioId);
    }

    @PermitirRoles(Role.ADMIN)
    @GetMapping("/{id}")
    public SolicitacaoTecnicoResponse buscarPorId(@PathVariable Long id) {
        return solicitacaoService.buscarPorId(id);
    }

    // O usuário logado pede para virar técnico (sem corpo na requisição).
    @PostMapping
    public ResponseEntity<SolicitacaoTecnicoResponse> solicitar(
            @RequestAttribute(AutenticacaoInterceptor.USUARIO_LOGADO_ID) Long usuarioLogadoId) {
        SolicitacaoTecnicoResponse criada = solicitacaoService.solicitar(usuarioLogadoId);
        return ResponseEntity.created(URI.create("/api/solicitacoes/" + criada.id())).body(criada);
    }

    @PermitirRoles(Role.ADMIN)
    @PatchMapping("/{id}/aprovar")
    public SolicitacaoTecnicoResponse aprovar(@PathVariable Long id) {
        return solicitacaoService.aprovar(id);
    }

    @PermitirRoles(Role.ADMIN)
    @PatchMapping("/{id}/rejeitar")
    public SolicitacaoTecnicoResponse rejeitar(@PathVariable Long id) {
        return solicitacaoService.rejeitar(id);
    }
}
