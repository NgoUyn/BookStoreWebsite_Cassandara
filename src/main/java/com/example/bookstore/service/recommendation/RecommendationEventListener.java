package com.example.bookstore.service.recommendation;

import com.example.bookstore.dto.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecommendationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationEventListener.class);

    @Autowired
    private RecommendationRedisService redisService;

    @RabbitListener(queues = "${app.rabbitmq.recommendation.queue:recommendation_queue}")
    public void handleOrderCompletedEvent(OrderCompletedEvent event) {
        logger.info("[Consumer] Nhận được đơn hàng {} có {} sách. Tiến hành cập nhật Redis...",
                event.getOrderId(), event.getBookIds().size());

        List<Long> bookIds = event.getBookIds();

        // Tạo các cặp tổ hợp chập 2 từ danh sách sách (A-B, B-C, A-C...)
        for (int i = 0; i < bookIds.size(); i++) {
            for (int j = i + 1; j < bookIds.size(); j++) {
                Long bookA = bookIds.get(i);
                Long bookB = bookIds.get(j);

                // Tránh việc mua 2 cuốn giống hệt nhau
                if (!bookA.equals(bookB)) {
                    redisService.incrementCoOccurrence(bookA, bookB);
                }
            }
        }
        logger.info("[Consumer] Cập nhật điểm Real-time lên Redis thành công!");
    }
}