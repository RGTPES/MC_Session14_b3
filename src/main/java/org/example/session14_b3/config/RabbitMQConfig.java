package org.example.session14_b3.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khai báo toàn bộ Exchange, Queue và Binding cho Choreography Saga.
 *
 * Kiến trúc:
 *   1 TopicExchange "saga.exchange"
 *   7 Queue  ←→  7 Routing Key
 *
 * Luồng thành công:
 *   OrderCreated   → queue.order.created     → PaymentService
 *   PaymentSuccess → queue.payment.success   → ShippingService
 *   ShippingSuccess→ queue.shipping.success  → OrderService
 *
 * Luồng bù trừ:
 *   PaymentFailed  → queue.payment.failed    → OrderService
 *   ShippingFailed → queue.shipping.failed   → OrderService
 *   CompensatePay  → queue.compensate.payment→ PaymentService
 *   RefundSuccess  → queue.refund.success    → OrderService
 */
@Configuration
public class RabbitMQConfig {

    /* ------------------------------------------------------------------ */
    /*  Exchange name                                                        */
    /* ------------------------------------------------------------------ */
    @Value("${saga.exchange}")
    private String exchange;

    /* ------------------------------------------------------------------ */
    /*  Queue names                                                          */
    /* ------------------------------------------------------------------ */
    @Value("${saga.queue.order-created}")       private String qOrderCreated;
    @Value("${saga.queue.payment-success}")     private String qPaymentSuccess;
    @Value("${saga.queue.payment-failed}")      private String qPaymentFailed;
    @Value("${saga.queue.shipping-success}")    private String qShippingSuccess;
    @Value("${saga.queue.shipping-failed}")     private String qShippingFailed;
    @Value("${saga.queue.compensate-payment}")  private String qCompensatePayment;
    @Value("${saga.queue.refund-success}")      private String qRefundSuccess;

    /* ------------------------------------------------------------------ */
    /*  Routing keys                                                         */
    /* ------------------------------------------------------------------ */
    @Value("${saga.routing.order-created}")      private String rkOrderCreated;
    @Value("${saga.routing.payment-success}")    private String rkPaymentSuccess;
    @Value("${saga.routing.payment-failed}")     private String rkPaymentFailed;
    @Value("${saga.routing.shipping-success}")   private String rkShippingSuccess;
    @Value("${saga.routing.shipping-failed}")    private String rkShippingFailed;
    @Value("${saga.routing.compensate-payment}") private String rkCompensatePayment;
    @Value("${saga.routing.refund-success}")     private String rkRefundSuccess;

    /* ------------------------------------------------------------------ */
    /*  Exchange                                                             */
    /* ------------------------------------------------------------------ */
    @Bean
    public TopicExchange sagaExchange() {
        return ExchangeBuilder.topicExchange(exchange).durable(true).build();
    }

    /* ------------------------------------------------------------------ */
    /*  Queues (durable = survive broker restart)                            */
    /* ------------------------------------------------------------------ */
    @Bean public Queue queueOrderCreated()      { return QueueBuilder.durable(qOrderCreated).build(); }
    @Bean public Queue queuePaymentSuccess()    { return QueueBuilder.durable(qPaymentSuccess).build(); }
    @Bean public Queue queuePaymentFailed()     { return QueueBuilder.durable(qPaymentFailed).build(); }
    @Bean public Queue queueShippingSuccess()   { return QueueBuilder.durable(qShippingSuccess).build(); }
    @Bean public Queue queueShippingFailed()    { return QueueBuilder.durable(qShippingFailed).build(); }
    @Bean public Queue queueCompensatePayment() { return QueueBuilder.durable(qCompensatePayment).build(); }
    @Bean public Queue queueRefundSuccess()     { return QueueBuilder.durable(qRefundSuccess).build(); }

    /* ------------------------------------------------------------------ */
    /*  Bindings                                                             */
    /* ------------------------------------------------------------------ */
    @Bean public Binding bindingOrderCreated(Queue queueOrderCreated, TopicExchange sagaExchange) {
        return BindingBuilder.bind(queueOrderCreated).to(sagaExchange).with(rkOrderCreated);
    }
    @Bean public Binding bindingPaymentSuccess(Queue queuePaymentSuccess, TopicExchange sagaExchange) {
        return BindingBuilder.bind(queuePaymentSuccess).to(sagaExchange).with(rkPaymentSuccess);
    }
    @Bean public Binding bindingPaymentFailed(Queue queuePaymentFailed, TopicExchange sagaExchange) {
        return BindingBuilder.bind(queuePaymentFailed).to(sagaExchange).with(rkPaymentFailed);
    }
    @Bean public Binding bindingShippingSuccess(Queue queueShippingSuccess, TopicExchange sagaExchange) {
        return BindingBuilder.bind(queueShippingSuccess).to(sagaExchange).with(rkShippingSuccess);
    }
    @Bean public Binding bindingShippingFailed(Queue queueShippingFailed, TopicExchange sagaExchange) {
        return BindingBuilder.bind(queueShippingFailed).to(sagaExchange).with(rkShippingFailed);
    }
    @Bean public Binding bindingCompensatePayment(Queue queueCompensatePayment, TopicExchange sagaExchange) {
        return BindingBuilder.bind(queueCompensatePayment).to(sagaExchange).with(rkCompensatePayment);
    }
    @Bean public Binding bindingRefundSuccess(Queue queueRefundSuccess, TopicExchange sagaExchange) {
        return BindingBuilder.bind(queueRefundSuccess).to(sagaExchange).with(rkRefundSuccess);
    }

    /* ------------------------------------------------------------------ */
    /*  JSON message converter + RabbitTemplate                             */
    /* ------------------------------------------------------------------ */
    @Bean
    public MessageConverter jsonMessageConverter() {
        // Spring AMQP 4.x: JacksonJsonMessageConverter sử dụng Jackson 3
        // hỗ trợ Java Time types (Instant) natively qua jackson-databind 3.x
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
