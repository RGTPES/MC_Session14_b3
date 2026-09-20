package org.example.session14_b3.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.session14_b3.event.ShippingFailedEvent;
import org.example.session14_b3.model.Order;
import org.example.session14_b3.model.OrderStatus;
import org.example.session14_b3.service.OrderService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Timeout Scheduler cho Shipping Service trong Choreography Saga.
 *
 * Vấn đề:
 *  Khi Shipping Service không phản hồi (chậm, crash, mạng lỗi),
 *  Order sẽ mắc kẹt ở trạng thái SHIPPING_PROCESSING mãi mãi.
 *
 * Giải pháp:
 *  Scheduler chạy mỗi 10 giây, quét tất cả đơn hàng đang SHIPPING_PROCESSING.
 *  Nếu đơn hàng đã ở trạng thái này quá `saga.shipping.timeout-seconds` giây
 *  (mặc định 30s) → tự động phát ShippingFailedEvent với timeout=true
 *  → OrderService nhận và bắt đầu luồng bù trừ.
 *
 * Flow:
 *  [SHIPPING_PROCESSING > 30s]
 *      → SagaTimeoutScheduler phát ShippingFailedEvent(timeout=true)
 *      → OrderService.onShippingFailed() → COMPENSATING
 *      → phát CompensatePaymentEvent
 *      → PaymentService hoàn tiền → RefundSuccess
 *      → OrderService → CANCELLED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaTimeoutScheduler {

    private final OrderService orderService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${saga.exchange}")
    private String exchange;

    @Value("${saga.routing.shipping-failed}")
    private String rkShippingFailed;

    @Value("${saga.shipping.timeout-seconds:30}")
    private long timeoutSeconds;

    /**
     * Chạy mỗi 10 giây để phát hiện các đơn hàng bị timeout ở bước Shipping.
     * fixedDelay đảm bảo interval tính từ sau khi lần chạy trước hoàn thành.
     */
    @Scheduled(fixedDelay = 10_000)
    public void checkShippingTimeout() {
        Instant cutoff = Instant.now().minus(timeoutSeconds, ChronoUnit.SECONDS);

        orderService.getAllOrders().stream()
                .filter(order -> order.getStatus() == OrderStatus.SHIPPING_PROCESSING)
                .filter(order -> order.getShippingStartedAt() != null)
                .filter(order -> order.getShippingStartedAt().isBefore(cutoff))
                .forEach(this::triggerTimeoutCompensation);
    }

    private void triggerTimeoutCompensation(Order order) {
        log.warn("[TIMEOUT] ⏰ Shipping timeout detected for orderId={} – started at {}, timeout={}s",
                order.getOrderId(), order.getShippingStartedAt(), timeoutSeconds);

        ShippingFailedEvent timeoutEvent = ShippingFailedEvent.builder()
                .orderId(order.getOrderId())
                .shippingAddress(order.getShippingAddress())
                .reason("Shipping service did not respond within " + timeoutSeconds + " seconds")
                .timeout(true)
                .failedAt(Instant.now())
                .build();

        rabbitTemplate.convertAndSend(exchange, rkShippingFailed, timeoutEvent);
        log.info("[TIMEOUT] Published ShippingFailedEvent(timeout=true) → orderId={}", order.getOrderId());
    }
}
