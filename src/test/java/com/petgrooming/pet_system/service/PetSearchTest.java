package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.PetSearchResponse;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 店員以寵物名稱搜尋：同名寵物以家長姓名與電話末四碼區分。 */
class PetSearchTest extends IntegrationTestBase {

    @Autowired
    private PetService petService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestData testData;

    @Test
    void resultLabelShowsOwnerAndLastFourDigitsOfPhone() {
        User owner = testData.customer();
        owner.setName("林小美");
        userRepository.save(owner);
        PetResponse dog = testData.dog(owner);

        List<PetSearchResponse> results = petService.searchForStaff("  " + dog.getName() + " ");

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.getPetId()).isEqualTo(dog.getId());
            assertThat(r.getOwnerUsername()).isEqualTo(owner.getUsername());
            assertThat(r.getDisplayLabel()).isEqualTo(dog.getName() + "（家長：林小美・電話末四碼 未填）");
        });
    }
}
