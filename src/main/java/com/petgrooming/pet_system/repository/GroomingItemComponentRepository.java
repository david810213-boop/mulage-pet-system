package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.GroomingItemComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroomingItemComponentRepository extends JpaRepository<GroomingItemComponent, Long> {
    List<GroomingItemComponent> findByGroomingItemId(Long groomingItemId);
    void deleteByGroomingItemId(Long groomingItemId);
}
