package com.luizcode.banco.security;

import com.luizcode.banco.dto.LoginResponse;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtProperties properties = new JwtProperties(SECRET, 3600000, "banco-portfolio", "banco-api");
    private final JwtService jwt = new JwtService(properties, CLOCK);
    private final UUID clienteId = UUID.randomUUID();

    @Test
    void emiteTokenDoClienteComValidadeERepresentacaoProtegida() {
        LoginResponse resposta = jwt.emitir(clienteId);
        assertThat(jwt.validar(resposta.token())).isEqualTo(clienteId);
        assertThat(resposta.tipo()).isEqualTo("Bearer");
        assertThat(resposta.expiraEm()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(resposta.toString()).doesNotContain(resposta.token());
        assertThat(properties.toString()).doesNotContain(SECRET);
        assertThat(jwt.emitir(clienteId).token()).isNotEqualTo(resposta.token());
    }

    @Test
    void rejeitaTokenExatamenteNoInstanteDaExpiracaoSemEsperarRelogioReal() {
        String token = jwt.emitir(clienteId).token();
        JwtService futuro = new JwtService(properties, Clock.offset(CLOCK, Duration.ofHours(1)));
        assertThatThrownBy(() -> futuro.validar(token)).isInstanceOf(BadCredentialsException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem-sub", "sub-invalido", "sem-exp", "sem-iat", "iat-futuro",
            "emissor-incorreto", "destinatario-incorreto", "sem-assinatura"})
    void rejeitaClaimsAusentesInvalidasOuTokenSemAssinatura(String caso) {
        JwtBuilder builder = Jwts.builder().setSubject(clienteId.toString())
                .setIssuer("banco-portfolio").setAudience("banco-api")
                .setIssuedAt(Date.from(NOW)).setExpiration(Date.from(NOW.plusSeconds(3600)));
        switch (caso) {
            case "sem-sub" -> builder.setSubject(null);
            case "sub-invalido" -> builder.setSubject("cliente-sem-uuid");
            case "sem-exp" -> builder.setExpiration(null);
            case "sem-iat" -> builder.setIssuedAt(null);
            case "iat-futuro" -> builder.setIssuedAt(Date.from(NOW.plusSeconds(60)));
            case "emissor-incorreto" -> builder.setIssuer("outro-emissor");
            case "destinatario-incorreto" -> builder.setAudience("outra-api");
            default -> { }
        }
        String token = caso.equals("sem-assinatura") ? builder.compact()
                : builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
        assertThatThrownBy(() -> jwt.validar(token)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejeitaOutroAlgoritmoMesmoComChaveEClaimsCorretos() {
        String secret64 = Base64.getEncoder().encodeToString(new byte[64]);
        JwtService service = new JwtService(new JwtProperties(secret64, 3600000, "banco-portfolio", "banco-api"), CLOCK);
        String token = Jwts.builder().setSubject(clienteId.toString())
                .setIssuer("banco-portfolio").setAudience("banco-api")
                .setIssuedAt(Date.from(NOW)).setExpiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(new byte[64]), SignatureAlgorithm.HS512).compact();
        assertThatThrownBy(() -> service.validar(token)).isInstanceOf(BadCredentialsException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"chave-curta", "%%%"})
    void rejeitaConfiguracaoSemChaveAdequada(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, 3600000, "banco-portfolio", "banco-api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 999, 86400001})
    void rejeitaPrazoDeExpiracaoInvalido(long prazo) {
        assertThatThrownBy(() -> new JwtProperties(SECRET, prazo, "banco-portfolio", "banco-api"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
