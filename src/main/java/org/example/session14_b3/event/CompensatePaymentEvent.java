package org.example.session14_b3.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event bù trừ: Order Service phát ra sau khi nhận ShippingFailed.
 * Payment Service lắng nghe event này để thực hiện hoàn tiền (refund).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensatePaymentEvent implements Serializable {

    private String orderId;
    private String customerId;
    private String transactionId;
    private BigDecimal refundAmount;
    /** Nguyên nhân cần hoàn tiền, ví dụ: "SHIPPING_FAILED", "SHIPPING_TIMEOUT" */
    private String reason;
    private Instant requestedAt;
}
