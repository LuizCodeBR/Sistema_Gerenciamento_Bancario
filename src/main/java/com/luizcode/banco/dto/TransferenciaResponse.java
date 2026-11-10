package com.luizcode.banco.dto;

import com.luizcode.banco.model.enums.TipoOperacao;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferenciaResponse(UUID id, String contaOrigem, String contaDestino,
                                    BigDecimal valor, TipoOperacao tipoOperacao,
                                    LocalDateTime dataHora, String chaveIdempotencia) {
}
