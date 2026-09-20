package org.example.session14_b3.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event phát ra khi Payment Service không thể trừ tiền (số dư không đủ, lỗi hệ thống...).
 * Order Service lắng nghe event này để hủy đơn hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent implements Serializable {

    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private String reason;
    private Instant failedAt;
}
