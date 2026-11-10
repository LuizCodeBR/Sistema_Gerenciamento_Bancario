package com.luizcode.banco.controller;

import com.luizcode.banco.dto.SaldoResponse;
import com.luizcode.banco.security.ClienteAutenticado;
import com.luizcode.banco.service.ContaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contas")
public class ContaController {
    private final ContaService contas;

    public ContaController(ContaService contas) {
        this.contas = contas;
    }

    @GetMapping
    public java.util.List<SaldoResponse> listar(@AuthenticationPrincipal ClienteAutenticado titular) {
        return contas.listar(titular.id());
    }

    @GetMapping("/{numero}/saldo")
    public SaldoResponse saldo(@PathVariable String numero,
                               @AuthenticationPrincipal ClienteAutenticado titular) {
        return contas.consultarSaldo(numero, titular.id());
    }
}
