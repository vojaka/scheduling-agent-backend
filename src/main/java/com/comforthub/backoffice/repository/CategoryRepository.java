package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    /** All categories (main + sub) for a company, ordered by sort_order. */
    List<CategoryEntity> findByCompanyIdOrderBySortOrderAscNameAsc(String companyId);
}
