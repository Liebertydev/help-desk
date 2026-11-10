package com.helpdesk.helpdesk.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.SolicitacaoTecnicoRequest;
import com.helpdesk.helpdesk.dto.SolicitacaoTecnicoResponse;
import com.helpdesk.helpdesk.models.StatusSolicitacao;
import com.helpdesk.helpdesk.service.SolicitacaoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/solicitacoes")
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    public SolicitacaoController(SolicitacaoService solicitacaoService) {
        this.solicitacaoService = solicitacaoService;
    }

    // GET /api/solicitacoes?status=PENDENTE → fila do admin
    @GetMapping
    public List<SolicitacaoTecnicoResponse> listar(@RequestParam(required = false) StatusSolicitacao status) {
        return solicitacaoService.listar(status);
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<SolicitacaoTecnicoResponse> listarPorUsuario(@PathVariable Long usuarioId) {
        return solicitacaoService.listarPorUsuario(usuarioId);
    }

    @GetMapping("/{id}")
    public SolicitacaoTecnicoResponse buscarPorId(@PathVariable Long id) {
        return solicitacaoService.buscarPorId(id);
    }

    // Body: { "usuarioId": 6 }
    @PostMapping
    public ResponseEntity<SolicitacaoTecnicoResponse> solicitar(@RequestBody @Valid SolicitacaoTecnicoRequest request) {
        SolicitacaoTecnicoResponse criada = solicitacaoService.solicitar(request);
        return ResponseEntity.created(URI.create("/api/solicitacoes/" + criada.id())).body(criada);
    }

    // Ação de ADMIN.
    @PatchMapping("/{id}/aprovar")
    public SolicitacaoTecnicoResponse aprovar(@PathVariable Long id) {
        return solicitacaoService.aprovar(id);
    }

    // Ação de ADMIN.
    @PatchMapping("/{id}/rejeitar")
    public SolicitacaoTecnicoResponse rejeitar(@PathVariable Long id) {
        return solicitacaoService.rejeitar(id);
    }
}
