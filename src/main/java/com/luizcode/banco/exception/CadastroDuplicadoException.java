package com.luizcode.banco.exception;

public class CadastroDuplicadoException extends RuntimeException {
    public CadastroDuplicadoException(String mensagem) {
        super(mensagem);
    }
}
