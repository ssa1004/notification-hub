package com.example.notification.adapter.out.persistence.repository

import com.example.notification.adapter.out.persistence.entity.UserPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceJpaRepository : JpaRepository<UserPreferenceEntity, String>
