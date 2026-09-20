package org.example.session14_b3.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.session14_b3.event.*;
import org.example.session14_b3.model.Order;
import org.example.session14_b3.model.OrderStatus;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order Service – thành phần trung tâm của Choreography Saga.
 *
 * Trách nhiệm:
 *  1. Tạo đơn hàng (PENDING → PAYMENT_PROCESSING) và phát OrderCreated.
 *  2. Nhận ShippingSuccess  → cập nhật COMPLETED.
 *  3. Nhận PaymentFailed    → hủy đơn (PAYMENT_FAILED).
 *  4. Nhận ShippingFailed   → chuyển COMPENSATING, phát CompensatePayment.
 *  5. Nhận RefundSuccess    → hủy đơn (CANCELLED) – kết thúc bù trừ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${saga.exchange}")
    private String exchange;

    @Value("${saga.routing.order-created}")
    private String rkOrderCreated;

    @Value("${saga.routing.compensate-payment}")
    private String rkCompensatePayment;

    /** In-memory store: orderId → Order */
    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ //
    //  PUBLIC API                                                           //
    // ------------------------------------------------------------------ //

    /**
     * Tạo đơn hàng mới và khởi động Saga bằng cách phát OrderCreatedEvent.
     */
    public Order createOrder(String customerId, String customerName,
                             String shippingAddress, java.math.BigDecimal amount) {

        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Order order = Order.builder()
                .orderId(orderId)
                .customerId(customerId)
                .customerName(customerName)
                .shippingAddress(shippingAddress)
                .amount(amount)
                .status(OrderStatus.PAYMENT_PROCESSING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderStore.put(orderId, order);
        log.info("[ORDER] Created order {} for customer {} – amount={}", orderId, customerId, amount);

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(orderId)
                .customerId(customerId)
                .customerName(customerName)
                .shippingAddress(shippingAddress)
                .amount(amount)
                .createdAt(now)
                .build();

        rabbitTemplate.convertAndSend(exchange, rkOrderCreated, event);
        log.info("[ORDER] Published OrderCreatedEvent → orderId={}", orderId);

        return order;
    }

    /** Truy vấn đơn hàng theo ID. */
    public Order getOrder(String orderId) {
        return orderStore.get(orderId);
    }

    /** Liệt kê tất cả đơn hàng (dùng cho REST API). */
    public Collection<Order> getAllOrders() {
        return orderStore.values();
    }

    // ------------------------------------------------------------------ //
    //  EVENT LISTENERS                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Nhận PaymentFailed → hủy đơn hàng ngay (không cần bù trừ).
     */
    @RabbitListener(queues = "${saga.queue.payment-failed}")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.warn("[ORDER] Received PaymentFailedEvent – orderId={}, reason={}",
                event.getOrderId(), event.getReason());

        Order order = orderStore.get(event.getOrderId());
        if (order == null) {
            log.error("[ORDER] Unknown orderId={}", event.getOrderId());
            return;
        }

        updateStatus(order, OrderStatus.PAYMENT_FAILED);
        log.info("[ORDER] Order {} cancelled due to payment failure.", order.getOrderId());
    }

    /**
     * Nhận PaymentSuccess → cập nhật transactionId và chuyển SHIPPING_PROCESSING.
     * (ShippingService đã tự lắng nghe PaymentSuccess nên không cần relay ở đây,
     *  nhưng Order cần cập nhật transactionId để phục vụ bù trừ sau này.)
     */
    @RabbitListener(queues = "${saga.queue.payment-success}")
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        log.info("[ORDER] Received PaymentSuccessEvent – orderId={}, txId={}",
                event.getOrderId(), event.getTransactionId());

        Order order = orderStore.get(event.getOrderId());
        if (order == null) {
            log.error("[ORDER] Unknown orderId={}", event.getOrderId());
            return;
        }

        order.setTransactionId(event.getTransactionId());
        order.setShippingStartedAt(Instant.now());
        updateStatus(order, OrderStatus.SHIPPING_PROCESSING);
        log.info("[ORDER] Order {} is now SHIPPING_PROCESSING.", order.getOrderId());
    }

    /**
     * Nhận ShippingSuccess → hoàn tất đơn hàng (COMPLETED).
     */
    @RabbitListener(queues = "${saga.queue.shipping-success}")
    public void onShippingSuccess(ShippingSuccessEvent event) {
        log.info("[ORDER] Received ShippingSuccessEvent – orderId={}, tracking={}",
                event.getOrderId(), event.getTrackingNumber());

        Order order = orderStore.get(event.getOrderId());
        if (order == null) {
            log.error("[ORDER] Unknown orderId={}", event.getOrderId());
            return;
        }

        updateStatus(order, OrderStatus.COMPLETED);
        log.info("[ORDER] ✅ Order {} COMPLETED successfully. Tracking={}", order.getOrderId(), event.getTrackingNumber());
    }

    /**
     * Nhận ShippingFailed → chuyển COMPENSATING, phát CompensatePaymentEvent.
     */
    @RabbitListener(queues = "${saga.queue.shipping-failed}")
    public void onShippingFailed(ShippingFailedEvent event) {
        log.warn("[ORDER] Received ShippingFailedEvent – orderId={}, reason={}, timeout={}",
                event.getOrderId(), event.getReason(), event.isTimeout());

        Order order = orderStore.get(event.getOrderId());
        if (order == null) {
            log.error("[ORDER] Unknown orderId={}", event.getOrderId());
            return;
        }

        // Chỉ bù trừ nếu đơn đang ở trạng thái SHIPPING_PROCESSING
        if (order.getStatus() != OrderStatus.SHIPPING_PROCESSING) {
            log.warn("[ORDER] Order {} is in status {}, skipping compensation.", order.getOrderId(), order.getStatus());
            return;
        }

        updateStatus(order, OrderStatus.COMPENSATING);

        String reason = event.isTimeout() ? "SHIPPING_TIMEOUT" : "SHIPPING_FAILED";

        CompensatePaymentEvent compensate = CompensatePaymentEvent.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .transactionId(order.getTransactionId())
                .refundAmount(order.getAmount())
                .reason(reason)
                .requestedAt(Instant.now())
                .build();

        rabbitTemplate.convertAndSend(exchange, rkCompensatePayment, compensate);
        log.info("[ORDER] Published CompensatePaymentEvent – orderId={}, refund={}", order.getOrderId(), order.getAmount());
    }

    /**
     * Nhận RefundSuccess → hủy đơn hàng (CANCELLED) – kết thúc bù trừ Saga.
     */
    @RabbitListener(queues = "${saga.queue.refund-success}")
    public void onRefundSuccess(RefundSuccessEvent event) {
        log.info("[ORDER] Received RefundSuccessEvent – orderId={}, refundTx={}",
                event.getOrderId(), event.getRefundTransactionId());

        Order order = orderStore.get(event.getOrderId());
        if (order == null) {
            log.error("[ORDER] Unknown orderId={}", event.getOrderId());
            return;
        }

        updateStatus(order, OrderStatus.CANCELLED);
        log.info("[ORDER] ❌ Order {} CANCELLED after successful refund of {}.",
                order.getOrderId(), event.getRefundAmount());
    }

    // ------------------------------------------------------------------ //
    //  HELPERS                                                              //
    // ------------------------------------------------------------------ //

    private void updateStatus(Order order, OrderStatus newStatus) {
        OrderStatus old = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        log.debug("[ORDER] Status transition {} → {} for orderId={}", old, newStatus, order.getOrderId());
    }
}
