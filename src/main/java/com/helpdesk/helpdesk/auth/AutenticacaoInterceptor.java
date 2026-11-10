package com.helpdesk.helpdesk.auth;

import java.util.Arrays;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.helpdesk.helpdesk.exception.AcessoNegadoException;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Roda ANTES de cada endpoint de /api e decide se a requisição pode seguir:
 *
 *   1. Endpoint com @Publico          → segue sem login.
 *   2. Sem token ou token inválido    → 401 Unauthorized.
 *   3. Role fora do @PermitirRoles    → 403 Forbidden.
 *   4. Tudo certo → guarda o id do usuário logado na requisição, e o controller
 *      o recebe com @RequestAttribute(USUARIO_LOGADO_ID) Long usuarioLogadoId.
 */
@Component
public class AutenticacaoInterceptor implements HandlerInterceptor {

    public static final String USUARIO_LOGADO_ID = "usuarioLogadoId";

    private static final String PREFIXO_BEARER = "Bearer ";

    private final AuthService authService;

    public AutenticacaoInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Requisições que não vão para um método de controller (ex.: preflight de CORS) passam direto.
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }
        if (metodo.hasMethodAnnotation(Publico.class)) {
            return true;
        }

        Usuario usuario = authService.usuarioDoToken(extrairToken(request));

        PermitirRoles permitidos = metodo.getMethodAnnotation(PermitirRoles.class);
        if (permitidos != null && !Arrays.asList(permitidos.value()).contains(usuario.getRole())) {
            throw new AcessoNegadoException("Você não tem permissão para esta ação.");
        }

        request.setAttribute(USUARIO_LOGADO_ID, usuario.getId());
        return true;
    }

    // Cabeçalho esperado: Authorization: Bearer <token>
    private String extrairToken(HttpServletRequest request) {
        String cabecalho = request.getHeader("Authorization");
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO_BEARER)) {
            return null;
        }
        return cabecalho.substring(PREFIXO_BEARER.length()).trim();
    }
}
