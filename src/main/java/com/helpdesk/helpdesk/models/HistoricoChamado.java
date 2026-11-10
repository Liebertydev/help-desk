package com.helpdesk.helpdesk.models;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Uma linha da "linha do tempo" do chamado: comentários e eventos automáticos
 * (abertura, mudança de status, troca de técnico). Nunca é editada nem apagada.
 */
@Entity
@Table(name = "historico_chamado")
public class HistoricoChamado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chamado_id", nullable = false)
    private Chamado chamado;

    // Quem escreveu o comentário ou fez a ação.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoHistorico tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public HistoricoChamado() {
    }

    public HistoricoChamado(Chamado chamado, Usuario autor, TipoHistorico tipo, String mensagem) {
        this.chamado = chamado;
        this.autor = autor;
        this.tipo = tipo;
        this.mensagem = mensagem;
    }

    // Só getters: um registro de histórico não muda depois de criado.

    public Long getId() {
        return id;
    }

    public Chamado getChamado() {
        return chamado;
    }

    public Usuario getAutor() {
        return autor;
    }

    public TipoHistorico getTipo() {
        return tipo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
