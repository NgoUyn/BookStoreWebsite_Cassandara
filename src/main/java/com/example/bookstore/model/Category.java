package com.example.bookstore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.util.List;

@Entity
@Table(name = "category")
@Data
@ToString(exclude = {"books"})
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "NVARCHAR(255)")
    private String name;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    // Quan hệ 1-Nhiều: 1 Thể loại có nhiều Cuốn sách
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    @JsonIgnore // Tránh lỗi lặp vòng vô tận khi trả về dữ liệu JSON
    private List<Book> books;
}