package br.com.pvss.rinhabackend2025.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<Void> handleDuplicate() {
        return ResponseEntity.unprocessableEntity().build();
    }
}