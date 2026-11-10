package com.luizcode.banco.service;

import com.luizcode.banco.dto.LoginRequest;
import com.luizcode.banco.dto.LoginResponse;
import com.luizcode.banco.model.Cliente;
import com.luizcode.banco.repository.ClienteRepository;
import com.luizcode.banco.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class LoginService {
    private final ClienteRepository clientes;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final String hashFicticio;

    public LoginService(ClienteRepository clientes, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.clientes = clientes;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse entrar(LoginRequest request) {
        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }
        Cliente cliente = clientes.findByEmail(request.email()).orElse(null);
        // Mesmo um e-mail inexistente passa pelo cálculo BCrypt.
        boolean senhaCorreta = passwordEncoder.matches(request.senha(),
                cliente == null ? hashFicticio : cliente.getSenhaHash());
        if (cliente == null || !senhaCorreta) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }
        return jwt.emitir(cliente.getId());
    }
}
