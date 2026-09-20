package org.example.session14_b3.model;

/**
 * Trạng thái của một vận đơn.
 *
 * PROCESSING  → Đang kiểm tra địa chỉ và tạo vận đơn
 * CREATED     → Vận đơn tạo thành công
 * FAILED      → Không tạo được vận đơn (địa chỉ không hỗ trợ, lỗi carrier...)
 */
public enum ShipmentStatus {
    PROCESSING,
    CREATED,
    FAILED
}
