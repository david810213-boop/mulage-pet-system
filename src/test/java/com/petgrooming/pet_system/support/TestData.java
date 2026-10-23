package com.petgrooming.pet_system.support;

import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 測試資料工廠。所有值都帶唯一後綴，讓測試之間共用同一個資料庫也不會互相干擾。
 */
@Component
public class TestData {

    /** 測試用時段一律放在遙遠的未來，避免與種子資料或「禁止預約過去日期」規則衝突。 */
    private static final LocalDate FAR_FUTURE = LocalDate.of(2099, 1, 4);
    private static final AtomicInteger DAY_OFFSET = new AtomicInteger();

    private final UserRepository userRepository;

    public TestData(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User customer() {
        return user(UserRole.CUSTOMER);
    }

    public User staff() {
        return user(UserRole.STAFF);
    }

    public User admin() {
        return user(UserRole.ADMIN);
    }

    private User user(UserRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(role.name().toLowerCase() + "-" + suffix + "@test.local")
                .password("{noop}unused")
                .name("測試" + role.getDescription() + suffix)
                .role(role)
                .build());
    }

    /** 每次呼叫回傳一個尚未被其他測試使用過的日期。 */
    public static LocalDate uniqueFutureDate() {
        return FAR_FUTURE.plusDays(DAY_OFFSET.getAndIncrement());
    }
}
