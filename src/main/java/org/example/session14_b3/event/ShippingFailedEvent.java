package org.example.session14_b3.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event phát ra khi Shipping Service không thể tạo vận đơn
 * (địa chỉ không hỗ trợ, timeout, lỗi hệ thống...).
 * Order Service lắng nghe và phát tiếp CompensatePaymentEvent để bù trừ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingFailedEvent implements Serializable {

    private String orderId;
    private String shippingAddress;
    private String reason;
    /** true nếu lỗi do timeout (không phải lỗi nghiệp vụ) */
    private boolean timeout;
    private Instant failedAt;
}
