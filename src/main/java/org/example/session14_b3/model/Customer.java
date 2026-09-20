package org.example.session14_b3.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Đại diện cho dữ liệu khách hàng (lưu in-memory, mock wallet).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    private String customerId;
    private String name;
    /** Số dư ví điện tử */
    private BigDecimal walletBalance;
}
