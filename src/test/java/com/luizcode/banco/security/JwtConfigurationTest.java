package com.luizcode.banco.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class JwtConfigurationTest {
    @Test
    void configuracaoPadraoNaoCriaChaveAutomaticamente() {
        assertThatThrownBy(() -> new JwtConfiguration().jwtProperties(new MockEnvironment()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JWT_SECRET");
    }
    @Test
    void demonstracaoGeraChavesDiferentesEmMemoria() {
        MockEnvironment env = new MockEnvironment(); env.setActiveProfiles("demo");
        assertThat(new JwtConfiguration().jwtProperties(env).secret())
                .isNotEqualTo(new JwtConfiguration().jwtProperties(env).secret());
    }
    @Test
    void demonstracaoRespeitaChaveConfiguradaExplicitamente() {
        String secret = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        MockEnvironment env = new MockEnvironment().withProperty("app.jwt.secret", secret);
        env.setActiveProfiles("demo");
        assertThat(new JwtConfiguration().jwtProperties(env).secret()).isEqualTo(secret);
    }
}
