package com.team27.amazon.shipping.repository;

import com.team27.amazon.shipping.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    @Query(value = "SELECT COUNT(*) FROM shipments s WHERE s.last_update < :cutoff", nativeQuery = true)
    int countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query(value = "DELETE FROM shipments s WHERE s.last_update < :cutoff", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}