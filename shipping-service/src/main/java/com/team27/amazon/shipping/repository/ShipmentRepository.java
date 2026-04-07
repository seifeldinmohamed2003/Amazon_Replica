package com.team27.amazon.shipping.repository;

import com.team27.amazon.shipping.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    @Query(value = """
    SELECT
        s.id AS shipment_id,
        s.order_id AS order_id,
        s.carrier AS carrier,
        s.tracking_number AS tracking_number,
        s.estimated_delivery AS estimated_delivery,
        (CURRENT_DATE - s.estimated_delivery) AS days_overdue,
        COALESCE(CAST(s.metadata ->> 'deliveryAttempts' AS INTEGER), 0) AS delivery_attempts
    FROM shipments s
    WHERE s.estimated_delivery < CURRENT_DATE
      AND s.status NOT IN ('DELIVERED', 'RETURNED')
      AND (
            :maxDeliveryAttempts IS NULL
            OR COALESCE(CAST(s.metadata ->> 'deliveryAttempts' AS INTEGER), 0) <= :maxDeliveryAttempts
      )
    ORDER BY days_overdue DESC
    """, nativeQuery = true)
List<Object[]> findDelayedShipments(@Param("maxDeliveryAttempts") Integer maxDeliveryAttempts);
}