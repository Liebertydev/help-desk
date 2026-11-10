package com.helpdesk.helpdesk.models;

/**
 * Tipo de problema do chamado. Valores fixos evitam variações
 * como "Rede", "rede" e "REDE" e permitem filtrar e contar por categoria.
 */
public enum Categoria {
    HARDWARE,
    SOFTWARE,
    REDE,
    IMPRESSORA,
    ACESSO,   // senha, login, permissões de sistemas
    OUTROS
}
