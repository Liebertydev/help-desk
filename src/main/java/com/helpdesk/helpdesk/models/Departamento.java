package com.helpdesk.helpdesk.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table (name = "departamento")
public class Departamento {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    // Obrigatório e único: não podem existir dois departamentos com o mesmo nome.
    @Column(nullable = false, unique = true)
    private String nome;

    // boolean (primitivo) em vez de Boolean: nunca fica nulo, igual ao Usuario.
    @Column(nullable = false)
    private boolean ativo = true;

    public Departamento() {
    }

    public Departamento(String nome) {
        this.nome = nome;
        this.ativo = true;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
