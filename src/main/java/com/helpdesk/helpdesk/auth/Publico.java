package com.helpdesk.helpdesk.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um endpoint que pode ser acessado SEM login
 * (ex.: o próprio login, o cadastro de usuário).
 * Todos os outros endpoints de /api exigem o token.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Publico {
}
