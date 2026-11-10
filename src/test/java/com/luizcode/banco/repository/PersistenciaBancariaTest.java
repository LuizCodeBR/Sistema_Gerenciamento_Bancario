package com.luizcode.banco.repository;

import com.luizcode.banco.model.Cliente;
import com.luizcode.banco.model.Conta;
import com.luizcode.banco.model.Transacao;
import com.luizcode.banco.model.enums.StatusConta;
import com.luizcode.banco.model.enums.TipoConta;
import com.luizcode.banco.model.enums.TipoOperacao;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PersistenciaBancariaTest {
    @Autowired
    private ClienteRepository clientes;
    @Autowired
    private ContaRepository contas;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void buscaClientePorUuidCpfEEmailDepoisDePersistir() {
        Cliente cliente = salvarCliente("12345678909", "cliente@exemplo.test");
        UUID id = cliente.getId();
        entityManager.clear();

        assertThat(clientes.findById(id)).isPresent();
        assertThat(clientes.findByCpf("12345678909")).get()
                .extracting(Cliente::getId).isEqualTo(id);
        assertThat(clientes.findByEmail("cliente@exemplo.test")).get()
                .extracting(Cliente::getId).isEqualTo(id);
    }

    @Test
    void buscaContaPorUuidENumeroDepoisDePersistir() {
        Cliente cliente = salvarCliente("12345678909", "cliente@exemplo.test");
        Conta conta = salvarConta(cliente, "10001", TipoConta.CORRENTE);
        UUID id = conta.getId();
        entityManager.clear();

        assertThat(contas.findById(id)).isPresent();
        assertThat(contas.findByNumero("10001")).get()
                .extracting(Conta::getId).isEqualTo(id);
    }

    @Test
    void consultaContaComBloqueioDentroDeTransacao() {
        Cliente cliente = salvarCliente("12345678909", "cliente@exemplo.test");
        Conta conta = salvarConta(cliente, "10001", TipoConta.CORRENTE);
        UUID id = conta.getId();
        entityManager.clear();

        assertThat(contas.findByIdWithLock(id)).get()
                .extracting(Conta::getId).isEqualTo(id);
    }

    @Test
    void consultaComBloqueioRetornaVazioParaContaInexistente() {
        assertThat(contas.findByIdWithLock(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listaAsDuasContasDoClienteSemIncluirOutroTitular() {
        Cliente titular = salvarCliente("12345678909", "titular@exemplo.test");
        Cliente outro = salvarCliente("98765432100", "outro@exemplo.test");
        Conta corrente = salvarConta(titular, "10001", TipoConta.CORRENTE);
        Conta poupanca = salvarConta(titular, "10002", TipoConta.POUPANCA);
        salvarConta(outro, "20001", TipoConta.CORRENTE);
        UUID titularId = titular.getId();
        UUID correnteId = corrente.getId();
        UUID poupancaId = poupanca.getId();
        entityManager.clear();

        assertThat(contas.findAllByClienteId(titularId))
                .extracting(Conta::getId)
                .containsExactlyInAnyOrder(correnteId, poupancaId);
    }

    @Test
    void listaVaziaParaClienteSemContas() {
        Cliente cliente = salvarCliente("12345678909", "cliente@exemplo.test");
        assertThat(contas.findAllByClienteId(cliente.getId())).isEmpty();
    }

    @Test
    void persisteTransacaoComValorETipoDeOperacao() {
        Cliente cliente = salvarCliente("12345678909", "cliente@exemplo.test");
        Conta origem = salvarConta(cliente, "10001", TipoConta.CORRENTE);
        Conta destino = salvarConta(cliente, "10002", TipoConta.POUPANCA);
        Transacao transacao = novaTransacao(origem, destino, "pedido-1");
        entityManager.persistAndFlush(transacao);
        UUID id = transacao.getId();
        entityManager.clear();

        Transacao salva = entityManager.find(Transacao.class, id);
        assertThat(salva.getValor()).isEqualByComparingTo("25.50");
        assertThat(salva.getTipoOperacao()).isEqualTo(TipoOperacao.PIX);
        assertThat(salva.getContaOrigem().getNumero()).isEqualTo("10001");
        assertThat(salva.getContaDestino().getNumero()).isEqualTo("10002");
        assertThat(salva.getDataHora()).isNotNull();
    }

    @Test
    void bancoRejeitaChaveDeIdempotenciaDuplicada() {
        Cliente cliente = salvarCliente("12345678909", "cliente@exemplo.test");
        Conta origem = salvarConta(cliente, "10001", TipoConta.CORRENTE);
        Conta destino = salvarConta(cliente, "10002", TipoConta.POUPANCA);
        entityManager.persistAndFlush(novaTransacao(origem, destino, "pedido-1"));

        assertThatThrownBy(() -> entityManager.persistAndFlush(
                novaTransacao(origem, destino, "pedido-1")))
                .isInstanceOf(PersistenceException.class);
    }

    private Cliente salvarCliente(String cpf, String email) {
        return clientes.saveAndFlush(Cliente.builder()
                .cpf(cpf)
                .nomeCompleto("Cliente de teste")
                .email(email)
                .senhaHash("hash-ficticio-para-teste-de-persistencia")
                .build());
    }

    private Conta salvarConta(Cliente cliente, String numero, TipoConta tipo) {
        return contas.saveAndFlush(Conta.builder()
                .cliente(cliente)
                .agencia("0001")
                .numero(numero)
                .saldo(new BigDecimal("100.00"))
                .tipo(tipo)
                .status(StatusConta.ATIVA)
                .build());
    }

    private Transacao novaTransacao(Conta origem, Conta destino, String chave) {
        return Transacao.builder()
                .contaOrigem(origem)
                .contaDestino(destino)
                .valor(new BigDecimal("25.50"))
                .tipoOperacao(TipoOperacao.PIX)
                .chaveIdempotencia(chave)
                .build();
    }
}