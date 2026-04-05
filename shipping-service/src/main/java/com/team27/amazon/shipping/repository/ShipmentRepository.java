package com.team27.amazon.shipping.repository;

import com.team27.amazon.shipping.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Shipment s WHERE s.lastUpdate < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}