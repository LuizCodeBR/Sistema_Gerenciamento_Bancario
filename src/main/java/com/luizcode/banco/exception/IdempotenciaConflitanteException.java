package com.luizcode.banco.exception;

public class IdempotenciaConflitanteException extends RuntimeException {
    public IdempotenciaConflitanteException() {
        super("A chave de idempotência já foi utilizada com outros dados nesta conta de origem.");
    }
}
