package com.luizcode.banco.demo;

import com.luizcode.banco.dto.CadastroClienteRequest;
import com.luizcode.banco.dto.CadastroClienteResponse;
import com.luizcode.banco.model.Conta;
import com.luizcode.banco.model.enums.TipoConta;
import com.luizcode.banco.repository.ClienteRepository;
import com.luizcode.banco.repository.ContaRepository;
import com.luizcode.banco.service.CadastroClienteService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {
    private final CadastroClienteService cadastro;
    private final ContaRepository contas;
    private final ClienteRepository clientes;
    public DemoDataInitializer(CadastroClienteService cadastro, ContaRepository contas, ClienteRepository clientes) {
        this.cadastro = cadastro; this.contas = contas; this.clientes = clientes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (clientes.count() != 0) return;
        criar("12345678909", "Alice de Demonstração", "alice@demo.local", "DemoAlice123!", "1000.00");
        criar("52998224725", "Bruno de Demonstração", "bruno@demo.local", "DemoBruno123!", "500.00");
    }

    private void criar(String cpf, String nome, String email, String senha, String saldo) {
        CadastroClienteResponse criado = cadastro.cadastrar(new CadastroClienteRequest(cpf, nome, email, senha, TipoConta.CORRENTE));
        Conta conta = contas.findById(criado.conta().id()).orElseThrow();
        conta.setSaldo(new BigDecimal(saldo));
    }
}
