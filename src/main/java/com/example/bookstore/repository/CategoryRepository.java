package com.example.bookstore.repository;

import com.example.bookstore.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Repository collection categories. */
@Repository
public interface CategoryRepository extends MongoRepository<Category, Long> {

    Optional<Category> findByName(String name);

    Optional<Category> findBySlug(String slug);

    boolean existsByName(String name);

    List<Category> findByParentId(Long parentId);

    List<Category> findByIsActive(boolean isActive);

    /** Danh muc goc (cay danh muc) - dung cho $graphLookup o tang service. */
    List<Category> findByParentIdIsNull();
}
