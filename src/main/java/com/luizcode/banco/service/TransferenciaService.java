package com.luizcode.banco.service;

import com.luizcode.banco.dto.TransferenciaRequest;
import com.luizcode.banco.dto.TransferenciaResponse;
import com.luizcode.banco.exception.*;
import com.luizcode.banco.model.Conta;
import com.luizcode.banco.model.Transacao;
import com.luizcode.banco.model.enums.StatusConta;
import com.luizcode.banco.model.enums.TipoOperacao;
import com.luizcode.banco.repository.ContaRepository;
import com.luizcode.banco.repository.TransacaoRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Validated
public class TransferenciaService {
    private static final BigDecimal SALDO_MAXIMO = new BigDecimal("9999999999999.99");
    private final ContaRepository contas;
    private final TransacaoRepository transacoes;

    public TransferenciaService(ContaRepository contas, TransacaoRepository transacoes) {
        this.contas = contas;
        this.transacoes = transacoes;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
    public TransferenciaResponse transferir(@Valid TransferenciaRequest request, UUID titularId) {
        if (request.tipoOperacao() != TipoOperacao.PIX && request.tipoOperacao() != TipoOperacao.TED) {
            throw new DadosInvalidosException("tipoOperacao", "Use PIX ou TED para uma transferência.");
        }
        UUID origemId = contas.findIdDoTitular(request.contaOrigem(), titularId)
                .orElseThrow(RecursoNaoEncontradoException::new);
        String chaveInterna = chaveInterna(origemId, request.chaveIdempotencia());
        Transacao existente = transacoes.findByChaveIdempotencia(chaveInterna).orElse(null);
        if (existente != null) return repetir(existente, request);
        UUID destinoId = contas.findIdPorNumero(request.contaDestino())
                .orElseThrow(RecursoNaoEncontradoException::new);
        if (origemId.equals(destinoId)) {
            throw new RegraNegocioException("Origem e destino devem ser contas diferentes.");
        }

        // Todos os processos adquirem os bloqueios na mesma ordem, inclusive A->B e B->A.
        UUID primeiroId = origemId.compareTo(destinoId) < 0 ? origemId : destinoId;
        UUID segundoId = primeiroId.equals(origemId) ? destinoId : origemId;
        Conta primeira = contas.findByIdWithLock(primeiroId).orElseThrow(RecursoNaoEncontradoException::new);
        Conta segunda = contas.findByIdWithLock(segundoId).orElseThrow(RecursoNaoEncontradoException::new);
        Conta origem = primeira.getId().equals(origemId) ? primeira : segunda;
        Conta destino = primeira.getId().equals(destinoId) ? primeira : segunda;
        if (!origem.getCliente().getId().equals(titularId)) throw new RecursoNaoEncontradoException();

        // Após esperar um bloqueio, READ_COMMITTED permite enxergar o comprovante recém-confirmado.
        existente = transacoes.findByChaveIdempotencia(chaveInterna).orElse(null);
        if (existente != null) return repetir(existente, request);
        if (origem.getStatus() != StatusConta.ATIVA || destino.getStatus() != StatusConta.ATIVA) {
            throw new RegraNegocioException("As duas contas devem estar ativas.");
        }
        BigDecimal valor = request.valor().setScale(2, RoundingMode.UNNECESSARY);
        if (origem.getSaldo().compareTo(valor) < 0) throw new RegraNegocioException("Saldo insuficiente.");
        BigDecimal saldoDestino = destino.getSaldo().add(valor);
        if (saldoDestino.compareTo(SALDO_MAXIMO) > 0) {
            throw new RegraNegocioException("O crédito ultrapassa o limite de saldo da conta de destino.");
        }
        origem.setSaldo(origem.getSaldo().subtract(valor));
        destino.setSaldo(saldoDestino);
        Transacao transacao = transacoes.saveAndFlush(Transacao.builder()
                .contaOrigem(origem).contaDestino(destino).valor(valor)
                .tipoOperacao(request.tipoOperacao()).chaveIdempotencia(chaveInterna).build());
        return resposta(transacao, request.chaveIdempotencia());
    }

    private TransferenciaResponse repetir(Transacao transacao, TransferenciaRequest request) {
        if (!transacao.getContaOrigem().getNumero().equals(request.contaOrigem())
                || !transacao.getContaDestino().getNumero().equals(request.contaDestino())
                || transacao.getValor().compareTo(request.valor()) != 0
                || transacao.getTipoOperacao() != request.tipoOperacao()) {
            throw new IdempotenciaConflitanteException();
        }
        return resposta(transacao, request.chaveIdempotencia());
    }

    private TransferenciaResponse resposta(Transacao transacao, String chave) {
        return new TransferenciaResponse(transacao.getId(), transacao.getContaOrigem().getNumero(),
                transacao.getContaDestino().getNumero(), transacao.getValor(), transacao.getTipoOperacao(),
                transacao.getDataHora(), chave);
    }

    private String chaveInterna(UUID origemId, String chave) {
        try {
            byte[] bytes = (origemId + ":" + chave).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }
}
