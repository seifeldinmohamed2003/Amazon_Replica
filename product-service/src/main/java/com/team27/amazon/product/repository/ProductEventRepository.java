package com.team27.amazon.product.repository;

import com.team27.amazon.common.events.ProductEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductEventRepository extends MongoRepository<ProductEvent, String> {
}