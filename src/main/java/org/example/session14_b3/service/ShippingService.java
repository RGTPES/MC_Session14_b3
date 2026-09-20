package org.example.session14_b3.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.session14_b3.event.PaymentSuccessEvent;
import org.example.session14_b3.event.ShippingFailedEvent;
import org.example.session14_b3.event.ShippingSuccessEvent;
import org.example.session14_b3.model.Shipment;
import org.example.session14_b3.model.ShipmentStatus;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shipping Service – kiểm tra địa chỉ giao hàng và tạo vận đơn.
 *
 * Trách nhiệm:
 *  1. Nhận PaymentSuccess → kiểm tra địa chỉ:
 *     - Địa chỉ hợp lệ   → tạo vận đơn → phát ShippingSuccess.
 *     - Địa chỉ không hỗ trợ → phát ShippingFailed.
 *
 * Logic kiểm tra địa chỉ (mock):
 *  - Các địa chỉ chứa keyword "INVALID" hoặc nằm trong danh sách
 *    UNSUPPORTED_ADDRESSES → không được hỗ trợ.
 *  - Địa chỉ chứa "TIMEOUT" → mô phỏng slow processing (dùng cho test timeout).
 *    Thực tế sẽ không phát event → SagaTimeoutScheduler sẽ kích hoạt bù trừ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${saga.exchange}")
    private String exchange;

    @Value("${saga.routing.shipping-success}")
    private String rkShippingSuccess;

    @Value("${saga.routing.shipping-failed}")
    private String rkShippingFailed;

    /** Danh sách địa chỉ không được hỗ trợ (mock) */
    private static final Set<String> UNSUPPORTED_ADDRESSES = Set.of(
            "DAO HUA ISLAND",
            "MARS",
            "NORTH POLE"
    );

    /** In-memory shipment store: shipmentId → Shipment */
    private final Map<String, Shipment> shipmentStore = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ //
    //  EVENT LISTENERS                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Bước 3 – Nhận PaymentSuccess, kiểm tra địa chỉ và tạo vận đơn.
     */
    @RabbitListener(queues = "${saga.queue.payment-success}")
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        log.info("[SHIPPING] Received PaymentSuccessEvent – orderId={}, address={}",
                event.getOrderId(), event.getShippingAddress());

        String address = event.getShippingAddress();

        // Mô phỏng TIMEOUT: không xử lý, SagaTimeoutScheduler sẽ xử lý sau 30s
        if (address != null && address.toUpperCase().contains("TIMEOUT")) {
            log.warn("[SHIPPING] ⏳ Address contains TIMEOUT keyword – simulating slow/no response for orderId={}",
                    event.getOrderId());
            // Không phát event → timeout scheduler sẽ kích hoạt sau 30 giây
            return;
        }

        // Kiểm tra địa chỉ
        if (!isAddressSupported(address)) {
            log.warn("[SHIPPING] ❌ Address not supported: '{}' – orderId={}", address, event.getOrderId());

            Shipment failedShipment = Shipment.builder()
                    .shipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .orderId(event.getOrderId())
                    .shippingAddress(address)
                    .status(ShipmentStatus.FAILED)
                    .failureReason("Address not supported: " + address)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            shipmentStore.put(failedShipment.getShipmentId(), failedShipment);

            ShippingFailedEvent failedEvent = ShippingFailedEvent.builder()
                    .orderId(event.getOrderId())
                    .shippingAddress(address)
                    .reason("Address not supported: " + address)
                    .timeout(false)
                    .failedAt(Instant.now())
                    .build();

            rabbitTemplate.convertAndSend(exchange, rkShippingFailed, failedEvent);
            log.info("[SHIPPING] Published ShippingFailedEvent → orderId={}", event.getOrderId());
            return;
        }

        // Tạo vận đơn thành công
        String trackingNumber = "VD" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String carrier = "GIAO HANG NHANH";
        Instant now = Instant.now();

        Shipment shipment = Shipment.builder()
                .shipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .orderId(event.getOrderId())
                .trackingNumber(trackingNumber)
                .shippingAddress(address)
                .carrier(carrier)
                .status(ShipmentStatus.CREATED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        shipmentStore.put(shipment.getShipmentId(), shipment);

        log.info("[SHIPPING] ✅ Shipment created – orderId={}, tracking={}", event.getOrderId(), trackingNumber);

        ShippingSuccessEvent successEvent = ShippingSuccessEvent.builder()
                .orderId(event.getOrderId())
                .trackingNumber(trackingNumber)
                .shippingAddress(address)
                .carrier(carrier)
                .shippedAt(now)
                .build();

        rabbitTemplate.convertAndSend(exchange, rkShippingSuccess, successEvent);
        log.info("[SHIPPING] Published ShippingSuccessEvent → orderId={}", event.getOrderId());
    }

    // ------------------------------------------------------------------ //
    //  PUBLIC API                                                           //
    // ------------------------------------------------------------------ //

    public Collection<Shipment> getAllShipments() {
        return shipmentStore.values();
    }

    // ------------------------------------------------------------------ //
    //  HELPERS                                                              //
    // ------------------------------------------------------------------ //

    /**
     * Kiểm tra xem địa chỉ giao hàng có được hỗ trợ hay không.
     * Logic mock: chứa "INVALID" hoặc nằm trong danh sách UNSUPPORTED_ADDRESSES.
     */
    private boolean isAddressSupported(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        String upper = address.toUpperCase();
        if (upper.contains("INVALID")) {
            return false;
        }
        for (String unsupported : UNSUPPORTED_ADDRESSES) {
            if (upper.contains(unsupported)) {
                return false;
            }
        }
        return true;
    }
}
