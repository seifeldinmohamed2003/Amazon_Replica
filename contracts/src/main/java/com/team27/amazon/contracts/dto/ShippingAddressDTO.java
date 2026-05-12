package com.team27.amazon.contracts.dto;

public record ShippingAddressDTO(
        Long id,
        Long userId,
        String addressLine,
        String city,
        String governorate,
        String postalCode,
        String country
) {}
