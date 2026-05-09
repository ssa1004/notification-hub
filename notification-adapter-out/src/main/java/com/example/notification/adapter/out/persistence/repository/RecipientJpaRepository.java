package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.RecipientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientJpaRepository extends JpaRepository<RecipientEntity, String> {}
