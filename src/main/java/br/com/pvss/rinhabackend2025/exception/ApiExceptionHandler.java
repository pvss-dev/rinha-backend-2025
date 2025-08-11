package br.com.pvss.rinhabackend2025.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Void> handleValidation() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> handleInput() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<Void> handleDuplicate() {
        return ResponseEntity.unprocessableEntity().build();
    }
}