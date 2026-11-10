package com.luizcode.banco.dto;

import com.luizcode.banco.model.enums.TipoOperacao;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferenciaRequest(
        @NotBlank @Pattern(regexp = "[0-9]{1,30}") String contaOrigem,
        @NotBlank @Pattern(regexp = "[0-9]{1,30}") String contaDestino,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valor,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._:-]+") String chaveIdempotencia,
        @NotNull TipoOperacao tipoOperacao) {
}
