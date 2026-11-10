package com.luizcode.banco.dto;

import java.time.Instant;

public record LoginResponse(String token, String tipo, Instant expiraEm) {
    @Override
    public String toString() {
        return "LoginResponse[token protegido, tipo=" + tipo + ", expiraEm=" + expiraEm + "]";
    }
}
