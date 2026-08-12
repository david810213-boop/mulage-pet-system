package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.BankAccountInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountInfoRepository extends JpaRepository<BankAccountInfo, Long> {
}
