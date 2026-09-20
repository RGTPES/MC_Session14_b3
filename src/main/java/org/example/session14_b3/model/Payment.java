package org.example.session14_b3.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Đại diện cho một giao dịch thanh toán hoặc hoàn tiền.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private String transactionId;
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private PaymentStatus status;

    /** ID giao dịch hoàn tiền (chỉ có khi status = REFUNDED) */
    private String refundTransactionId;

    private Instant createdAt;
    private Instant updatedAt;
}
