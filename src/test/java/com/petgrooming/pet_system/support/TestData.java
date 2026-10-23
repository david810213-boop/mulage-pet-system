package com.petgrooming.pet_system.support;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.service.ClosedDateService;
import com.petgrooming.pet_system.service.PetService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
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

    /** 預約時的契約簽名：服務端只檢查是 data:image 開頭且長度足夠。 */
    public static final String SIGNATURE = "data:image/png;base64," + "A".repeat(1200);

    private final UserRepository userRepository;
    private final PetService petService;
    private final ClosedDateService closedDateService;

    public TestData(UserRepository userRepository, PetService petService, ClosedDateService closedDateService) {
        this.userRepository = userRepository;
        this.petService = petService;
        this.closedDateService = closedDateService;
    }

    public PetResponse dog(User owner) {
        return pet(owner, PetType.DOG, "柴犬", 10.0);
    }

    public PetResponse cat(User owner) {
        return pet(owner, PetType.CAT, "米克斯", 4.0);
    }

    private PetResponse pet(User owner, PetType type, String breed, double weight) {
        PetRequest req = new PetRequest();
        req.setName("毛孩" + UUID.randomUUID().toString().substring(0, 6));
        req.setPetType(type);
        req.setBreed(breed);
        req.setWeight(weight);
        req.setAge(3.0);
        return petService.addPet(owner.getUsername(), req);
    }

    /** 尚未被其他測試使用、且不是公休日的未來日期。 */
    public LocalDate openFutureDate() {
        LocalDate date = uniqueFutureDate();
        while (closedDateService.isClosed(date)) {
            date = uniqueFutureDate();
        }
        return date;
    }

    /** 不選服務項目的預約請求（初次預約只選時段，由店家到店評估報價）。 */
    public static AppointmentRequest bookingRequest(Long petId, LocalDate date, LocalTime start) {
        AppointmentRequest req = new AppointmentRequest();
        req.setPetId(petId);
        req.setDate(date);
        req.setStartTime(start);
        req.setEndTime(start.plusHours(2));
        req.setContractSignatureData(SIGNATURE);
        return req;
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
