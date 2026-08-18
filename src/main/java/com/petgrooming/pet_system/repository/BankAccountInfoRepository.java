package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.model.BankAccountInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankAccountInfoRepository extends JpaRepository<BankAccountInfo, Long> {

    // 需求（追加）：依用途查詢對應的收款帳戶（結帳收款 / 儲值金收款，各自最多一筆）
    Optional<BankAccountInfo> findByPurpose(BankAccountPurpose purpose);
}
