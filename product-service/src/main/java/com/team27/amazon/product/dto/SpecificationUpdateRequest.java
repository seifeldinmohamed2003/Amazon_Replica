package com.team27.amazon.product.dto;

import java.util.HashMap;
import java.util.Map;

public class SpecificationUpdateRequest {
    private Map<String, Object> specifications = new HashMap<>();

    public SpecificationUpdateRequest() {
    }

    public SpecificationUpdateRequest(Map<String, Object> specifications) {
        this.specifications = specifications != null ? new HashMap<>(specifications) : new HashMap<>();
    }

    public Map<String, Object> getSpecifications() {
        return specifications;
    }

    public void setSpecifications(Map<String, Object> specifications) {
        this.specifications = specifications != null ? specifications : new HashMap<>();
    }
}
