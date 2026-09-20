package org.example.session14_b3.controller;

import lombok.RequiredArgsConstructor;
import org.example.session14_b3.model.Order;
import org.example.session14_b3.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * REST Controller cho Order Service.
 *
 * POST /api/orders          – Tạo đơn hàng mới, khởi động Saga
 * GET  /api/orders          – Liệt kê tất cả đơn hàng
 * GET  /api/orders/{id}     – Xem trạng thái một đơn hàng
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Tạo đơn hàng mới.
     *
     * Request body (JSON):
     * {
     *   "customerId":      "CUST-001",
     *   "customerName":    "Nguyen Van A",
     *   "shippingAddress": "123 Le Loi, HCM",   // dùng "INVALID" để test bù trừ
     *   "amount":          150000                // phải <= số dư ví
     * }
     *
     * Gợi ý test:
     *  - Luồng thành công  : shippingAddress = "123 Le Loi, HCM"
     *  - Shipping thất bại : shippingAddress = "INVALID_AREA"
     *  - Payment thất bại  : amount > 500000 với CUST-002
     *  - Timeout           : shippingAddress = "TIMEOUT_ZONE"
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Map<String, Object> body) {
        String customerId     = (String) body.get("customerId");
        String customerName   = (String) body.get("customerName");
        String shippingAddress = (String) body.get("shippingAddress");
        BigDecimal amount     = new BigDecimal(body.get("amount").toString());

        Order order = orderService.createOrder(customerId, customerName, shippingAddress, amount);
        return ResponseEntity.ok(order);
    }

    /** Lấy tất cả đơn hàng (để kiểm tra trạng thái cuối). */
    @GetMapping
    public ResponseEntity<Collection<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    /** Lấy trạng thái một đơn hàng. */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }
}
