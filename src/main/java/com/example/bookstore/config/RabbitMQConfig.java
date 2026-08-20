package com.example.bookstore.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.recommendation.queue}")
    private String queueName;

    @Value("${app.rabbitmq.recommendation.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.recommendation.routing-key}")
    private String routingKey;

    // 1. Tạo hàng đợi (Queue)
    @Bean
    public Queue recommendationQueue() {
        return new Queue(queueName, true); // true = Giữ lại queue khi server tắt
    }

    // 2. Tạo điểm phân phối (Exchange)
    @Bean
    public DirectExchange recommendationExchange() {
        return new DirectExchange(exchangeName);
    }

    // 3. Gắn Queue vào Exchange
    @Bean
    public Binding bindingRecommendation(Queue recommendationQueue, DirectExchange recommendationExchange) {
        return BindingBuilder.bind(recommendationQueue).to(recommendationExchange).with(routingKey);
    }

    // 4. Cấu hình chuyển đổi dữ liệu Java Object sang JSON để gửi qua RabbitMQ
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}