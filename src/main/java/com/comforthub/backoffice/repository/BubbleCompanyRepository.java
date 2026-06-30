package com.comforthub.backoffice.repository;
import com.comforthub.backoffice.model.entity.BubbleCompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BubbleCompanyRepository extends JpaRepository<BubbleCompanyEntity, String> {}
