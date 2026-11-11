package com.helpdesk.helpdesk.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Guarda as sessões em memória: cada token tem um usuário e um instante de expiração.
 * A validade é fixa a partir do login; usar o token não prolonga a sessão.
 *
 * Simples de propósito (projeto acadêmico). Limitações conhecidas:
 * - ao reiniciar a aplicação, todos precisam logar de novo;
 * - não funciona com mais de uma instância do servidor.
 * Para compartilhar sessões entre servidores, elas precisam de armazenamento compartilhado.
 */
@Service
public class SessaoService {

    // ConcurrentHashMap: seguro com várias requisições ao mesmo tempo.
    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();

    private final Duration duracao;
    private final Clock relogio;

    public SessaoService(@Value("${app.sessao.duracao:PT8H}") Duration duracao, Clock relogio) {
        if (duracao.isZero() || duracao.isNegative()) {
            throw new IllegalArgumentException("A duração da sessão deve ser maior que zero.");
        }
        this.duracao = duracao;
        this.relogio = relogio;
    }

    public String criar(Long usuarioId) {
        // UUID aleatório: impossível de adivinhar, ao contrário de um id sequencial.
        String token = UUID.randomUUID().toString();
        Instant expiraEm = relogio.instant().plus(duracao);
        sessoes.put(token, new Sessao(usuarioId, expiraEm));
        return token;
    }

    public Optional<Long> buscarUsuarioId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Sessao sessao = sessoes.get(token);
        if (sessao == null) {
            return Optional.empty();
        }

        // A validação acontece em toda requisição, mesmo antes da próxima limpeza periódica.
        if (estaExpirada(sessao, relogio.instant())) {
            sessoes.remove(token, sessao);
            return Optional.empty();
        }
        return Optional.of(sessao.usuarioId());
    }

    public void encerrar(String token) {
        if (token != null) {
            sessoes.remove(token);
        }
    }

    /** Encerra o acesso em todos os dispositivos, sem afetar as sessões de outras pessoas. */
    public void encerrarTodasDoUsuario(Long usuarioId) {
        for (Map.Entry<String, Sessao> entrada : sessoes.entrySet()) {
            Sessao sessao = entrada.getValue();
            if (sessao.usuarioId().equals(usuarioId)) {
                sessoes.remove(entrada.getKey(), sessao);
            }
        }
    }

    /** Remove também os tokens expirados que nunca mais foram apresentados à API. */
    @Scheduled(fixedDelayString = "${app.sessao.intervalo-limpeza-ms:60000}")
    public void removerSessoesExpiradas() {
        Instant agora = relogio.instant();
        for (Map.Entry<String, Sessao> entrada : sessoes.entrySet()) {
            Sessao sessao = entrada.getValue();
            if (estaExpirada(sessao, agora)) {
                sessoes.remove(entrada.getKey(), sessao);
            }
        }
    }

    private boolean estaExpirada(Sessao sessao, Instant agora) {
        // No instante exato do vencimento, a sessão já não é válida.
        return !agora.isBefore(sessao.expiraEm());
    }

    // Os dados de uma sessão não mudam: outro login sempre cria um novo token.
    private record Sessao(Long usuarioId, Instant expiraEm) {
    }
}
