package com.team27.amazon.product.exception;

public class InvalidPriceRangeException extends RuntimeException {
    public InvalidPriceRangeException(Double minPrice, Double maxPrice) {
        super("Invalid price range: minimum price (" + minPrice + ") cannot be greater than maximum price (" + maxPrice + ")");
    }
}
