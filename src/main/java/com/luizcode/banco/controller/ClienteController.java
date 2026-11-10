package com.luizcode.banco.controller;

import com.luizcode.banco.dto.CadastroClienteRequest;
import com.luizcode.banco.dto.CadastroClienteResponse;
import com.luizcode.banco.service.CadastroClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {
    private final CadastroClienteService cadastro;

    public ClienteController(CadastroClienteService cadastro) {
        this.cadastro = cadastro;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CadastroClienteResponse> cadastrar(
            @Valid @RequestBody CadastroClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastro.cadastrar(request));
    }
}
