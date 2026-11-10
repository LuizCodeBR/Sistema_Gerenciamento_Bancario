package com.luizcode.banco.service;

import com.luizcode.banco.dto.TransferenciaRequest;
import com.luizcode.banco.dto.TransferenciaResponse;
import com.luizcode.banco.exception.*;
import com.luizcode.banco.model.*;
import com.luizcode.banco.model.enums.*;
import com.luizcode.banco.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

abstract class TransferenciaContract {
    @Autowired protected TransferenciaService service;
    @Autowired protected ClienteRepository clientes;
    @Autowired protected ContaRepository contas;
    @SpyBean protected TransacaoRepository transacoes;
    @PersistenceContext protected EntityManager entityManager;
    protected UUID titularA;
    protected UUID titularB;
    protected UUID contaA;
    protected UUID contaB;
    protected UUID contaC;

    @BeforeEach
    void prepararContas() {
        transacoes.deleteAllInBatch(); contas.deleteAllInBatch(); clientes.deleteAllInBatch();
        Cliente a = clientes.saveAndFlush(Cliente.builder().cpf("12345678909").nomeCompleto("Titular A")
                .email("a@exemplo.test").senhaHash("fixture").build());
        Cliente b = clientes.saveAndFlush(Cliente.builder().cpf("52998224725").nomeCompleto("Titular B")
                .email("b@exemplo.test").senhaHash("fixture").build());
        titularA = a.getId(); titularB = b.getId();
        contaA = criarConta(a, "10001", "100.00");
        contaB = criarConta(b, "20001", "0.00");
        contaC = criarConta(b, "30001", "0.00");
    }

    @Test
    void debitoCreditoEComprovanteSaoConfirmadosJuntos() {
        TransferenciaResponse resultado = service.transferir(pedido("10.50", "primeiro"), titularA);
        assertThat(resultado.id()).isNotNull();
        assertThat(resultado.dataHora()).isNotNull();
        assertThat(resultado.valor()).isEqualByComparingTo("10.50");
        assertSaldo(contaA, "89.50"); assertSaldo(contaB, "10.50");
        assertThat(transacoes.count()).isEqualTo(1);
    }

    @Test
    void repeticaoRetornaMesmoComprovanteAposOutrasOperacoes() {
        TransferenciaResponse original = service.transferir(pedido("10.00", "repetir"), titularA);
        service.transferir(pedido("5.00", "outra"), titularA);
        TransferenciaResponse repetida = service.transferir(pedido("10.0", "repetir"), titularA);
        assertThat(repetida).isEqualTo(original);
        assertSaldo(contaA, "85.00"); assertSaldo(contaB, "15.00");
        assertThat(transacoes.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"valor", "destino", "tipo"})
    void mesmaChaveComOutroConteudoRetornaConflito(String campo) {
        service.transferir(pedido("10.00", "repetir"), titularA);
        TransferenciaRequest alterado = new TransferenciaRequest("10001", campo.equals("destino") ? "30001" : "20001",
                new BigDecimal(campo.equals("valor") ? "11.00" : "10.00"), "repetir",
                campo.equals("tipo") ? TipoOperacao.TED : TipoOperacao.PIX);
        assertThatThrownBy(() -> service.transferir(alterado, titularA))
                .isInstanceOf(IdempotenciaConflitanteException.class);
        assertSaldo(contaA, "90.00"); assertSaldo(contaB, "10.00"); assertSaldo(contaC, "0.00");
        assertThat(transacoes.count()).isEqualTo(1);
    }

    @Test
    void mesmaChavePodeSerUsadaEmOutraContaDeOrigem() {
        alterarSaldo(contaB, "100.00");
        service.transferir(pedido("10.00", "chave-comum"), titularA);
        service.transferir(new TransferenciaRequest("20001", "10001", new BigDecimal("10.00"),
                "chave-comum", TipoOperacao.PIX), titularB);
        assertSaldo(contaA, "100.00"); assertSaldo(contaB, "100.00");
        assertThat(transacoes.count()).isEqualTo(2);
    }

    @Test
    void saldoInsuficienteNaoAlteraNenhumaConta() {
        assertThatThrownBy(() -> service.transferir(pedido("100.01", "sem-saldo"), titularA))
                .isInstanceOf(RegraNegocioException.class);
        assertSaldo(contaA, "100.00"); assertSaldo(contaB, "0.00");
        assertThat(transacoes.count()).isZero();
    }

    @Test
    void contaDeOutroTitularNaoPodeSerDebitada() {
        assertThatThrownBy(() -> service.transferir(pedido("10.00", "indevida"), titularB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertSaldo(contaA, "100.00"); assertThat(transacoes.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"origem-bloqueada", "destino-bloqueado", "origem-encerrada", "destino-encerrado"})
    void contasInativasNaoTransferem(String caso) {
        Conta conta = contas.findById(caso.startsWith("origem") ? contaA : contaB).orElseThrow();
        conta.setStatus(caso.endsWith("bloqueada") || caso.endsWith("bloqueado") ? StatusConta.BLOQUEADA : StatusConta.ENCERRADA);
        contas.saveAndFlush(conta);
        assertThatThrownBy(() -> service.transferir(pedido("1.00", "inativa"), titularA))
                .isInstanceOf(RegraNegocioException.class);
        assertSaldo(contaA, "100.00"); assertSaldo(contaB, "0.00");
        assertThat(transacoes.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "10000000000000.00"})
    void valoresInvalidosNaoSaoAceitos(String valor) {
        assertThatThrownBy(() -> service.transferir(pedido(valor, "valor-invalido"), titularA))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(transacoes.count()).isZero();
    }

    @Test
    void rejeitaDestinoInexistenteEOrigemIgualAoDestino() {
        assertThatThrownBy(() -> service.transferir(new TransferenciaRequest("10001", "99999",
                BigDecimal.ONE, "ausente", TipoOperacao.PIX), titularA)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.transferir(new TransferenciaRequest("10001", "10001",
                BigDecimal.ONE, "mesma", TipoOperacao.PIX), titularA)).isInstanceOf(RegraNegocioException.class);
        assertThat(transacoes.count()).isZero();
    }

    @Test
    void naoAceitaDepositoDisfarcadoDeTransferencia() {
        assertThatThrownBy(() -> service.transferir(new TransferenciaRequest("10001", "20001",
                BigDecimal.ONE, "deposito", TipoOperacao.DEPOSITO), titularA)).isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void impedeEstouroDaPrecisaoDoSaldoDeDestino() {
        alterarSaldo(contaB, "9999999999999.99");
        assertThatThrownBy(() -> service.transferir(pedido("0.01", "limite"), titularA))
                .isInstanceOf(RegraNegocioException.class);
        assertSaldo(contaA, "100.00"); assertSaldo(contaB, "9999999999999.99");
        assertThat(transacoes.count()).isZero();
    }

    @Test
    void falhaAposGravarSaldosDesfazDebitoECredito() {
        doAnswer(invocation -> { entityManager.flush(); throw new IllegalStateException("falha-controlada"); })
                .when(transacoes).saveAndFlush(any(Transacao.class));
        assertThatThrownBy(() -> service.transferir(pedido("10.00", "rollback"), titularA))
                .isInstanceOf(IllegalStateException.class);
        assertSaldo(contaA, "100.00"); assertSaldo(contaB, "0.00");
        assertThat(transacoes.count()).isZero();
    }

    @Test
    void pedidosSimultaneosNaoPerdemAtualizacoes() throws Exception {
        List<TransferenciaResponse> respostas = emParalelo(8, i -> () ->
                service.transferir(pedido("7.00", "paralelo-" + i), titularA));
        assertThat(respostas).hasSize(8);
        assertSaldo(contaA, "44.00"); assertSaldo(contaB, "56.00");
        assertThat(transacoes.count()).isEqualTo(8);
    }

    @Test
    void concorrenciaNaoPermiteGastarOMesmoSaldoDuasVezes() throws Exception {
        List<Boolean> resultados = emParalelo(8, i -> () -> {
            try { service.transferir(pedido("60.00", "gasto-" + i), titularA); return true; }
            catch (RegraNegocioException exception) { return false; }
        });
        assertThat(resultados.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertSaldo(contaA, "40.00"); assertSaldo(contaB, "60.00");
        assertThat(transacoes.count()).isEqualTo(1);
    }

    @Test
    void repeticoesSimultaneasDebitamUmaUnicaVez() throws Exception {
        List<TransferenciaResponse> resultados = emParalelo(8, i -> () ->
                service.transferir(pedido("10.00", "mesmo-pedido"), titularA));
        assertThat(resultados.stream().map(TransferenciaResponse::id).distinct().count()).isEqualTo(1);
        assertSaldo(contaA, "90.00"); assertSaldo(contaB, "10.00");
        assertThat(transacoes.count()).isEqualTo(1);
    }

    @Test
    void transferenciasOpostasUsamOrdemDeBloqueioConsistente() throws Exception {
        alterarSaldo(contaB, "100.00");
        emParalelo(8, i -> () -> service.transferir(new TransferenciaRequest(
                i % 2 == 0 ? "10001" : "20001", i % 2 == 0 ? "20001" : "10001",
                BigDecimal.ONE, "oposta-" + i, TipoOperacao.PIX), i % 2 == 0 ? titularA : titularB));
        assertSaldo(contaA, "100.00"); assertSaldo(contaB, "100.00");
        assertThat(transacoes.count()).isEqualTo(8);
    }

    @Test
    void pedidosSimultaneosComMesmaChaveEValoresDiferentesConflitam() throws Exception {
        List<Boolean> resultados = emParalelo(2, i -> () -> {
            try { service.transferir(pedido(i == 0 ? "1.00" : "2.00", "conflitante"), titularA); return true; }
            catch (IdempotenciaConflitanteException exception) { return false; }
        });
        assertThat(resultados.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(transacoes.count()).isEqualTo(1);
        assertThat(contas.findById(contaA).orElseThrow().getSaldo()
                .add(contas.findById(contaB).orElseThrow().getSaldo())).isEqualByComparingTo("100.00");
    }

    private <T> List<T> emParalelo(int quantidade, IntFunction<Callable<T>> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(quantidade);
        CountDownLatch prontos = new CountDownLatch(quantidade);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < quantidade; i++) {
                Callable<T> acao = tarefa.apply(i);
                futuros.add(executor.submit(() -> { prontos.countDown();
                    if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Início não liberado.");
                    return acao.call(); }));
            }
            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue();
            inicio.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) resultados.add(futuro.get(30, TimeUnit.SECONDS));
            return resultados;
        } finally {
            inicio.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    protected TransferenciaRequest pedido(String valor, String chave) {
        return new TransferenciaRequest("10001", "20001", new BigDecimal(valor), chave, TipoOperacao.PIX);
    }
    private UUID criarConta(Cliente cliente, String numero, String saldo) {
        return contas.saveAndFlush(Conta.builder().cliente(cliente).agencia("0001").numero(numero)
                .saldo(new BigDecimal(saldo)).tipo(TipoConta.CORRENTE).status(StatusConta.ATIVA).build()).getId();
    }
    protected void alterarSaldo(UUID id, String valor) {
        Conta conta = contas.findById(id).orElseThrow(); conta.setSaldo(new BigDecimal(valor)); contas.saveAndFlush(conta);
    }
    protected void assertSaldo(UUID id, String valor) {
        assertThat(contas.findById(id).orElseThrow().getSaldo()).isEqualByComparingTo(valor);
    }
}
