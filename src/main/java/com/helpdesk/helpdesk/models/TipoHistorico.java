package com.helpdesk.helpdesk.models;

public enum TipoHistorico {
    ABERTURA,    // chamado criado
    COMENTARIO,  // mensagem escrita pelo usuário ou pelo técnico
    STATUS,      // mudança de status (registrada automaticamente)
    TECNICO      // técnico atribuído ou trocado (registrado automaticamente)
}
