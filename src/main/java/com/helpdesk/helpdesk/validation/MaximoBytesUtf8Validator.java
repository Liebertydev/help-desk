package com.helpdesk.helpdesk.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Aplicado pelo @Valid antes de a senha chegar ao BCrypt. */
public class MaximoBytesUtf8Validator implements ConstraintValidator<MaximoBytesUtf8, String> {

    private int maximoBytes;

    @Override
    public void initialize(MaximoBytesUtf8 anotacao) {
        maximoBytes = anotacao.value();
    }

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        // Obrigatoriedade fica com @NotBlank. A senha é medida inteira, sem trim ou truncamento.
        return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= maximoBytes;
    }
}
