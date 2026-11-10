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

import com.helpdesk.helpdesk.dto.AlterarStatusChamadoRequest;
import com.helpdesk.helpdesk.dto.AtribuirTecnicoRequest;
import com.helpdesk.helpdesk.dto.ChamadoRequest;
import com.helpdesk.helpdesk.dto.ChamadoResponse;
import com.helpdesk.helpdesk.dto.ChamadoUpdateRequest;
import com.helpdesk.helpdesk.models.StatusChamado;
import com.helpdesk.helpdesk.service.ChamadoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chamados")
public class ChamadoController {

    private final ChamadoService chamadoService;

    public ChamadoController(ChamadoService chamadoService) {
        this.chamadoService = chamadoService;
    }

    // GET /api/chamados                 → todos
    // GET /api/chamados?status=ABERTO   → filtrados por status
    @GetMapping
    public List<ChamadoResponse> listar(@RequestParam(required = false) StatusChamado status) {
        return chamadoService.listar(status);
    }

    // "Meus chamados": os que o usuário abriu.
    @GetMapping("/usuario/{usuarioId}")
    public List<ChamadoResponse> listarPorUsuario(@PathVariable Long usuarioId) {
        return chamadoService.listarPorUsuario(usuarioId);
    }

    // Fila de trabalho do técnico.
    @GetMapping("/tecnico/{tecnicoId}")
    public List<ChamadoResponse> listarPorTecnico(@PathVariable Long tecnicoId) {
        return chamadoService.listarPorTecnico(tecnicoId);
    }

    @GetMapping("/{id}")
    public ChamadoResponse buscarPorId(@PathVariable Long id) {
        return chamadoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ChamadoResponse> abrir(@RequestBody @Valid ChamadoRequest request) {
        ChamadoResponse criado = chamadoService.abrir(request);
        return ResponseEntity.created(URI.create("/api/chamados/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public ChamadoResponse atualizar(@PathVariable Long id, @RequestBody @Valid ChamadoUpdateRequest request) {
        return chamadoService.atualizar(id, request);
    }

    // Body: { "tecnicoId": 2 }
    @PatchMapping("/{id}/tecnico")
    public ChamadoResponse atribuirTecnico(@PathVariable Long id, @RequestBody @Valid AtribuirTecnicoRequest request) {
        return chamadoService.atribuirTecnico(id, request.tecnicoId());
    }

    // Body: { "status": "EM_ANDAMENTO" }
    @PatchMapping("/{id}/status")
    public ChamadoResponse alterarStatus(@PathVariable Long id, @RequestBody @Valid AlterarStatusChamadoRequest request) {
        return chamadoService.alterarStatus(id, request.status());
    }
}
