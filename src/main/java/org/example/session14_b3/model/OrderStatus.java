package org.example.session14_b3.model;

/**
 * Trạng thái vòng đời của một đơn hàng trong Choreography Saga.
 *
 * PENDING          → Đơn vừa được tạo, chờ thanh toán
 * PAYMENT_PROCESSING → Đã gửi OrderCreated, đang chờ Payment phản hồi
 * PAYMENT_SUCCESS  → Thanh toán thành công, chờ Shipping xử lý
 * SHIPPING_PROCESSING → Đã gửi PaymentSuccess, đang chờ Shipping phản hồi (có timeout 30s)
 * COMPLETED        → Vận đơn tạo thành công, saga kết thúc thành công
 * COMPENSATING     → Shipping thất bại, đang chờ hoàn tiền
 * CANCELLED        → Hoàn tiền xong, đơn hàng bị hủy (saga kết thúc thất bại)
 * PAYMENT_FAILED   → Thanh toán thất bại, đơn hủy ngay
 */
public enum OrderStatus {
    PENDING,
    PAYMENT_PROCESSING,
    PAYMENT_SUCCESS,
    SHIPPING_PROCESSING,
    COMPLETED,
    COMPENSATING,
    CANCELLED,
    PAYMENT_FAILED
}
