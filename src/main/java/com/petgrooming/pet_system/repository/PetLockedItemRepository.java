package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.PetLockedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PetLockedItemRepository extends JpaRepository<PetLockedItem, Long> {

    List<PetLockedItem> findByPetIdOrderByIdAsc(Long petId);

    List<PetLockedItem> findByPetIdInOrderByIdAsc(Collection<Long> petIds);

    @Modifying
    @Query("delete from PetLockedItem p where p.petId = :petId")
    void deleteByPetId(@Param("petId") Long petId);
}
