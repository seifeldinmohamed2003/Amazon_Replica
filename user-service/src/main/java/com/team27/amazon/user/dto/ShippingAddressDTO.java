package com.team27.amazon.user.dto;

import java.util.List;
import java.util.Map;

public record ShippingAddressDTO(
        String label,
        String streetAddress,
        String city,
        String country,
        String zipCode,
        Boolean isDefault,
        Map<String, Object> metadata
) {}
