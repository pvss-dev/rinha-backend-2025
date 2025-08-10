package br.com.pvss.rinhabackend2025.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<Void> handleDuplicate() {
        return ResponseEntity.unprocessableEntity().build();
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Void> handleValidation() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Void> handleInput() {
        return ResponseEntity.badRequest().build();
    }
}