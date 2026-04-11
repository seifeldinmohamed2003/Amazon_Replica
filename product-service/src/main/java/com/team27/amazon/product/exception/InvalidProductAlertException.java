package com.team27.amazon.product.exception;

public class InvalidProductAlertException extends RuntimeException {

    public InvalidProductAlertException(String message) {
        super(message);
    }
}