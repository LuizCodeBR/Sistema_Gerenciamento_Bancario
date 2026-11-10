package com.luizcode.banco.service;

import com.luizcode.banco.dto.CadastroClienteRequest;
import com.luizcode.banco.dto.CadastroClienteResponse;
import com.luizcode.banco.exception.CadastroDuplicadoException;
import com.luizcode.banco.exception.DadosInvalidosException;
import com.luizcode.banco.model.Cliente;
import com.luizcode.banco.model.Conta;
import com.luizcode.banco.model.enums.StatusConta;
import com.luizcode.banco.repository.ClienteRepository;
import com.luizcode.banco.repository.ContaRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Service
@Validated
public class CadastroClienteService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ClienteRepository clientes;
    private final ContaRepository contas;
    private final PasswordEncoder passwordEncoder;

    public CadastroClienteService(ClienteRepository clientes, ContaRepository contas,
                                  PasswordEncoder passwordEncoder) {
        this.clientes = clientes;
        this.contas = contas;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CadastroClienteResponse cadastrar(@Valid CadastroClienteRequest request) {
        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new DadosInvalidosException("senha",
                    "A senha deve ocupar no máximo 72 bytes em UTF-8.");
        }
        if (clientes.existsByCpf(request.cpf())) {
            throw new CadastroDuplicadoException("CPF já cadastrado.");
        }
        if (clientes.existsByEmail(request.email())) {
            throw new CadastroDuplicadoException("E-mail já cadastrado.");
        }

        Cliente cliente = clientes.saveAndFlush(Cliente.builder()
                .cpf(request.cpf())
                .nomeCompleto(request.nomeCompleto())
                .email(request.email())
                .senhaHash(passwordEncoder.encode(request.senha()))
                .build());

        Conta conta = contas.saveAndFlush(Conta.builder()
                .cliente(cliente)
                .agencia("0001")
                .numero(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .saldo(new BigDecimal("0.00"))
                .tipo(request.tipoConta())
                .status(StatusConta.ATIVA)
                .build());

        return new CadastroClienteResponse(cliente.getId(), cliente.getNomeCompleto(),
                cliente.getEmail(), cliente.getDataCriacao(),
                new CadastroClienteResponse.ContaResponse(conta.getId(), conta.getAgencia(),
                        conta.getNumero(), conta.getSaldo(), conta.getTipo(), conta.getStatus()));
    }
}
