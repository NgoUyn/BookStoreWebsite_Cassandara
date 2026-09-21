package com.example.bookstore.service;

import com.example.bookstore.dto.SubOrderSummaryResponse;
import com.example.bookstore.model.Order;
import com.example.bookstore.model.SubOrder;
import com.example.bookstore.model.User;
import com.example.bookstore.model.embedded.UserSnapshot;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.CartRepository;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.repository.SubOrderRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.service.mongo.MongoSequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Test quyen so huu don hang - phien ban MongoDB.
 *
 * <p>Thay doi so voi ban SQL:
 * - OrderService co them BookRepository + MongoSequenceService (cap Long id).
 * - Order/SubOrder luu SNAPSHOT buyer/seller (UserSnapshot) thay vi quan he JPA
 *   nen test dung {@code UserSnapshot.of(user)}.
 * - Order.shippingAddress nay la field long trong {@code shipping{}} => set bang
 *   setter {@code order.setShippingAddress(...)} (khong con trong builder).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceSecurityAndOwnershipTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SubOrderRepository subOrderRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MongoSequenceService sequenceService;

    @Mock
    private CouponService couponService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                userRepository,
                cartRepository,
                orderRepository,
                subOrderRepository,
                bookRepository,
                sequenceService,
                couponService,
                notificationService,
                rabbitTemplate
        );
    }

    private User buyer(Long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("x")
                .role(UserRole.BUYER)
                .build();
    }

    private User seller(Long id, String username, String shopName) {
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("x")
                .role(UserRole.SELLER)
                .shopName(shopName)
                .build();
    }

    @Test
    void getCurrentBuyerOrders_shouldReturnOrdersForBuyer() {
        User buyer = buyer(2L, "buyer");

        Order order = Order.builder()
                .id(10L)
                .buyerId(buyer.getId())
                .buyer(UserSnapshot.of(buyer))
                .totalAmount(100000.0)
                .build();
        order.setShippingAddress("HCM");

        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));
        when(orderRepository.findByBuyerOrderByCreatedAtDesc(buyer)).thenReturn(List.of(order));

        assertEquals(1, orderService.getCurrentBuyerOrders(2L).size());
    }

    @Test
    void updateSubOrderStatusForSeller_shouldUpdateStatus() {
        User owner = seller(33L, "seller-ok", "Shop OK");

        SubOrder subOrder = SubOrder.builder()
                .id(200L)
                .orderId(2L)
                .sellerId(owner.getId())
                .seller(UserSnapshot.of(owner))
                .status(OrderStatus.PROCESSING)
                .subTotal(210000.0)
                .build();

        when(userRepository.findById(33L)).thenReturn(Optional.of(owner));
        when(subOrderRepository.findById(200L)).thenReturn(Optional.of(subOrder));
        when(subOrderRepository.save(subOrder)).thenReturn(subOrder);

        SubOrderSummaryResponse response = orderService.updateSubOrderStatusForSeller(33L, 200L, OrderStatus.SHIPPING);

        assertEquals(OrderStatus.SHIPPING, response.getStatus());
        assertEquals(33L, response.getSellerId());
        assertEquals(2L, response.getOrderId());
    }

    @Test
    void cancelCurrentBuyerOrder_shouldCancelSubOrdersWhenOrderIsPending() {
        User buyer = buyer(44L, "buyer-ok");
        User shopOwner = seller(55L, "seller", "Shop");

        SubOrder subOrder = SubOrder.builder()
                .id(301L)
                .orderId(9L)
                .status(OrderStatus.PROCESSING)
                .sellerId(shopOwner.getId())
                .seller(UserSnapshot.of(shopOwner))
                .buyer(UserSnapshot.of(buyer))
                .subTotal(45000.0)
                .build();

        Order order = Order.builder()
                .id(9L)
                .buyerId(buyer.getId())
                .buyer(UserSnapshot.of(buyer))
                .totalAmount(45000.0)
                .subOrders(List.of(subOrder))
                .build();
        order.setShippingAddress("Hanoi");

        when(userRepository.findById(44L)).thenReturn(Optional.of(buyer));
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));

        orderService.cancelCurrentBuyerOrder(44L, 9L);

        assertEquals(OrderStatus.CANCELLED, subOrder.getStatus());
    }
}
