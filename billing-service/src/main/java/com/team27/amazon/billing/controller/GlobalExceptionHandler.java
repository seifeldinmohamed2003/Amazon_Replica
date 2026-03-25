package com.team27.amazon.billing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntime(RuntimeException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("not found")) {
            return ResponseEntity.status(404).body(msg);
        }
        if (msg != null && (msg.contains("already paid") || msg.contains("cannot") || msg.contains("must") || msg.contains("expired") || msg.contains("limit") || msg.contains("inactive") || msg.contains("voucher already") || msg.contains("only retry"))) {
            return ResponseEntity.status(400).body(msg);
        }
        if (msg != null && msg.contains("not an ADMIN")) {
            return ResponseEntity.status(403).body(msg);
        }
        return ResponseEntity.status(400).body(msg);
    }
}