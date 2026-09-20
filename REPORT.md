# BÀI TẬP 3: THIẾT KẾ VŨ ĐIỆU CHOREOGRAPHY SAGA
## Quy trình đặt hàng: Order → Payment → Shipping

---

## 1. PHÂN TÍCH INPUT / OUTPUT

### 1.1 Input của toàn bộ quy trình

| Thông tin | Nguồn | Ví dụ |
|---|---|---|
| `customerId` | Client gửi lên API | `CUST-001` |
| `customerName` | Client gửi lên API | `Nguyen Van A` |
| `shippingAddress` | Client gửi lên API | `123 Le Loi, HCM` |
| `amount` | Client gửi lên API | `150000` (VNĐ) |
| Số dư ví khách hàng | PaymentService (mock in-memory) | CUST-001: 5.000.000 VNĐ |
| Danh sách địa chỉ hỗ trợ | ShippingService (mock rule) | Từ chối: `INVALID*`, `MARS`, v.v. |

### 1.2 Output trạng thái cuối của 3 Service

#### Kịch bản 1: Thành công hoàn toàn

| Service | Trạng thái cuối | Ghi chú |
|---|---|---|
| **Order** | `COMPLETED` | Đơn hàng hoàn tất |
| **Payment** | `SUCCESS` | Tiền đã bị trừ, không hoàn |
| **Shipping** | `CREATED` | Vận đơn có mã tracking |

#### Kịch bản 2: Shipping thất bại → Bù trừ

| Service | Trạng thái cuối | Ghi chú |
|---|---|---|
| **Order** | `CANCELLED` | Đơn bị hủy sau khi hoàn tiền |
| **Payment** | `REFUNDED` | Tiền đã hoàn lại đầy đủ |
| **Shipping** | `FAILED` | Vận đơn không tạo được |

#### Kịch bản 3: Payment thất bại

| Service | Trạng thái cuối | Ghi chú |
|---|---|---|
| **Order** | `PAYMENT_FAILED` | Đơn bị hủy ngay, không cần bù trừ |
| **Payment** | `FAILED` | Không trừ tiền |
| **Shipping** | _(không tham gia)_ | Không nhận event nào |

#### Kịch bản 4: Shipping Timeout (> 30 giây)

| Service | Trạng thái cuối | Ghi chú |
|---|---|---|
| **Order** | `CANCELLED` | SagaTimeoutScheduler kích hoạt bù trừ |
| **Payment** | `REFUNDED` | Hoàn tiền tự động |
| **Shipping** | _(không phản hồi)_ | Không phát event nào |

---

## 2. THIẾT KẾ LUỒNG XỬ LÝ

### 2.1 Các Event trong Saga

```
OrderCreatedEvent       – Order → [saga.exchange] → queue.order.created     → PaymentService
PaymentSuccessEvent     – Payment → [saga.exchange] → queue.payment.success  → OrderService + ShippingService
PaymentFailedEvent      – Payment → [saga.exchange] → queue.payment.failed   → OrderService
ShippingSuccessEvent    – Shipping → [saga.exchange] → queue.shipping.success → OrderService
ShippingFailedEvent     – Shipping → [saga.exchange] → queue.shipping.failed  → OrderService
CompensatePaymentEvent  – Order → [saga.exchange] → queue.compensate.payment → PaymentService
RefundSuccessEvent      – Payment → [saga.exchange] → queue.refund.success   → OrderService
```

### 2.2 State Machine của Order

```
PENDING
  └─ createOrder()
       └─► PAYMENT_PROCESSING ──[OrderCreated]──► PaymentService
                │
          [PaymentFailed]                    [PaymentSuccess]
                │                                   │
         PAYMENT_FAILED                    SHIPPING_PROCESSING ──[PaymentSuccess]──► ShippingService
                                                    │                    │
                                          [ShippingSuccess]      [ShippingFailed / Timeout]
                                                    │                    │
                                               COMPLETED           COMPENSATING ──[CompensatePayment]──► PaymentService
                                                                         │
                                                                  [RefundSuccess]
                                                                         │
                                                                    CANCELLED
```

---

## 3. LƯU ĐỒ (FLOWCHART)

### 3.1 Luồng Thành Công

```
Client
  │
  │  POST /api/orders { customerId, shippingAddress, amount }
  ▼
┌─────────────────────────────────────────────────────────────┐
│  ORDER SERVICE                                              │
│  1. Tạo Order (status = PAYMENT_PROCESSING)                 │
│  2. Publish ──► OrderCreatedEvent                           │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ queue.order.created
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  PAYMENT SERVICE                                            │
│  3. Nhận OrderCreatedEvent                                  │
│  4. Kiểm tra số dư ví khách hàng                            │
│  5. Trừ tiền thành công                                     │
│  6. Publish ──► PaymentSuccessEvent                         │
└─────────────────────────────────────────────────────────────┘
                          │
              ┌───────────┴───────────┐
              │ queue.payment.success │
    ┌─────────▼──────────┐  ┌────────▼───────────────────┐
    │   ORDER SERVICE    │  │    SHIPPING SERVICE         │
    │  7a. Nhận event    │  │  7b. Nhận PaymentSuccess    │
    │  Lưu transactionId │  │  8.  Kiểm tra địa chỉ       │
    │  → SHIPPING_       │  │  9.  Tạo vận đơn thành công │
    │    PROCESSING      │  │  10. Publish ──►            │
    └────────────────────┘  │      ShippingSuccessEvent   │
                            └────────────────────────────┘
                                          │
                                          │ queue.shipping.success
                                          ▼
                            ┌────────────────────────────┐
                            │   ORDER SERVICE            │
                            │  11. Nhận ShippingSuccess  │
                            │  12. status = COMPLETED ✅ │
                            └────────────────────────────┘
```

### 3.2 Luồng Bù Trừ – Shipping Thất Bại

```
(tiếp tục từ bước 8 – kiểm tra địa chỉ)
                            │
                    [Địa chỉ KHÔNG hỗ trợ]
                            │
┌───────────────────────────▼────────────────────────────────┐
│  SHIPPING SERVICE                                          │
│  8b. Kiểm tra → địa chỉ không được hỗ trợ                 │
│  9b. Publish ──► ShippingFailedEvent (timeout=false)       │
└────────────────────────────────────────────────────────────┘
                            │
                            │ queue.shipping.failed
                            ▼
┌────────────────────────────────────────────────────────────┐
│  ORDER SERVICE                                             │
│  10. Nhận ShippingFailedEvent                              │
│  11. status = COMPENSATING                                 │
│  12. Publish ──► CompensatePaymentEvent                    │
└────────────────────────────────────────────────────────────┘
                            │
                            │ queue.compensate.payment
                            ▼
┌────────────────────────────────────────────────────────────┐
│  PAYMENT SERVICE                                           │
│  13. Nhận CompensatePaymentEvent                           │
│  14. Hoàn tiền vào ví khách hàng                           │
│  15. Cập nhật Payment → REFUNDED                           │
│  16. Publish ──► RefundSuccessEvent                        │
└────────────────────────────────────────────────────────────┘
                            │
                            │ queue.refund.success
                            ▼
┌────────────────────────────────────────────────────────────┐
│  ORDER SERVICE                                             │
│  17. Nhận RefundSuccessEvent                               │
│  18. status = CANCELLED ❌                                 │
└────────────────────────────────────────────────────────────┘
```

### 3.3 Luồng Bù Trừ – Shipping Timeout (> 30 giây)

```
(tiếp tục từ bước 7b – Order đang ở SHIPPING_PROCESSING)

  ┌─────────────────────────────────────────────┐
  │  SHIPPING SERVICE                           │
  │  Address chứa "TIMEOUT" → không phát event  │
  │  (mô phỏng service không phản hồi)          │
  └─────────────────────────────────────────────┘

  Sau 30 giây...

  ┌─────────────────────────────────────────────┐
  │  SAGA TIMEOUT SCHEDULER (chạy mỗi 10s)     │
  │  Phát hiện SHIPPING_PROCESSING > 30s        │
  │  Publish ──► ShippingFailedEvent(timeout=true)│
  └─────────────────────────────────────────────┘
                      │
                      │ queue.shipping.failed
                      ▼
           (tiếp tục luồng bù trừ như 3.2)
           ORDER → COMPENSATING → CompensatePayment
           PAYMENT → REFUNDED → RefundSuccess
           ORDER → CANCELLED ❌
```

---

## 4. KIẾN TRÚC KỸ THUẬT

### 4.1 Cấu trúc Package

```
org.example.session14_b3/
├── Session14B3Application.java       # @SpringBootApplication + @EnableScheduling
├── config/
│   └── RabbitMQConfig.java           # Exchange, Queue, Binding, MessageConverter
├── controller/
│   ├── OrderController.java          # POST /api/orders, GET /api/orders/{id}
│   ├── PaymentController.java        # GET /api/payments, /customers
│   └── ShippingController.java       # GET /api/shipments
├── event/
│   ├── OrderCreatedEvent.java
│   ├── PaymentSuccessEvent.java
│   ├── PaymentFailedEvent.java
│   ├── ShippingSuccessEvent.java
│   ├── ShippingFailedEvent.java
│   ├── CompensatePaymentEvent.java
│   └── RefundSuccessEvent.java
├── model/
│   ├── Order.java + OrderStatus.java
│   ├── Payment.java + PaymentStatus.java
│   ├── Shipment.java + ShipmentStatus.java
│   └── Customer.java
├── scheduler/
│   └── SagaTimeoutScheduler.java     # @Scheduled – timeout 30s
└── service/
    ├── OrderService.java             # State machine + publishers + listeners
    ├── PaymentService.java           # Debit/Refund logic + listeners
    └── ShippingService.java          # Address validation + listeners
```

### 4.2 RabbitMQ Topology

```
                    ┌──────────────────────┐
                    │   saga.exchange       │
                    │   (TopicExchange)     │
                    └──────────┬───────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │          routing key │                       │
   order.created    payment.success    payment.failed
        │                │                  │
queue.order.created  queue.payment.success  queue.payment.failed
     [PaymentSvc]    [OrderSvc+ShippingSvc]    [OrderSvc]

   shipping.success   shipping.failed   compensate.payment   refund.success
        │                   │                  │                  │
queue.shipping.success  queue.shipping.failed  queue.compensate  queue.refund.success
    [OrderSvc]           [OrderSvc]           [PaymentSvc]        [OrderSvc]
```

### 4.3 Cơ chế Timeout

| Thành phần | Cấu hình | Mô tả |
|---|---|---|
| `SagaTimeoutScheduler` | `@Scheduled(fixedDelay=10_000)` | Chạy mỗi 10 giây |
| Ngưỡng timeout | `saga.shipping.timeout-seconds=30` | Cấu hình trong `application.properties` |
| Điều kiện kích hoạt | `status=SHIPPING_PROCESSING AND shippingStartedAt < now-30s` | Quét tất cả orders |
| Hành động | Phát `ShippingFailedEvent(timeout=true)` | Kích hoạt luồng bù trừ |

---

## 5. HƯỚNG DẪN CHẠY DỰ ÁN

### 5.1 Yêu cầu

- Java 21+
- Gradle 8+
- RabbitMQ (Docker khuyến nghị)

### 5.2 Khởi động RabbitMQ

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

RabbitMQ Management UI: http://localhost:15672 (guest/guest)

### 5.3 Build và chạy

```bash
./gradlew bootRun
```

### 5.4 Test các kịch bản qua API

**Kịch bản 1 – Thành công:**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "customerName": "Nguyen Van A",
    "shippingAddress": "123 Le Loi, HCM",
    "amount": 150000
  }'
```

**Kịch bản 2 – Shipping thất bại (bù trừ):**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "customerName": "Nguyen Van A",
    "shippingAddress": "INVALID_REMOTE_AREA",
    "amount": 150000
  }'
```

**Kịch bản 3 – Payment thất bại (số dư không đủ):**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-002",
    "customerName": "Tran Thi B",
    "shippingAddress": "456 Tran Hung Dao, HN",
    "amount": 9999999
  }'
```

**Kịch bản 4 – Shipping Timeout:**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "customerName": "Nguyen Van A",
    "shippingAddress": "TIMEOUT_ZONE_ABC",
    "amount": 100000
  }'
# Sau 30s → SagaTimeoutScheduler tự động kích hoạt bù trừ
```

**Kiểm tra kết quả:**
```bash
# Xem trạng thái đơn hàng
curl http://localhost:8080/api/orders

# Xem số dư ví khách hàng (xác nhận hoàn tiền)
curl http://localhost:8080/api/payments/customers

# Xem tất cả giao dịch thanh toán
curl http://localhost:8080/api/payments

# Xem vận đơn
curl http://localhost:8080/api/shipments
```

---

## 6. DỮ LIỆU MOCK BAN ĐẦU

### Khách hàng (wallet)

| customerId | name | walletBalance |
|---|---|---|
| CUST-001 | Nguyen Van A | 5.000.000 VNĐ |
| CUST-002 | Tran Thi B | 500.000 VNĐ |

### Quy tắc kiểm tra địa chỉ (ShippingService)

| Điều kiện | Kết quả |
|---|---|
| Địa chỉ chứa `INVALID` (không phân biệt hoa thường) | Từ chối → ShippingFailed |
| Địa chỉ chứa `TIMEOUT` | Không phản hồi → Timeout sau 30s |
| Địa chỉ là `DAO HUA ISLAND`, `MARS`, `NORTH POLE` | Từ chối → ShippingFailed |
| Mọi địa chỉ khác | Chấp nhận → ShippingSuccess |

---

## 7. SO SÁNH: CHOREOGRAPHY vs ORCHESTRATION SAGA

| Tiêu chí | Choreography (bài này) | Orchestration |
|---|---|---|
| Điều phối | Phân tán – mỗi service tự lắng nghe event | Tập trung – một Orchestrator điều khiển |
| Coupling | Loose coupling qua message broker | Tighter coupling với Orchestrator |
| Khả năng mở rộng | Dễ thêm service mới (chỉ subscribe event) | Cần sửa Orchestrator khi thêm service |
| Khó debug | Khó theo dõi luồng (phân tán) | Dễ theo dõi (luồng rõ ràng ở một chỗ) |
| Điểm lỗi duy nhất | Không có SPOF | Orchestrator là SPOF |
| Phù hợp | Saga đơn giản, ít bước | Saga phức tạp, nhiều điều kiện |

---

## 8. KẾT LUẬN

Bài tập đã thiết kế và hiện thực đầy đủ **Choreography Saga** cho quy trình đặt hàng gồm 3 service:

- **Luồng thành công**: Order → Payment → Shipping → COMPLETED (4 event, 3 state transition)
- **Luồng bù trừ Shipping**: ShippingFailed → CompensatePayment → RefundSuccess → CANCELLED (3 event bổ sung)
- **Luồng bù trừ Timeout**: SagaTimeoutScheduler tự động phát ShippingFailed sau 30s → cùng luồng bù trừ
- **Luồng Payment thất bại**: PaymentFailed → PAYMENT_FAILED (không cần bù trừ vì chưa trừ tiền)

Tính đúng đắn của Saga được đảm bảo bởi:
1. State machine nghiêm ngặt trong OrderService (chỉ xử lý khi đúng trạng thái)
2. Idempotent check (không bù trừ đơn không ở SHIPPING_PROCESSING)
3. Timeout scheduler phát hiện và xử lý service không phản hồi
