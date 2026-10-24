package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    @Modifying
    @Query("delete from IdempotencyKey k where k.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
