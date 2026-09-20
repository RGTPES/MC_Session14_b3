package org.example.session14_b3.controller;

import lombok.RequiredArgsConstructor;
import org.example.session14_b3.model.Customer;
import org.example.session14_b3.model.Payment;
import org.example.session14_b3.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

/**
 * REST Controller cho Payment Service.
 *
 * GET /api/payments          – Liệt kê tất cả giao dịch thanh toán
 * GET /api/payments/customers – Liệt kê ví của tất cả khách hàng
 * GET /api/payments/customers/{id} – Xem số dư ví một khách hàng
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Liệt kê tất cả giao dịch thanh toán / hoàn tiền. */
    @GetMapping
    public ResponseEntity<Collection<Payment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    /** Xem số dư ví của tất cả khách hàng. */
    @GetMapping("/customers")
    public ResponseEntity<Collection<Customer>> getAllCustomers() {
        return ResponseEntity.ok(paymentService.getAllCustomers());
    }

    /** Xem số dư ví của một khách hàng cụ thể. */
    @GetMapping("/customers/{customerId}")
    public ResponseEntity<Customer> getCustomer(@PathVariable String customerId) {
        Customer customer = paymentService.getCustomer(customerId);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(customer);
    }
}
