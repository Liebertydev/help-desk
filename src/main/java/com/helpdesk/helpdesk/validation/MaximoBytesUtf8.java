package com.helpdesk.helpdesk.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Valida o tamanho codificado em UTF-8, que pode ser maior que a quantidade de caracteres. */
@Documented
@Constraint(validatedBy = MaximoBytesUtf8Validator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaximoBytesUtf8 {

    int value();

    String message() default "O campo deve ter no máximo {value} bytes em UTF-8.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
