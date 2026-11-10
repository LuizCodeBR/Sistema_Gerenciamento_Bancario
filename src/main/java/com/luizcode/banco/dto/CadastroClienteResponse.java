package com.luizcode.banco.dto;

import com.luizcode.banco.model.enums.StatusConta;
import com.luizcode.banco.model.enums.TipoConta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CadastroClienteResponse(
        UUID id,
        String nomeCompleto,
        String email,
        LocalDateTime dataCriacao,
        ContaResponse conta) {

    public record ContaResponse(
            UUID id,
            String agencia,
            String numero,
            BigDecimal saldo,
            TipoConta tipo,
            StatusConta status) {
    }
}
