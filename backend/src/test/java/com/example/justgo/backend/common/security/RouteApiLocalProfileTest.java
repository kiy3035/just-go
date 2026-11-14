package com.example.justgo.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RouteApiLocalProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=local")
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void localProfileAllowsRepeatedThemeChecks() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RouteApiSecurityProperties.class).getRequestsPerMinute())
                    .isEqualTo(60);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RouteApiSecurityProperties.class)
    static class PropertiesConfiguration {
    }
}
