package com.petgrooming.pet_system.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.petgrooming.pet_system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 啟動時不再建立固定密碼的員工與會員帳號（repo 是公開的，密碼等於公開）。
 */
class SeedAccountsTest extends IntegrationTestBase {

    @Autowired private UserRepository userRepository;

    @Test
    void noPublicDefaultAccountsAreCreated() {
        assertThat(userRepository.existsByUsername("staff@pet.com")).isFalse();
        assertThat(userRepository.existsByUsername("user@pet.com")).isFalse();
    }
}
