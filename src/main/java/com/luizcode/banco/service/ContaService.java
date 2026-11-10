package com.luizcode.banco.service;

import com.luizcode.banco.dto.SaldoResponse;
import com.luizcode.banco.exception.RecursoNaoEncontradoException;
import com.luizcode.banco.model.Conta;
import com.luizcode.banco.repository.ContaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ContaService {
    private final ContaRepository contas;

    public ContaService(ContaRepository contas) {
        this.contas = contas;
    }

    @Transactional(readOnly = true)
    public java.util.List<SaldoResponse> listar(UUID titularId) {
        return contas.findAllByClienteId(titularId).stream().map(conta ->
                new SaldoResponse(conta.getAgencia(), conta.getNumero(), conta.getSaldo(), conta.getTipo(), conta.getStatus())).toList();
    }

    @Transactional(readOnly = true)
    public SaldoResponse consultarSaldo(String numero, UUID titularId) {
        Conta conta = contas.findByNumeroAndClienteId(numero, titularId)
                .orElseThrow(RecursoNaoEncontradoException::new);
        return new SaldoResponse(conta.getAgencia(), conta.getNumero(), conta.getSaldo(),
                conta.getTipo(), conta.getStatus());
    }
}
