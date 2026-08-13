package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findByOwnerId(Long ownerId);

    List<Pet> findByOwnerUsername(String username);

    Optional<Pet> findByOwnerUsernameAndName(String username, String petName);

    // 需求 9：依寵物名稱模糊搜尋（現場開單/預約用，自動對應家長）
    List<Pet> findByNameContainingIgnoreCase(String namePart);

    // 需求 8-2：查所有貓咪（回洗優惠名單篩選用）
    List<Pet> findByPetType(com.petgrooming.pet_system.enums.PetType petType);
}
