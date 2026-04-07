package com.team27.amazon.shipping.repository;

import com.team27.amazon.shipping.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    @Query(value = "SELECT COUNT(*) FROM shipments s WHERE s.last_update < :cutoff", nativeQuery = true)
    int countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query(value = "DELETE FROM shipments s WHERE s.last_update < :cutoff", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
        SELECT s FROM Shipment s
        WHERE s.carrier = :carrier
        AND s.createdAt BETWEEN :start AND :end
    """)
    List<Shipment> findByCarrierAndDateRange(
            @Param("carrier") String carrier,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}