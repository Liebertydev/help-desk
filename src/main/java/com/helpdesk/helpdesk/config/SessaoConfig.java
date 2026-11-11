package com.helpdesk.helpdesk.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita a limpeza periódica das sessões e fornece o relógio usado na expiração. */
@Configuration
@EnableScheduling
public class SessaoConfig {

    @Bean
    public Clock relogioDasSessoes() {
        // Instant representa um instante absoluto: a validade não depende do fuso do servidor.
        // Injetar o relógio também permite testar a expiração sem esperar horas de verdade.
        return Clock.systemUTC();
    }
}
