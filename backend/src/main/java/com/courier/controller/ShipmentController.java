package com.courier.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.courier.entity.Shipment;
import com.courier.service.ShipmentService;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {
    private final ShipmentService service;

    public ShipmentController(ShipmentService service) { this.service = service; }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidShipmentInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @GetMapping
    public List<Shipment> all(@RequestHeader(value = "X-Operating-Region", defaultValue = "ALL") String region,
                              @RequestHeader(value = "X-Customer-Username", defaultValue = "") String customer) {
        if (!customer.isBlank()) return service.customerShipments(customer);
        return service.all(region);
    }

    @PostMapping
    public Shipment create(@RequestHeader(value = "X-Operating-Region", defaultValue = "ALL") String region,
                           @RequestHeader(value = "X-Customer-Username", defaultValue = "") String customer,
                           @RequestBody Shipment s) {
        if (!customer.isBlank()) s.setCustomerUsername(customer.trim());
        return service.create(s, false, region);
    }

    @PostMapping("/manual")
    public Shipment manual(@RequestHeader(value = "X-Operating-Region", defaultValue = "ALL") String region,
                           @RequestBody Shipment s) {
        if (region == null || region.isBlank() || "ALL".equalsIgnoreCase(region.trim())) {
            throw new IllegalStateException("Manual shipment entry is available only for regional Export operations");
        }
        return service.create(s, true, region);
    }

    @PostMapping("/return")
    public Shipment createReturn(@RequestHeader(value = "X-Operating-Region", defaultValue = "") String region,
                                 @RequestHeader(value = "X-Region-Role", defaultValue = "") String role,
                                 @RequestBody Map<String, String> body) {
        if (!"REGION_ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalStateException("Only regional administrators can create return shipments");
        }
        return service.createReturn(body.get("trackingNumber"), region, body.get("reason"));
    }

    @PutMapping("/{tracking}/status")
    public Shipment status(@PathVariable String tracking, @RequestParam String value,
                           @RequestHeader(value = "X-Operating-Region", defaultValue = "ALL") String region,
                           @RequestHeader(value = "X-Region-Role", defaultValue = "") String role,
                           @RequestParam(required = false) Double weight,
                           @RequestParam(required = false) Double amount) {
        if (!"REGION_ADMIN".equalsIgnoreCase(role)
            || region == null || region.isBlank() || "ALL".equalsIgnoreCase(region.trim())) {
            throw new IllegalStateException("Shipment tracking updates are available only to regional administrators");
        }

        return service.status(tracking, value, region, weight, amount);
    }

    @PutMapping("/{tracking}/destination-payment-verification")
    public Shipment verifyDestinationPayment(@PathVariable String tracking,
                           @RequestHeader(value = "X-Operating-Region", defaultValue = "ALL") String region,
                           @RequestHeader(value = "X-Region-Role", defaultValue = "") String role,
                           @RequestParam Double weight,
                           @RequestParam Double amount) {
        if (!"REGION_ADMIN".equalsIgnoreCase(role)
            || region == null || region.isBlank() || "ALL".equalsIgnoreCase(region.trim())) {
            throw new IllegalStateException("Destination payment verification is available only to regional administrators");
        }
        return service.verifyDestinationPayment(tracking, region, weight, amount);
    }

    @PutMapping("/{tracking}")
    public Shipment updateUnverified(@PathVariable String tracking,
            @RequestHeader(value = "X-Operating-Region", defaultValue = "") String region,
            @RequestHeader(value = "X-Region-Role", defaultValue = "") String role,
            @RequestBody Shipment shipment) {
        if (!"REGION_ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalStateException("Only regional administrators can edit shipments");
        }
        return service.updateUnverified(tracking, shipment, region);
    }

    @DeleteMapping("/{tracking}")
    public void delete(@PathVariable String tracking,
                       @RequestHeader(value = "X-Operating-Region", defaultValue = "ALL") String region) {
        service.deleteUnverified(tracking, region);
    }

    @DeleteMapping("/reset")
    public void resetAll(@RequestHeader(value = "X-Admin-Role", defaultValue = "") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalStateException("Only the global admin can reset all shipments");
        }
        service.resetAll();
    }

    @PostMapping("/demo-seed")
    public List<Shipment> seedDemoData(@RequestHeader(value = "X-Admin-Role", defaultValue = "") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalStateException("Only the global admin can seed demo shipments");
        }
        service.resetAll();
        return service.seedDemoData();
    }
}
