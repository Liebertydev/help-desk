package com.helpdesk.helpdesk.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.helpdesk.helpdesk.auth.AutenticacaoInterceptor;
import com.helpdesk.helpdesk.exception.GlobalExceptionHandler;
import com.helpdesk.helpdesk.service.UsuarioService;

@ExtendWith(MockitoExtension.class)
class SenhaControllerTest {

    @Mock
    private UsuarioService usuarioService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UsuarioController(usuarioService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void cadastroAcimaDe72BytesDeveRetornar400AntesDoService() throws Exception {
        String corpo = "{\"name\":\"Ana\",\"email\":\"ana@teste.com\",\"password\":\""
                + "é".repeat(37) + "\",\"departamentoId\":1}";
        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.password").value("A senha deve ter no máximo 72 bytes em UTF-8."));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void trocaAcimaDe72BytesDeveRetornar400AntesDeAlterarSenhaOuSessoes() throws Exception {
        String corpo = "{\"senhaAtual\":\"123456\",\"novaSenha\":\"" + "😀".repeat(19) + "\"}";
        mockMvc.perform(patch("/api/usuarios/me/senha")
                        .requestAttr(AutenticacaoInterceptor.USUARIO_LOGADO_ID, 10L)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.novaSenha").value("A nova senha deve ter no máximo 72 bytes em UTF-8."));
        verifyNoInteractions(usuarioService);
    }
}
