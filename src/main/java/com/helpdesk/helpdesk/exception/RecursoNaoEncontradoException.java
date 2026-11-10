package com.helpdesk.helpdesk.exception;

/**
 * Lançada quando um registro buscado pelo id não existe.
 * Futuramente, o controller vai transformá-la em resposta HTTP 404.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, Long id) {
        super(recurso + " com id " + id + " não encontrado.");
    }
}
