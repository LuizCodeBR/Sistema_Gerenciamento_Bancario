package com.luizcode.banco.controller;

import com.luizcode.banco.dto.TransferenciaRequest;
import com.luizcode.banco.dto.TransferenciaResponse;
import com.luizcode.banco.security.ClienteAutenticado;
import com.luizcode.banco.service.TransferenciaService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transferencia")
public class TransferenciaController {
    private final TransferenciaService transferencias;
    public TransferenciaController(TransferenciaService transferencias) { this.transferencias = transferencias; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TransferenciaResponse transferir(@Valid @RequestBody TransferenciaRequest request,
                                            @AuthenticationPrincipal ClienteAutenticado titular) {
        return transferencias.transferir(request, titular.id());
    }
}
