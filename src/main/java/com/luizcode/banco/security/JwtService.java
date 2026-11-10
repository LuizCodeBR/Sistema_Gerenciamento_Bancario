package com.luizcode.banco.security;

import com.luizcode.banco.dto.LoginResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey key;
    private final JwtParser parser;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.parser = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .setClock(() -> Date.from(clock.instant()))
                .build();
    }

    public LoginResponse emitir(UUID clienteId) {
        Instant agora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEm = agora.plusMillis(properties.expirationMs()).truncatedTo(ChronoUnit.SECONDS);
        String token = Jwts.builder()
                .setSubject(clienteId.toString())
                .setIssuer(properties.issuer())
                .setAudience(properties.audience())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(agora))
                .setExpiration(Date.from(expiraEm))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        return new LoginResponse(token, "Bearer", expiraEm);
    }

    public UUID validar(String token) {
        try {
            Jws<Claims> jwt = parser.parseClaimsJws(token);
            Claims claims = jwt.getBody();
            Instant agora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            if (!"HS256".equals(jwt.getHeader().getAlgorithm())
                    || claims.getSubject() == null || claims.getIssuedAt() == null || claims.getExpiration() == null
                    || claims.getIssuedAt().toInstant().isAfter(agora)
                    || !claims.getExpiration().after(claims.getIssuedAt())
                    || !claims.getExpiration().toInstant().isAfter(agora)) {
                throw new BadCredentialsException("Token inválido ou expirado.");
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Token inválido ou expirado.");
        }
    }
}
