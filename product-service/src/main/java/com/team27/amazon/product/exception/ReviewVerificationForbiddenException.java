package com.team27.amazon.product.exception;

public class ReviewVerificationForbiddenException extends RuntimeException {

    public ReviewVerificationForbiddenException(String message) {
        super(message);
    }
}