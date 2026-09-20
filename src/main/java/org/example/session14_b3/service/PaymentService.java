package org.example.session14_b3.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.session14_b3.event.*;
import org.example.session14_b3.model.Customer;
import org.example.session14_b3.model.Payment;
import org.example.session14_b3.model.PaymentStatus;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Payment Service – xử lý thanh toán và hoàn tiền trong Choreography Saga.
 *
 * Trách nhiệm:
 *  1. Nhận OrderCreated  → trừ tiền ví khách hàng.
 *     - Thành công → phát PaymentSuccess.
 *     - Thất bại   → phát PaymentFailed.
 *  2. Nhận CompensatePayment → hoàn tiền (refund) → phát RefundSuccess.
 *
 * Dữ liệu mock:
 *  - 2 khách hàng với số dư ví cố định (có thể mở rộng qua REST).
 *  - Địa chỉ "INVALID_ADDRESS" được dùng để kích hoạt ShippingFailed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${saga.exchange}")
    private String exchange;

    @Value("${saga.routing.payment-success}")
    private String rkPaymentSuccess;

    @Value("${saga.routing.payment-failed}")
    private String rkPaymentFailed;

    @Value("${saga.routing.refund-success}")
    private String rkRefundSuccess;

    /** In-memory wallet: customerId → Customer */
    private final Map<String, Customer> customerStore = new ConcurrentHashMap<>(Map.of(
            "CUST-001", Customer.builder()
                    .customerId("CUST-001")
                    .name("Nguyen Van A")
                    .walletBalance(new BigDecimal("5000000"))
                    .build(),
            "CUST-002", Customer.builder()
                    .customerId("CUST-002")
                    .name("Tran Thi B")
                    .walletBalance(new BigDecimal("500000"))
                    .build()
    ));

    /** In-memory payment store: transactionId → Payment */
    private final Map<String, Payment> paymentStore = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ //
    //  EVENT LISTENERS                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Bước 2 – Nhận OrderCreated, thực hiện trừ tiền.
     */
    @RabbitListener(queues = "${saga.queue.order-created}")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[PAYMENT] Received OrderCreatedEvent – orderId={}, customerId={}, amount={}",
                event.getOrderId(), event.getCustomerId(), event.getAmount());

        Customer customer = customerStore.get(event.getCustomerId());

        // Kiểm tra khách hàng tồn tại
        if (customer == null) {
            publishPaymentFailed(event.getOrderId(), event.getCustomerId(),
                    event.getAmount(), "Customer not found: " + event.getCustomerId());
            return;
        }

        // Kiểm tra số dư
        if (customer.getWalletBalance().compareTo(event.getAmount()) < 0) {
            publishPaymentFailed(event.getOrderId(), event.getCustomerId(),
                    event.getAmount(),
                    String.format("Insufficient balance: required=%s, available=%s",
                            event.getAmount(), customer.getWalletBalance()));
            return;
        }

        // Trừ tiền
        customer.setWalletBalance(customer.getWalletBalance().subtract(event.getAmount()));

        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();

        Payment payment = Payment.builder()
                .transactionId(transactionId)
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .amount(event.getAmount())
                .status(PaymentStatus.SUCCESS)
                .createdAt(now)
                .updatedAt(now)
                .build();
        paymentStore.put(transactionId, payment);

        log.info("[PAYMENT] ✅ Payment SUCCESS – txId={}, customerId={}, newBalance={}",
                transactionId, event.getCustomerId(), customer.getWalletBalance());

        PaymentSuccessEvent successEvent = PaymentSuccessEvent.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .transactionId(transactionId)
                .shippingAddress(event.getShippingAddress())
                .amount(event.getAmount())
                .paidAt(now)
                .build();

        rabbitTemplate.convertAndSend(exchange, rkPaymentSuccess, successEvent);
        log.info("[PAYMENT] Published PaymentSuccessEvent → orderId={}", event.getOrderId());
    }

    /**
     * Bước bù trừ – Nhận CompensatePayment, hoàn tiền cho khách hàng.
     */
    @RabbitListener(queues = "${saga.queue.compensate-payment}")
    public void onCompensatePayment(CompensatePaymentEvent event) {
        log.info("[PAYMENT] Received CompensatePaymentEvent – orderId={}, txId={}, reason={}",
                event.getOrderId(), event.getTransactionId(), event.getReason());

        // Tìm giao dịch gốc theo transactionId
        Payment originalPayment = paymentStore.get(event.getTransactionId());
        if (originalPayment == null) {
            // Thử tìm theo orderId
            originalPayment = paymentStore.values().stream()
                    .filter(p -> p.getOrderId().equals(event.getOrderId()))
                    .findFirst()
                    .orElse(null);
        }

        if (originalPayment == null) {
            log.error("[PAYMENT] No payment found for orderId={}, cannot refund.", event.getOrderId());
            return;
        }

        // Hoàn tiền
        Customer customer = customerStore.get(event.getCustomerId());
        if (customer != null) {
            customer.setWalletBalance(customer.getWalletBalance().add(event.getRefundAmount()));
            log.info("[PAYMENT] Refunded {} to customerId={}, newBalance={}",
                    event.getRefundAmount(), event.getCustomerId(), customer.getWalletBalance());
        }

        String refundTxId = "REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();

        // Cập nhật trạng thái giao dịch gốc
        originalPayment.setStatus(PaymentStatus.REFUNDED);
        originalPayment.setRefundTransactionId(refundTxId);
        originalPayment.setUpdatedAt(now);

        log.info("[PAYMENT] ✅ Refund SUCCESS – refundTxId={}, orderId={}", refundTxId, event.getOrderId());

        RefundSuccessEvent refundEvent = RefundSuccessEvent.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .refundTransactionId(refundTxId)
                .refundAmount(event.getRefundAmount())
                .refundedAt(now)
                .build();

        rabbitTemplate.convertAndSend(exchange, rkRefundSuccess, refundEvent);
        log.info("[PAYMENT] Published RefundSuccessEvent → orderId={}", event.getOrderId());
    }

    // ------------------------------------------------------------------ //
    //  PUBLIC API                                                           //
    // ------------------------------------------------------------------ //

    public Collection<Payment> getAllPayments() {
        return paymentStore.values();
    }

    public Customer getCustomer(String customerId) {
        return customerStore.get(customerId);
    }

    public Collection<Customer> getAllCustomers() {
        return customerStore.values();
    }

    // ------------------------------------------------------------------ //
    //  HELPERS                                                              //
    // ------------------------------------------------------------------ //

    private void publishPaymentFailed(String orderId, String customerId,
                                      BigDecimal amount, String reason) {
        log.warn("[PAYMENT] ❌ Payment FAILED – orderId={}, reason={}", orderId, reason);

        Payment failedPayment = Payment.builder()
                .transactionId("FAILED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .status(PaymentStatus.FAILED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        paymentStore.put(failedPayment.getTransactionId(), failedPayment);

        PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .reason(reason)
                .failedAt(Instant.now())
                .build();

        rabbitTemplate.convertAndSend(exchange, rkPaymentFailed, failedEvent);
        log.info("[PAYMENT] Published PaymentFailedEvent → orderId={}", orderId);
    }
}
