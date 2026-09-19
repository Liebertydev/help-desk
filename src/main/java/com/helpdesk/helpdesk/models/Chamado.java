package com.helpdesk.helpdesk.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    private String titulo;

    private String descricao;

    private String categoria;

    private int numeroPatrimonio;

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

    @ManyToOne 
    @JoinColumn (name = "departamento_id", nullable = false)
    private Departamento departamento;

    private String local;

    private String setor;

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

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Chamado() {
    }

    public Chamado(String titulo, String descricao, String categoria, int numeroPatrimonio,
            String equipamento, Usuario usuario, Usuario tecnico) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.categoria = categoria;
        this.numeroPatrimonio = numeroPatrimonio;
        this.equipamento = equipamento;
        this.usuario = usuario;
        this.tecnico = tecnico;
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

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public int getNumeroPatrimonio() {
        return numeroPatrimonio;
    }

    public void setNumeroPatrimonio(int numeroPatrimonio) {
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

    public String getSetor() {
        return setor;
    }

    public void setSetor(String setor) {
        this.setor = setor;
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
