package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.LineBindToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LineBindTokenRepository extends JpaRepository<LineBindToken, Long> {

    Optional<LineBindToken> findByCodeAndUsedFalse(String code);
}
