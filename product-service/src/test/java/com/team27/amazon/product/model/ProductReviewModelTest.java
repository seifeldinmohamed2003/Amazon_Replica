package com.team27.amazon.product.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductReviewModelTest {

    @Test
    void prePersistInitializesDefaultFields() throws Exception {
        ProductReview review = new ProductReview();
        review.setUserId(10L);
        review.setRating(5);
        review.setTitle("Great product");
        review.setComment("Works as expected");
        review.setMetadata(null);
        review.setVerified(null);

        Method onCreate = ProductReview.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(review);

        assertNotNull(review.getCreatedAt());
        assertFalse(review.getVerified());
        assertNotNull(review.getMetadata());
        assertEquals(new HashMap<>(), review.getMetadata());
    }
}

