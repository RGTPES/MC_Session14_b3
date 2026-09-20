package org.example.session14_b3.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Đại diện cho một đơn hàng trong hệ thống.
 * Được lưu in-memory trong OrderService (không dùng DB để đơn giản hoá demo).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private String orderId;
    private String customerId;
    private String customerName;
    private String shippingAddress;
    private BigDecimal amount;
    private OrderStatus status;

    /** ID giao dịch thanh toán, được điền sau khi Payment thành công */
    private String transactionId;

    /** Thời điểm bắt đầu chờ Shipping phản hồi (dùng cho timeout 30s) */
    private Instant shippingStartedAt;

    private Instant createdAt;
    private Instant updatedAt;
}
