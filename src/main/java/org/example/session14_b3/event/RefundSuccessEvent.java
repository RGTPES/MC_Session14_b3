package org.example.session14_b3.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event phát ra khi Payment Service hoàn tiền thành công.
 * Order Service lắng nghe event này để hủy đơn hàng (CANCELLED).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundSuccessEvent implements Serializable {

    private String orderId;
    private String customerId;
    private String refundTransactionId;
    private BigDecimal refundAmount;
    private Instant refundedAt;
}
