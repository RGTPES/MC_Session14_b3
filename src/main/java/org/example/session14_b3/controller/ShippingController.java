package org.example.session14_b3.controller;

import lombok.RequiredArgsConstructor;
import org.example.session14_b3.model.Shipment;
import org.example.session14_b3.service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

/**
 * REST Controller cho Shipping Service.
 *
 * GET /api/shipments – Liệt kê tất cả vận đơn (thành công & thất bại)
 */
@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    /** Liệt kê tất cả vận đơn. */
    @GetMapping
    public ResponseEntity<Collection<Shipment>> getAllShipments() {
        return ResponseEntity.ok(shippingService.getAllShipments());
    }
}
