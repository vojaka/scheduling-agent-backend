package com.comforthub.backoffice.repository;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface BubbleUserRepository extends JpaRepository<BubbleUserEntity, String> {}
