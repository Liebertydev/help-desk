package com.helpdesk.helpdesk.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Captura as exceções lançadas em QUALQUER controller e as transforma
 * numa resposta HTTP com o status certo e uma mensagem em JSON.
 * Assim os controllers não precisam de try/catch.
 *
 *   NaoAutenticadoException              → 401 Unauthorized
 *   AcessoNegadoException                → 403 Forbidden
 *   RecursoNaoEncontradoException        → 404 Not Found
 *   RegraNegocioException                → 409 Conflict
 *   Validação dos DTOs (@Valid)          → 400 Bad Request (com os campos)
 *   JSON inválido / tipo errado na URL   → 400 Bad Request
 *   Rota inexistente                     → 404 Not Found
 *   Método HTTP não suportado            → 405 Method Not Allowed
 *   Content-Type não suportado           → 415 Unsupported Media Type
 *   Violação de unique no banco          → 409 Conflict
 *   Qualquer outro erro                  → 500 Internal Server Error
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NaoAutenticadoException.class)
    public ResponseEntity<ErroResponse> naoAutenticado(NaoAutenticadoException ex, HttpServletRequest request) {
        return resposta(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResponse> acessoNegado(AcessoNegadoException ex, HttpServletRequest request) {
        return resposta(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException ex, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    // Disparada pelo @Valid quando o DTO tem campos inválidos.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }
        return resposta(HttpStatus.BAD_REQUEST, "Existem campos inválidos.", request, campos);
    }

    // JSON malformado ou valor inválido para enum (ex.: "status": "QUALQUER").
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> corpoInvalido(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST,
                "O corpo da requisição é inválido. Verifique o JSON e os valores enviados.", request, null);
    }

    // Tipo errado na URL (ex.: /api/chamados/abc ou ?status=QUALQUER).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST,
                "Valor inválido para o parâmetro \"" + ex.getName() + "\": " + ex.getValue(), request, null);
    }

    // Rede de segurança: se duas requisições ao mesmo tempo passarem pela checagem
    // do service, o unique do banco ainda barra o dado duplicado.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> integridade(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violação de integridade em {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return resposta(HttpStatus.CONFLICT, "A operação viola uma restrição do banco de dados.", request, null);
    }

    // Ex.: GET /api/rota-que-nao-existe
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> rotaInexistente(NoResourceFoundException ex, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, "Rota não encontrada.", request, null);
    }

    // Ex.: DELETE numa rota que só aceita GET e PUT.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> metodoNaoSuportado(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED,
                "Método " + ex.getMethod() + " não é suportado nesta rota.", request, null);
    }

    // Ex.: corpo enviado como text/plain em vez de application/json.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResponse> tipoDeConteudoNaoSuportado(HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type não suportado. Envie o corpo como application/json.", request, null);
    }

    // Erro inesperado: registra o detalhe no log, mas não expõe para o cliente.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> inesperado(Exception ex, HttpServletRequest request) {
        // Outras exceções do próprio Spring MVC já sabem o status HTTP certo
        // (ex.: 406, 413); nesses casos, usa o status delas em vez de 500.
        if (ex instanceof ErrorResponse erroDoSpring) {
            HttpStatus status = HttpStatus.valueOf(erroDoSpring.getStatusCode().value());
            return resposta(status, status.getReasonPhrase(), request, null);
        }

        log.error("Erro inesperado em {}", request.getRequestURI(), ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor.", request, null);
    }

    private ResponseEntity<ErroResponse> resposta(HttpStatus status, String mensagem,
            HttpServletRequest request, Map<String, String> campos) {
        ErroResponse corpo = new ErroResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                request.getRequestURI(),
                campos);
        return ResponseEntity.status(status).body(corpo);
    }
}
