package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.CatCoatCategory;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PetException;
import com.petgrooming.pet_system.model.CatBreedCoatMapping;
import com.petgrooming.pet_system.repository.CatBreedCoatMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 貓咪品種 → 毛髮分類對照表的維護（新增寵物時依品種自動帶出毛髮分類）。 */
@Service
@RequiredArgsConstructor
public class CatBreedCoatMappingService {

    private final CatBreedCoatMappingRepository repository;

    public List<CatBreedCoatMapping> listAll() {
        return repository.findAllByOrderBySortOrderAscBreedNameAsc();
    }

    @Transactional
    public CatBreedCoatMapping add(String breedName, CatCoatCategory coatCategory, Integer sortOrder) {
        String name = requireName(breedName);
        if (repository.existsByBreedName(name)) {
            throw new PetException(ErrorCode.CONFLICT, "「" + name + "」已經在對照表裡了，不能重複新增");
        }
        return repository.save(CatBreedCoatMapping.builder()
                .breedName(name)
                .coatCategory(coatCategory)
                .sortOrder(sortOrder)
                .build());
    }

    @Transactional
    public CatBreedCoatMapping update(Long id, String breedName, CatCoatCategory coatCategory, Integer sortOrder) {
        CatBreedCoatMapping mapping = repository.findById(id)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到這筆對照資料"));
        mapping.setBreedName(requireName(breedName));
        mapping.setCoatCategory(coatCategory);
        mapping.setSortOrder(sortOrder);
        return repository.save(mapping);
    }

    /** 刪除對照資料；回傳被刪除的資料，不存在時回傳空值。 */
    @Transactional
    public Optional<CatBreedCoatMapping> delete(Long id) {
        Optional<CatBreedCoatMapping> mapping = repository.findById(id);
        mapping.ifPresent(m -> repository.deleteById(id));
        return mapping;
    }

    private static String requireName(String breedName) {
        String name = breedName == null ? "" : breedName.trim();
        if (name.isEmpty()) {
            throw new PetException("品種名稱不能空白");
        }
        return name;
    }
}
