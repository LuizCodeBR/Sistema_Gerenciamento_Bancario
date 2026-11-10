package com.luizcode.banco.security;

import io.jsonwebtoken.io.Decoders;



public record JwtProperties(String secret, long expirationMs, String issuer, String audience) {
    public JwtProperties {
        try {
            if (secret == null || secret.isBlank() || Decoders.BASE64.decode(secret).length < 32) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Configure JWT_SECRET com uma chave Base64 de pelo menos 32 bytes.");
        }
        if (expirationMs < 1000 || expirationMs > 86_400_000) {
            throw new IllegalArgumentException("JWT_EXPIRATION_MS deve ficar entre 1000 e 86400000.");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("Configure o emissor e o destinatário do JWT.");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[chave protegida]";
    }
}
