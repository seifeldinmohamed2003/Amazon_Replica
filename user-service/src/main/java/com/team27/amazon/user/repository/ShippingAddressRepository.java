package com.team27.amazon.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import com.team27.amazon.user.model.ShippingAddress;

@Repository
@RepositoryRestResource
public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {
    java.util.List<ShippingAddress> findByUser_Id(Long userId);
}