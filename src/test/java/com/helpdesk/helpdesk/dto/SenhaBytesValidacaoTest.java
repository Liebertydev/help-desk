package com.helpdesk.helpdesk.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class SenhaBytesValidacaoTest {

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
    @MethodSource("senhas")
    void cadastroDeveValidarBytesSemAlterarASenha(String senha, boolean valida) {
        UsuarioRequest request = new UsuarioRequest("Ana", "ana@teste.com", senha, null, null, null, 1L);
        assertThat(validator.validateProperty(request, "password").isEmpty()).isEqualTo(valida);
        assertThat(request.password()).isEqualTo(senha);
    }

    @ParameterizedTest
    @MethodSource("senhas")
    void trocaDeSenhaDeveAplicarOMesmoLimite(String senha, boolean valida) {
        AlterarSenhaRequest request = new AlterarSenhaRequest("senhaAtual123", senha);
        assertThat(validator.validateProperty(request, "novaSenha").isEmpty()).isEqualTo(valida);
        assertThat(request.novaSenha()).isEqualTo(senha);
    }

    static Stream<Arguments> senhas() {
        return Stream.of(
                Arguments.of("123456", true),
                Arguments.of("a".repeat(72), true),
                Arguments.of("a".repeat(73), false),
                Arguments.of("é".repeat(36), true),
                Arguments.of("é".repeat(37), false),
                Arguments.of("😀".repeat(18), true),
                Arguments.of("😀".repeat(19), false),
                Arguments.of("a".repeat(70) + "é", true),
                Arguments.of("a".repeat(71) + "é", false),
                Arguments.of("12345", false),
                Arguments.of("      ", false),
                Arguments.of(null, false));
    }
}
