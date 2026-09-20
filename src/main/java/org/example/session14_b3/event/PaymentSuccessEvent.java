package org.example.session14_b3.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event phát ra khi Payment Service trừ tiền thành công.
 * Shipping Service lắng nghe event này để tạo vận đơn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEvent implements Serializable {

    private String orderId;
    private String customerId;
    private String transactionId;
    private String shippingAddress;
    private BigDecimal amount;
    private Instant paidAt;
}
