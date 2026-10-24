package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.CompanySignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySignatureRepository extends JpaRepository<CompanySignature, Long> {
    // 單例設定表若不小心存在多筆，明確依 id 由大到小取最新那筆，
    // 避免 findAll() 沒有固定順序、每次抓到不同筆，導致「明明設定過卻顯示尚未設定」。
    Optional<CompanySignature> findFirstByOrderByIdDesc();
}
