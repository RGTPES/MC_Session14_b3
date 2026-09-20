package org.example.session14_b3.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event phát ra khi Order Service tạo đơn hàng mới (trạng thái PENDING).
 * Payment Service lắng nghe event này để thực hiện thanh toán.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent implements Serializable {

    private String orderId;
    private String customerId;
    private String customerName;
    private String shippingAddress;
    private BigDecimal amount;
    private Instant createdAt;
}
