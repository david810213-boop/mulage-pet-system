package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.StoreSupply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreSupplyRepository extends JpaRepository<StoreSupply, Long> {

    List<StoreSupply> findByIsDeletedFalseOrderByNameAsc();
}
