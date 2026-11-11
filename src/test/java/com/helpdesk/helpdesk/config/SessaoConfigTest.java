package com.helpdesk.helpdesk.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.helpdesk.helpdesk.service.SessaoService;

/** Carrega somente os componentes de sessão: não inicia JPA, banco ou dados de teste. */
@SpringBootTest(classes = { SessaoConfig.class, SessaoService.class }, properties = {
        "app.sessao.duracao=PT30M",
        "app.sessao.intervalo-limpeza-ms=60000"
})
class SessaoConfigTest {

    @Autowired
    private SessaoService sessaoService;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor agendador;

    @Test
    void deveConverterDuracaoConfiguradaERegistrarLimpezaPeriodica() {
        // Criar o contexto já exige converter PT30M para Duration e injetar o Clock.
        String token = sessaoService.criar(10L);

        assertThat(sessaoService.buscarUsuarioId(token)).contains(10L);
        assertThat(agendador.getScheduledTasks()).hasSize(1);
    }
}
