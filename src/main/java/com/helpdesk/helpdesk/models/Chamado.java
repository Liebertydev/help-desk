package com.helpdesk.helpdesk.models;

import java.time.LocalDate;
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
@Table(name = "chamado")
public class Chamado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    // TEXT não tem o limite de 255 caracteres do varchar padrão.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    // Sem categoria informada, o chamado fica como OUTROS.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categoria categoria = Categoria.OUTROS;

    // String em vez de int: aceita zeros à esquerda e letras (ex.: "000123", "PAT-45")
    // e pode ficar nulo quando o chamado não envolve um equipamento.
    private String numeroPatrimonio;

    private String equipamento;

    // LAZY = carrega o usuário só quando alguém realmente usar esse campo.
    // Assim, a listagem de chamados não busca usuário e técnico desnecessariamente.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // O mesmo vale para o técnico: ele só aparece quando for necessário.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id", nullable = true)
    private Usuario tecnico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departamento_id", nullable = false)
    private Departamento departamento;

    // Endereço físico do problema: local (bloco/prédio) → sala → detalheLocal.
    // Não confundir com o departamento, que é a área responsável pelo chamado.
    private String local;

    private String sala;

    private String detalheLocal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusChamado status = StatusChamado.ABERTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false)
    private Prioridade prioridade = Prioridade.MEDIA;

    // Data opcional para indicar quando o chamado deveria ser resolvido.
    // Pode ficar nula quando ainda não houver previsão definida.
    @Column(name = "data_previsao_resolucao", nullable = true)
    private LocalDate dataPrevisaoResolucao;

    // Preenchido automaticamente pelo Hibernate ao inserir; nunca é alterado depois.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Atualizado automaticamente pelo Hibernate a cada alteração do registro.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Chamado() {
    }

    public Chamado(String titulo, String descricao, Categoria categoria, String numeroPatrimonio,
            String equipamento, Usuario usuario, Usuario tecnico, Departamento departamento) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.categoria = categoria == null ? Categoria.OUTROS : categoria;
        this.numeroPatrimonio = numeroPatrimonio;
        this.equipamento = equipamento;
        this.usuario = usuario;
        this.tecnico = tecnico;
        this.departamento = departamento;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public String getNumeroPatrimonio() {
        return numeroPatrimonio;
    }

    public void setNumeroPatrimonio(String numeroPatrimonio) {
        this.numeroPatrimonio = numeroPatrimonio;
    }

    public String getEquipamento() {
        return equipamento;
    }

    public void setEquipamento(String equipamento) {
        this.equipamento = equipamento;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Usuario getTecnico() {
        return tecnico;
    }

    public void setTecnico(Usuario tecnico) {
        this.tecnico = tecnico;
    }

    public Departamento getDepartamento() {
        return departamento;
    }

    public void setDepartamento(Departamento departamento) {
        this.departamento = departamento;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public String getSala() {
        return sala;
    }

    public void setSala(String sala) {
        this.sala = sala;
    }

    public String getDetalheLocal() {
        return detalheLocal;
    }

    public void setDetalheLocal(String detalheLocal) {
        this.detalheLocal = detalheLocal;
    }

    public StatusChamado getStatus() {
        return status;
    }

    public void setStatus(StatusChamado status) {
        this.status = status;
    }

    public Prioridade getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(Prioridade prioridade) {
        this.prioridade = prioridade;
    }

    public LocalDate getDataPrevisaoResolucao() {
        return dataPrevisaoResolucao;
    }

    public void setDataPrevisaoResolucao(LocalDate dataPrevisaoResolucao) {
        this.dataPrevisaoResolucao = dataPrevisaoResolucao;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
