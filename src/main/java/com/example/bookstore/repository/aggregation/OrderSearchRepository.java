package com.example.bookstore.repository.aggregation;

import com.example.bookstore.model.Order;
import com.example.bookstore.model.SubOrder;
import com.example.bookstore.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Truy vấn nang cao cho orders/subOrders bang Aggregation pipeline.
 *
 * <p>Tuong duong cac method JOIN FETCH cua {@code SubOrderRepository} ban cu -
 * nay chi can $unwind {@code subOrders} tren 1 collection.</p>
 */
public interface OrderSearchRepository {

    /** Don cua buyer voi bo loc (thoi gian + khoang tien). */
    Page<Order> findBuyerOrdersWithFilters(Long buyerId,
                                          LocalDateTime createdFrom,
                                          LocalDateTime createdTo,
                                          Double minAmount,
                                          Double maxAmount,
                                          Pageable pageable);

    /** Sub-order cua seller voi bo loc - tra ve SubOrder da tach khoi document cha. */
    Page<SubOrder> findSubOrdersBySellerWithFilters(Long sellerId,
                                                    OrderStatus status,
                                                    LocalDateTime createdFrom,
                                                    LocalDateTime createdTo,
                                                    Double minAmount,
                                                    Double maxAmount,
                                                    Pageable pageable);

    List<SubOrder> findSubOrdersBySeller(Long sellerId, OrderStatus status, Pageable pageable);

    /** Seller tim sub-order theo ten nguoi mua (thay LOWER(buyer.username) LIKE). */
    List<SubOrder> searchSubOrdersByBuyerName(Long sellerId, String buyerName, Pageable pageable);

    /** Top sach ban chay tinh tu orders (thay GROUP BY order_items). */
    List<java.util.Map> aggregateTopSellingBooks(LocalDateTime fromDate, int limit);

    /** Doanh thu theo ngay/trang thai cho dashboard - 1 request, nhieu nhanh ($facet). */
    java.util.Map<String, Object> sellerDashboardFacet(Long sellerId, LocalDateTime fromDate);
}
