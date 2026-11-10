package com.helpdesk.helpdesk.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Testa as anotações de validação do DTO (as mesmas que o @Valid aplica no controller),
 * sem subir o Spring.
 */
class UsuarioRequestValidacaoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void criarValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "   ", "12345678901", "123.456.789-01", " 123.456.789-01 " })
    void cpfAceito(String cpf) {
        assertThat(errosDoCpf(cpf)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "123", "1234567890", "123456789012", "123.456.789-0", "abc.def.ghi-jk", "123 456 789 01" })
    void cpfRecusado(String cpf) {
        assertThat(errosDoCpf(cpf)).isNotEmpty();
    }

    private Set<ConstraintViolation<UsuarioRequest>> errosDoCpf(String cpf) {
        UsuarioRequest request = new UsuarioRequest("Ana", "ana@email.com", "123456", cpf, null, null, 1L);
        return validator.validateProperty(request, "cpf");
    }
}
