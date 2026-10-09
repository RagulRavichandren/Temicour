package com.courier.repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.courier.entity.Shipment;
public interface ShipmentRepository extends JpaRepository<Shipment,Long> {
 Optional<Shipment> findByTrackingNumber(String trackingNumber);
 boolean existsByReturnOfTrackingNumberIgnoreCase(String returnOfTrackingNumber);
 List<Shipment> findBySourceRegionIgnoreCaseOrDestinationRegionIgnoreCase(String sourceRegion, String destinationRegion);
 List<Shipment> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);
 List<Shipment> findByCustomerUsernameIgnoreCase(String customerUsername);
}