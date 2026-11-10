package com.helpdesk.helpdesk.models;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

@Entity
@Table (name ="solicitacao_tecnico")
public class SolicitacaoTecnico {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    // várias solicitações podem pertencer a um mesmo usuário, mas cada solicitação pertence a um usuário obrigatório.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn (name = "usuario_id", nullable = false) // nullable = false significa que a coluna não pode ser nula, ou seja, cada solicitação deve estar associada a um usuário.
    private Usuario usuario;

    @Enumerated (EnumType.STRING)
    @Column(nullable = false)
    private StatusSolicitacao status = StatusSolicitacao.PENDENTE;

    // Preenchidos quando um admin aprova ou rejeita. Ficam nulos enquanto PENDENTE.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avaliado_por_id", nullable = true)
    private Usuario avaliadoPor;

    private LocalDateTime avaliadoEm;

    // Motivo da rejeição (obrigatório) ou observação da aprovação (opcional).
    @Column(length = 500)
    private String observacao;

    // Preenchido automaticamente pelo Hibernate ao inserir; nunca é alterado depois.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Atualizado automaticamente pelo Hibernate a cada alteração do registro.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SolicitacaoTecnico() {
    }

    public SolicitacaoTecnico(Usuario usuario) {
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public StatusSolicitacao getStatus() {
        return status;
    }

    public void setStatus(StatusSolicitacao status) {
        this.status = status;
    }

    public Usuario getAvaliadoPor() {
        return avaliadoPor;
    }

    public LocalDateTime getAvaliadoEm() {
        return avaliadoEm;
    }

    public String getObservacao() {
        return observacao;
    }

    /** Registra a avaliação de uma vez: novo status, quem avaliou, quando e por quê. */
    public void avaliar(StatusSolicitacao novoStatus, Usuario admin, String observacao) {
        this.status = novoStatus;
        this.avaliadoPor = admin;
        this.avaliadoEm = LocalDateTime.now();
        this.observacao = observacao;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
