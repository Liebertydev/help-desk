package com.helpdesk.helpdesk.exception;

/** Usuário logado, mas sem permissão para a ação → HTTP 403. */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
