package com.team27.amazon.shipping.repository;

import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);

    // M3: Used by S4-F1 to get the latest shipment by last update
    Optional<Shipment> findFirstByOrderIdOrderByLastUpdateDesc(Long orderId);

    // M3: Used by saga consumer to find active shipment for order.completed / order.cancelled
    Optional<Shipment> findFirstByOrderIdAndStatusInOrderByLastUpdateDesc(
            Long orderId,
            Collection<ShipmentStatus> statuses
    );

    // M3: Useful for GET /api/shipments/order/{orderId}/ids
    List<Shipment> findByOrderId(Long orderId);

    List<Shipment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Shipment> findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ShipmentStatus status);

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
          AND s.status NOT IN ('DELIVERED', 'RETURNED', 'CANCELLED')
          AND (
                :maxDeliveryAttempts IS NULL
                OR COALESCE(CAST(s.metadata ->> 'deliveryAttempts' AS INTEGER), 0) <= :maxDeliveryAttempts
          )
        ORDER BY days_overdue DESC
        """, nativeQuery = true)
    List<Object[]> findDelayedShipments(@Param("maxDeliveryAttempts") Integer maxDeliveryAttempts);

    @Query("SELECT s FROM Shipment s WHERE s.lastUpdate BETWEEN :startDate AND :endDate AND (:status IS NULL OR s.status = :status) ORDER BY s.lastUpdate ASC")
    List<Shipment> findShipmentsByDateRangeAndStatus(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") ShipmentStatus status
    );

    @Query(value = """
            SELECT * FROM shipments s
            WHERE jsonb_exists(s.metadata, :key)
              AND LOWER(jsonb_extract_path_text(s.metadata, :key)) = LOWER(:value)
            """, nativeQuery = true)
    List<Shipment> findByMetadataKeyAndValueEquals(
            @Param("key") String key,
            @Param("value") String value
    );

    @Query(value = """
            SELECT * FROM shipments s
            WHERE jsonb_exists(s.metadata, :key)
              AND jsonb_extract_path_text(s.metadata, :key) ~ '^[0-9]+(\\.[0-9]+)?$'
              AND CAST(jsonb_extract_path_text(s.metadata, :key) AS DOUBLE PRECISION) > CAST(:value AS DOUBLE PRECISION)
            """, nativeQuery = true)
    List<Shipment> findByMetadataKeyAndValueGreaterThan(
            @Param("key") String key,
            @Param("value") String value
    );

    @Query(value = """
            SELECT * FROM shipments s
            WHERE jsonb_exists(s.metadata, :key)
              AND jsonb_extract_path_text(s.metadata, :key) ~ '^[0-9]+(\\.[0-9]+)?$'
              AND CAST(jsonb_extract_path_text(s.metadata, :key) AS DOUBLE PRECISION) < CAST(:value AS DOUBLE PRECISION)
            """, nativeQuery = true)
    List<Shipment> findByMetadataKeyAndValueLessThan(
            @Param("key") String key,
            @Param("value") String value
    );
    List<Shipment> findByOrderId(Long orderId);

    @Query("""
        SELECT s FROM Shipment s
        WHERE s.orderId = :orderId
        AND s.status IN (
            com.team27.amazon.shipping.model.ShipmentStatus.PROCESSING,
            com.team27.amazon.shipping.model.ShipmentStatus.SHIPPED,
            com.team27.amazon.shipping.model.ShipmentStatus.IN_TRANSIT,
            com.team27.amazon.shipping.model.ShipmentStatus.OUT_FOR_DELIVERY
        )
        ORDER BY s.createdAt DESC
    """)
    List<Shipment> findActiveShipmentsForOrder(@Param("orderId") Long orderId);

}