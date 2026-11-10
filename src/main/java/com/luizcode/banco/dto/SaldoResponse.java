package com.luizcode.banco.dto;

import com.luizcode.banco.model.enums.StatusConta;
import com.luizcode.banco.model.enums.TipoConta;

import java.math.BigDecimal;

public record SaldoResponse(String agencia, String numero, BigDecimal saldo,
                            TipoConta tipo, StatusConta status) {
}
