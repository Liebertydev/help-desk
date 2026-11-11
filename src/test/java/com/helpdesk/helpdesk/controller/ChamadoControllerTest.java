package com.helpdesk.helpdesk.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.helpdesk.helpdesk.auth.AutenticacaoInterceptor;
import com.helpdesk.helpdesk.exception.GlobalExceptionHandler;
import com.helpdesk.helpdesk.exception.RegraNegocioException;
import com.helpdesk.helpdesk.service.ChamadoService;

@ExtendWith(MockitoExtension.class)
class ChamadoControllerTest {

    @Mock
    private ChamadoService chamadoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Testa o contrato HTTP do controller e do tratamento de erros.
        // A autenticação já foi resolvida: fornecemos o id que o interceptor colocaria.
        mockMvc = MockMvcBuilders.standaloneSetup(new ChamadoController(chamadoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void assumirChamadoJaAtribuido_deveRetornar409ComOrientacaoParaAtualizarAFila() throws Exception {
        when(chamadoService.atribuirTecnico(1L, 20L, 20L)).thenThrow(new RegraNegocioException(
                "Este chamado já tem técnico. Atualize a fila. Para transferir, peça a um administrador."));

        mockMvc.perform(patch("/api/chamados/1/tecnico")
                        .requestAttr(AutenticacaoInterceptor.USUARIO_LOGADO_ID, 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tecnicoId\":20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value(
                        "Este chamado já tem técnico. Atualize a fila. Para transferir, peça a um administrador."));
    }

    @Test
    void falhaAoObterBloqueio_deveRetornar409SemExporDetalhesDoBanco() throws Exception {
        when(chamadoService.atribuirTecnico(1L, 20L, 20L))
                .thenThrow(new CannotAcquireLockException("Detalhe interno do PostgreSQL"));

        mockMvc.perform(patch("/api/chamados/1/tecnico")
                        .requestAttr(AutenticacaoInterceptor.USUARIO_LOGADO_ID, 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tecnicoId\":20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value(
                        "O registro está sendo alterado por outra operação. Atualize os dados e tente novamente."));
    }
}
