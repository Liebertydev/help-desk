package com.helpdesk.helpdesk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.helpdesk.helpdesk.service.InicializacaoAdminService;

class InicializacaoAdminRunnerTest {

    private final InicializacaoAdminService service = mock(InicializacaoAdminService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(InicializacaoAdminRunner.class)
            .withBean(InicializacaoAdminService.class, () -> service);

    @Test
    void devePermanecerDesabilitadoPorPadrao() {
        contextRunner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).doesNotHaveBean(InicializacaoAdminRunner.class);
            verifyNoInteractions(service);
        });
    }

    @Test
    void naoDeveExecutarForaDeProducao() {
        contextRunner.withPropertyValues("spring.profiles.active=dev", "app.bootstrap.habilitado=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(InicializacaoAdminRunner.class);
                    verifyNoInteractions(service);
                });
    }

    @Test
    void deveExecutarComPerfilProdEAutorizacaoExplicita() {
        contextRunner.withPropertyValues("spring.profiles.active=prod", "app.bootstrap.habilitado=true",
                "app.bootstrap.admin.nome=Admin", "app.bootstrap.admin.email=admin@empresa.com",
                "app.bootstrap.admin.senha=SenhaInicial!123", "app.bootstrap.departamento=TI")
                .run(context -> {
                    context.getBean(InicializacaoAdminRunner.class).run();
                    verify(service).inicializar("Admin", "admin@empresa.com", "SenhaInicial!123", "TI");
                });
    }

    @Test
    void deveRecusarPerfisProdEDevJuntos() {
        contextRunner.withPropertyValues("spring.profiles.active=prod,dev", "app.bootstrap.habilitado=true")
                .run(context -> {
                    assertThatThrownBy(() -> context.getBean(InicializacaoAdminRunner.class).run())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("perfil dev");
                    verifyNoInteractions(service);
                });
    }
}
