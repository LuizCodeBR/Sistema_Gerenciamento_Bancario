package com.luizcode.banco.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration
public class JwtConfiguration {
    @Bean
    JwtProperties jwtProperties(Environment environment) {
        String secret = environment.getProperty("app.jwt.secret", "");
        if (secret.isBlank() && environment.acceptsProfiles(Profiles.of("demo"))) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            secret = Base64.getEncoder().encodeToString(bytes);
        }
        return new JwtProperties(secret,
                environment.getProperty("app.jwt.expiration-ms", Long.class, 3600000L),
                environment.getProperty("app.jwt.issuer", "banco-portfolio"),
                environment.getProperty("app.jwt.audience", "banco-api"));
    }
}
