package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<CompanyEntity, String> {

    @Query(value = "SELECT * FROM companies WHERE (is_deleted IS NOT TRUE) AND (:bubbleId = ANY(owners) OR :bubbleId = ANY(workers))", nativeQuery = true)
    List<CompanyEntity> findAvailableCompanies(@Param("bubbleId") String bubbleId);
}
