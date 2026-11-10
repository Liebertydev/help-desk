package com.helpdesk.helpdesk.exception;

/** Sem login, token inválido ou credenciais erradas → HTTP 401. */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
