package org.example.session14_b3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động ứng dụng Choreography Saga Demo.
 *
 * Bao gồm 3 service trong cùng một Spring Boot app (monolith-style demo):
 *  - OrderService   : quản lý vòng đời đơn hàng
 *  - PaymentService : trừ tiền và hoàn tiền
 *  - ShippingService: kiểm tra địa chỉ và tạo vận đơn
 *
 * Giao tiếp qua RabbitMQ (TopicExchange "saga.exchange").
 * @EnableScheduling kích hoạt SagaTimeoutScheduler (30s timeout cho Shipping).
 */
@SpringBootApplication
@EnableScheduling
public class Session14B3Application {

    public static void main(String[] args) {
        SpringApplication.run(Session14B3Application.class, args);
    }
}
