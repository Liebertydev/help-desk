package com.helpdesk.helpdesk.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.helpdesk.helpdesk.models.Role;

/**
 * Restringe um endpoint a certos roles. Exemplo:
 *
 *   @PermitirRoles(Role.ADMIN)                     → só admin
 *   @PermitirRoles({Role.TECHNICIAN, Role.ADMIN})  → técnico ou admin
 *
 * Sem esta anotação, qualquer usuário logado pode acessar.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PermitirRoles {

    Role[] value();
}
