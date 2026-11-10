package com.luizcode.banco.exception;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException() {
        super("Conta não encontrada.");
    }
}
