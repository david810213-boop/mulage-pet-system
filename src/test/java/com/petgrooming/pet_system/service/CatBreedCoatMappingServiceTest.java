package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.CatCoatCategory;
import com.petgrooming.pet_system.exception.PetException;
import com.petgrooming.pet_system.model.CatBreedCoatMapping;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatBreedCoatMappingServiceTest extends IntegrationTestBase {

    @Autowired
    private CatBreedCoatMappingService service;

    @Test
    void addTrimsNameAndRejectsDuplicates() {
        String breed = uniqueBreed();

        CatBreedCoatMapping added = service.add("  " + breed + " ", firstCategory(), 5);

        assertThat(added.getBreedName()).isEqualTo(breed);
        assertThatThrownBy(() -> service.add(breed, firstCategory(), 0))
                .isInstanceOf(PetException.class)
                .hasMessageContaining("不能重複新增");
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> service.add("   ", firstCategory(), 0))
                .isInstanceOf(PetException.class)
                .hasMessage("品種名稱不能空白");
    }

    @Test
    void updatingMissingMappingIsRejected() {
        assertThatThrownBy(() -> service.update(999_999_999L, "暹羅貓", firstCategory(), 0))
                .isInstanceOf(PetException.class)
                .hasMessage("找不到這筆對照資料");
    }

    @Test
    void deleteReturnsTheRemovedMappingOnlyOnce() {
        CatBreedCoatMapping added = service.add(uniqueBreed(), firstCategory(), 0);

        assertThat(service.delete(added.getId())).isPresent();
        assertThat(service.delete(added.getId())).isEmpty();
    }

    private static CatCoatCategory firstCategory() {
        return CatCoatCategory.values()[0];
    }

    private static String uniqueBreed() {
        return "測試品種" + UUID.randomUUID().toString().substring(0, 6);
    }
}
