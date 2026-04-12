package com.team27.amazon.billing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null) return ResponseEntity.status(400).body("Bad request");

        if (msg.toLowerCase().contains("not found")) {
            return ResponseEntity.status(404).body(msg);
        }
        if (msg.contains("403") || msg.contains("not an ADMIN")) {
            return ResponseEntity.status(403).body(msg);
        }
        return ResponseEntity.status(400).body(msg);
    }
}