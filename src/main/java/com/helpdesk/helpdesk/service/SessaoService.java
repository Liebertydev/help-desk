package com.helpdesk.helpdesk.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Guarda as sessões ativas em memória: token → id do usuário.
 *
 * Simples de propósito (projeto acadêmico). Limitações conhecidas:
 * - ao reiniciar a aplicação, todos precisam logar de novo;
 * - não funciona com mais de uma instância do servidor.
 * Numa aplicação real, o caminho seria JWT ou sessões no banco/Redis.
 */
@Service
public class SessaoService {

    // ConcurrentHashMap: seguro com várias requisições ao mesmo tempo.
    private final Map<String, Long> sessoes = new ConcurrentHashMap<>();

    public String criar(Long usuarioId) {
        // UUID aleatório: impossível de adivinhar, ao contrário de um id sequencial.
        String token = UUID.randomUUID().toString();
        sessoes.put(token, usuarioId);
        return token;
    }

    public Optional<Long> buscarUsuarioId(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(sessoes.get(token));
    }

    public void encerrar(String token) {
        if (token != null) {
            sessoes.remove(token);
        }
    }
}
