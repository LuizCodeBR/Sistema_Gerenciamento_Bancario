package com.luizcode.banco.dto;

import java.time.Instant;
import java.util.Map;

public record ErroApi(Instant instante, int status, String mensagem, Map<String, String> campos) {
    public ErroApi {
        campos = Map.copyOf(campos);
    }
}
