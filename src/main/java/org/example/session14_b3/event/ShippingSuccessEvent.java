package org.example.session14_b3.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event phát ra khi Shipping Service tạo vận đơn thành công.
 * Order Service lắng nghe event này để cập nhật trạng thái COMPLETED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingSuccessEvent implements Serializable {

    private String orderId;
    private String trackingNumber;
    private String shippingAddress;
    private String carrier;
    private Instant shippedAt;
}
