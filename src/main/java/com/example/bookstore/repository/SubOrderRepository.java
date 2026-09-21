package com.example.bookstore.repository;

import com.example.bookstore.model.Order;
import com.example.bookstore.model.SubOrder;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FACADE truy vấn {@code SubOrder} tren AGGREGATE {@code orders}.
 *
 * <p>SQL Server truoc day co bang rieng {@code sub_orders} + repository rieng.
 * Sau khi gop vao document {@code orders}, lop nay giu LAI API cua repository cu
 * (findBySeller, save, existsByIdAndSellerId...) nhung hien thuc bang
 * {@code OrderRepository} + Aggregation ({@code $unwind} + {@code $replaceRoot})
 * => tang service/controller khong phai viet lai.</p>
 *
 * <p>Moi {@code save(subOrder)} chi la MOT update tren document order chua no
 * (khong con transaction nhieu bang nhu ban SQL).</p>
 */
@Repository
@RequiredArgsConstructor
public class SubOrderRepository {

    private final OrderRepository orderRepository;

    public Optional<SubOrder> findById(Long subOrderId) {
        if (subOrderId == null) {
            return Optional.empty();
        }
        return orderRepository.findFirstBySubOrdersId(subOrderId)
                .flatMap(order -> order.getSubOrders().stream()
                        .filter(so -> subOrderId.equals(so.getId()))
                        .findFirst()
                        .map(so -> attachOrderInfo(so, order)));
    }

    public List<SubOrder> findBySeller(User seller) {
        return findBySellerOrderByIdDesc(seller);
    }

    public List<SubOrder> findBySellerOrderByIdDesc(User seller) {
        if (seller == null || seller.getId() == null) {
            return List.of();
        }
        return orderRepository.findSubOrdersBySeller(seller.getId(), null, PageRequest.of(0, 500));
    }

    public List<SubOrder> findBySellerAndStatus(User seller, OrderStatus status) {
        if (seller == null || seller.getId() == null) {
            return List.of();
        }
        return orderRepository.findSubOrdersBySeller(seller.getId(), status, PageRequest.of(0, 500));
    }

    public List<SubOrder> findBySellerAndStatusOrdered(User seller, OrderStatus status) {
        return findBySellerAndStatus(seller, status);
    }

    public List<SubOrder> findBySellerAndBuyerNameContaining(User seller, String buyerName) {
        if (seller == null || seller.getId() == null) {
            return List.of();
        }
        return orderRepository.searchSubOrdersByBuyerName(seller.getId(), buyerName, PageRequest.of(0, 100));
    }

    public Page<SubOrder> findBySellerWithFilters(User seller,
                                                 OrderStatus status,
                                                 LocalDateTime createdFrom,
                                                 LocalDateTime createdTo,
                                                 Double minPrice,
                                                 Double maxPrice,
                                                 Pageable pageable) {
        return orderRepository.findSubOrdersBySellerWithFilters(seller.getId(), status,
                createdFrom, createdTo, minPrice, maxPrice, pageable);
    }

    public boolean existsByIdAndSellerId(Long subOrderId, Long sellerId) {
        return orderRepository.existsBySubOrdersSellerIdAndSubOrderId(sellerId, subOrderId);
    }

    public boolean existsByIdAndBuyerId(Long subOrderId, Long buyerId) {
        return orderRepository.existsBySubOrderIdAndBuyerId(subOrderId, buyerId);
    }

    public List<Long> findDistinctBuyerIdsBySeller(User seller) {
        if (seller == null || seller.getId() == null) {
            return List.of();
        }
        return orderRepository.findDistinctBuyerIdsBySellerId(seller.getId());
    }

    public long countByBuyer(User buyer) {
        if (buyer == null || buyer.getId() == null) {
            return 0L;
        }
        long count = 0L;
        for (Order order : orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId())) {
            if (order.getSubOrders() != null) {
                count += order.getSubOrders().size();
            }
        }
        return count;
    }

    public long countCancelledByBuyer(User buyer) {
        if (buyer == null || buyer.getId() == null) {
            return 0L;
        }
        long count = 0L;
        for (Order order : orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId())) {
            if (order.getSubOrders() != null) {
                count += order.getSubOrders().stream()
                        .filter(so -> so.getStatus() == OrderStatus.CANCELLED)
                        .count();
            }
        }
        return count;
    }

    /**
     * Luu 1 subOrder: tim order chua no (theo orderId hoac subOrderId) roi thay
     * the trong mang {@code subOrders} va ghi lai document order (1 lan ghi).
     */
    public SubOrder save(SubOrder subOrder) {
        Order order = resolveOwnerOrder(subOrder);
        if (order == null) {
            return subOrder;
        }
        replaceSubOrder(order, subOrder);
        orderRepository.save(order);
        return subOrder;
    }

    public List<SubOrder> saveAll(List<SubOrder> subOrders) {
        if (subOrders == null || subOrders.isEmpty()) {
            return subOrders;
        }
        for (SubOrder subOrder : subOrders) {
            save(subOrder);
        }
        return subOrders;
    }

    private Order resolveOwnerOrder(SubOrder subOrder) {
        if (subOrder.getOrderId() != null) {
            Order order = orderRepository.findById(subOrder.getOrderId()).orElse(null);
            if (order != null) {
                return order;
            }
        }
        if (subOrder.getId() != null) {
            return orderRepository.findFirstBySubOrdersId(subOrder.getId()).orElse(null);
        }
        return null;
    }

    private void replaceSubOrder(Order order, SubOrder subOrder) {
        if (order.getSubOrders() == null) {
            order.setSubOrders(new ArrayList<>());
        }
        List<SubOrder> list = order.getSubOrders();
        for (int i = 0; i < list.size(); i++) {
            SubOrder current = list.get(i);
            if (current.getId() != null && current.getId().equals(subOrder.getId())) {
                subOrder.setOrderId(order.getId());
                list.set(i, subOrder);
                return;
            }
        }
        list.add(subOrder);
    }

    /** Bo sung orderId/buyer snapshot cho subOrder khi tra ve tang service. */
    private SubOrder attachOrderInfo(SubOrder subOrder, Order order) {
        subOrder.setOrderId(order.getId());
        if (subOrder.getBuyer() == null) {
            subOrder.setBuyer(order.getBuyer());
        }
        return subOrder;
    }
}

