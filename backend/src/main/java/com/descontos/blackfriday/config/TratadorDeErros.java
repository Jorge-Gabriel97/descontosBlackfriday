package com.descontos.blackfriday.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class TratadorDeErros {

    public record Erro(int status, String message) {
    }

    private static final Map<String, String> CAMPOS = Map.of(
            "nome", "Nome",
            "email", "E-mail",
            "senha", "Senha",
            "precoMaximo", "Preço máximo",
            "codigoProduto", "Produto",
            "loja", "Loja",
            "termo", "Termo de busca");

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Erro> status(ResponseStatusException e) {
        return erro(e.getStatusCode(), e.getReason() != null ? e.getReason() : "Erro " + e.getStatusCode().value());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Erro> validacao(MethodArgumentNotValidException e) {
        String mensagem = e.getBindingResult().getFieldErrors().stream()
                .map(f -> CAMPOS.getOrDefault(f.getField(), f.getField()) + ": " + f.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return erro(HttpStatus.BAD_REQUEST, mensagem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Erro> validacaoParametro(HandlerMethodValidationException e) {
        String mensagem = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> CAMPOS.getOrDefault(r.getMethodParameter().getParameterName(),
                                r.getMethodParameter().getParameterName()) + ": " + err.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return erro(HttpStatus.BAD_REQUEST, mensagem);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Erro> requisicaoInvalida(Exception e) {
        return erro(HttpStatus.BAD_REQUEST, "Requisição inválida");
    }

    private static ResponseEntity<Erro> erro(HttpStatusCode status, String mensagem) {
        return ResponseEntity.status(status).body(new Erro(status.value(), mensagem));
    }
}
