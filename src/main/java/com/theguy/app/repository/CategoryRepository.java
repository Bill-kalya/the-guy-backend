package com.theguy.app.repository;

import com.theguy.app.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByParentIdOrderBySortOrderAsc(UUID parentId);
    List<Category> findByParentIdIsNullOrderBySortOrderAsc();
    List<Category> findByIsActiveTrueOrderBySortOrderAsc();
}