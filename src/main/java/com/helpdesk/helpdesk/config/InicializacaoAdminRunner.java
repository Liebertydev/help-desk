package com.helpdesk.helpdesk.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.helpdesk.helpdesk.service.InicializacaoAdminService;

/** Executa a inicialização apenas quando o perfil e a autorização explícita estão presentes. */
@Component
@Profile("prod")
@ConditionalOnProperty(name = "app.bootstrap.habilitado", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InicializacaoAdminRunner implements CommandLineRunner {

    private final InicializacaoAdminService inicializacaoAdminService;
    private final Environment environment;

    public InicializacaoAdminRunner(InicializacaoAdminService inicializacaoAdminService, Environment environment) {
        this.inicializacaoAdminService = inicializacaoAdminService;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        // Recusa a combinação antes de executar o DataSeeder e suas contas de demonstração.
        if (environment.matchesProfiles("dev")) {
            throw new IllegalStateException("A inicialização de produção não pode ser executada com o perfil dev ativo.");
        }

        // Valores ausentes ficam vazios e recebem mensagens específicas, sem expor a senha.
        inicializacaoAdminService.inicializar(
                environment.getProperty("app.bootstrap.admin.nome", ""),
                environment.getProperty("app.bootstrap.admin.email", ""),
                environment.getProperty("app.bootstrap.admin.senha", ""),
                environment.getProperty("app.bootstrap.departamento", ""));
    }
}
