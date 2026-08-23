package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.CatBreedCoatMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatBreedCoatMappingRepository extends JpaRepository<CatBreedCoatMapping, Long> {

    List<CatBreedCoatMapping> findAllByOrderBySortOrderAscBreedNameAsc();

    Optional<CatBreedCoatMapping> findByBreedName(String breedName);

    boolean existsByBreedName(String breedName);
}
