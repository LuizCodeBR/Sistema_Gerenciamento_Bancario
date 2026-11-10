package com.luizcode.banco.exception;

import com.luizcode.banco.dto.ErroApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroApi> validarCampos(MethodArgumentNotValidException exception) {
        Map<String, String> campos = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return resposta(HttpStatus.BAD_REQUEST, "Confira os campos informados.", campos);
    }

    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<ErroApi> validarDados(DadosInvalidosException exception) {
        return resposta(HttpStatus.BAD_REQUEST, "Confira os campos informados.",
                Map.of(exception.getCampo(), exception.getMessage()));
    }

    @ExceptionHandler(CadastroDuplicadoException.class)
    public ResponseEntity<ErroApi> cadastroDuplicado(CadastroDuplicadoException exception) {
        return resposta(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroApi> conflitoDePersistencia() {
        // A restrição no banco também protege contra cadastros simultâneos.
        return resposta(HttpStatus.CONFLICT,
                "Não foi possível concluir o cadastro por um conflito de dados.", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroApi> jsonInvalido() {
        return resposta(HttpStatus.BAD_REQUEST,
                "Corpo JSON inválido. Confira os campos informados.", Map.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroApi> credenciaisInvalidas() {
        return resposta(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.", Map.of());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroApi> contaNaoEncontrada() {
        return resposta(HttpStatus.NOT_FOUND, "Conta não encontrada.", Map.of());
    }
    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroApi> regraNegocio(RegraNegocioException exception) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IdempotenciaConflitanteException.class)
    public ResponseEntity<ErroApi> chaveConflitante(IdempotenciaConflitanteException exception) {
        return resposta(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<ErroApi> contaOcupada() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "1")
                .body(new ErroApi(Instant.now(), 503, "Conta ocupada. Repita o pedido com a mesma chave de idempotência.", Map.of()));
    }

    private ResponseEntity<ErroApi> resposta(HttpStatus status, String mensagem,
                                           Map<String, String> campos) {
        return ResponseEntity.status(status)
                .body(new ErroApi(Instant.now(), status.value(), mensagem, campos));
    }
}
