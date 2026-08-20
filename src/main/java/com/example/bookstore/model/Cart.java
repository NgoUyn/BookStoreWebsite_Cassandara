package com.example.bookstore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts")
@Data
@ToString(exclude = {"buyer", "items"})
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1 giỏ hàng thuộc sở hữu của 1 người bán
    @OneToOne
    @JoinColumn(name = "buyer_id", nullable = false, unique = true)
    private User buyer;

    //1 giỏ hàng có thể có nhiều item
    //orphanRemoval = true : use to đảm bảo item sẽ bị xóa khỏi list , cũng bị xóa khỏi database
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();
}
