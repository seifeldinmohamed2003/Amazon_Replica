package com.team27.amazon.contracts.feign;

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.dto.ProductExistsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-service", url = "${FEIGN_PRODUCT_SERVICE_URL:http://product-service:8080}")
public interface ProductServiceClient {

    @GetMapping("/api/products/{id}")
    ProductDTO getProduct(@PathVariable("id") Long id);

    @GetMapping("/api/products/{id}/exists")
    ProductExistsDTO productExists(@PathVariable("id") Long id);

    @GetMapping("/api/products/batch")
    List<ProductDTO> getProductsBatch(@RequestParam("ids") List<Long> ids);
}
