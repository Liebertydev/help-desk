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

import com.helpdesk.helpdesk.dto.DepartamentoRequest;
import com.helpdesk.helpdesk.dto.DepartamentoResponse;
import com.helpdesk.helpdesk.service.DepartamentoService;

import jakarta.validation.Valid;

/**
 * O controller só traduz HTTP ↔ service: recebe a requisição, valida o DTO
 * com @Valid, chama o service e devolve o status HTTP certo.
 * Nenhuma regra de negócio fica aqui.
 */
@RestController
@RequestMapping("/api/departamentos")
public class DepartamentoController {

    private final DepartamentoService departamentoService;

    public DepartamentoController(DepartamentoService departamentoService) {
        this.departamentoService = departamentoService;
    }

    // GET /api/departamentos?apenasAtivos=true
    @GetMapping
    public List<DepartamentoResponse> listar(@RequestParam(defaultValue = "false") boolean apenasAtivos) {
        return departamentoService.listar(apenasAtivos);
    }

    @GetMapping("/{id}")
    public DepartamentoResponse buscarPorId(@PathVariable Long id) {
        return departamentoService.buscarPorId(id);
    }

    // 201 Created + cabeçalho Location apontando para o novo recurso.
    @PostMapping
    public ResponseEntity<DepartamentoResponse> criar(@RequestBody @Valid DepartamentoRequest request) {
        DepartamentoResponse criado = departamentoService.criar(request);
        return ResponseEntity.created(URI.create("/api/departamentos/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public DepartamentoResponse atualizar(@PathVariable Long id, @RequestBody @Valid DepartamentoRequest request) {
        return departamentoService.atualizar(id, request);
    }

    // 204 No Content: deu certo, mas não há corpo para devolver.
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        departamentoService.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(@PathVariable Long id) {
        departamentoService.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
