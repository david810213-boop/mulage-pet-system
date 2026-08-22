package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.CompanySignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySignatureRepository extends JpaRepository<CompanySignature, Long> {
    // 需求（追加）：單例設定表如果不小心存在多筆資料，findAll().findFirst() 沒有
    // 保證固定順序，可能今天抓到這筆、明天抓到那筆，導致「明明設定過卻顯示尚未設定」。
    // 改成明確依 id 由大到小排序取最新那筆，行為固定、可預期。
    Optional<CompanySignature> findFirstByOrderByIdDesc();
}
