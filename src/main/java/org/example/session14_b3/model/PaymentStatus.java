package org.example.session14_b3.model;

/**
 * Trạng thái của một giao dịch thanh toán.
 *
 * PENDING   → Đang xử lý
 * SUCCESS   → Đã trừ tiền thành công
 * FAILED    → Thanh toán thất bại (số dư không đủ, lỗi...)
 * REFUNDED  → Đã hoàn tiền cho khách (bù trừ Saga)
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
