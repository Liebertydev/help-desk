package com.helpdesk.helpdesk.exception;

/**
 * Lançada quando uma operação viola uma regra do sistema
 * (ex.: nome duplicado, ação não permitida para o status atual).
 * Futuramente, o controller vai transformá-la em resposta HTTP 400 ou 409.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
