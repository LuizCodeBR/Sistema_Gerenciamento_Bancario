package com.luizcode.banco.controller;

import com.luizcode.banco.dto.LoginRequest;
import com.luizcode.banco.dto.LoginResponse;
import com.luizcode.banco.service.LoginService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final LoginService login;

    public AuthController(LoginService login) {
        this.login = login;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse entrar(@Valid @RequestBody LoginRequest request) {
        return login.entrar(request);
    }
}
