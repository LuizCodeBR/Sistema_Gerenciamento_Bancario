package com.luizcode.banco.exception;

public class DadosInvalidosException extends RuntimeException {
    private final String campo;

    public DadosInvalidosException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
