package org.example.session14_b3.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Đại diện cho một vận đơn được tạo bởi Shipping Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    private String shipmentId;
    private String orderId;
    private String trackingNumber;
    private String shippingAddress;
    private String carrier;
    private ShipmentStatus status;
    private String failureReason;

    private Instant createdAt;
    private Instant updatedAt;
}
