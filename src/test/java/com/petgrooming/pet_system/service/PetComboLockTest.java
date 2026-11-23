package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.WalkInOrderCreateRequest;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.DogWeightTier;
import com.petgrooming.pet_system.exception.PetException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.service.PetComboLockService.ComboLine;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 固定組合（客製化鎖定）：整組自動帶入、照鎖定時的固定價，可以另外加購，至少要有一項精緻洗。
 */
class PetComboLockTest extends IntegrationTestBase {

    @Autowired private PetComboLockService petComboLockService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private WalkInOrderService walkInOrderService;
    @Autowired private PetService petService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private GroomingItemRepository groomingItemRepository;
    @Autowired private TestData testData;

    @Test
    void bookingAlwaysIncludesComboAtLockedPricesAndAllowsExtras() {
        User customer = testData.customer();
        PetResponse dog = returningDog(customer);
        GroomingItem bath = item(1000, DogWeightTier.MEDIUM_SMALL, false);
        GroomingItem hc = item(800, DogWeightTier.MEDIUM_SMALL, true);
        GroomingItem teeth = item(100, null, false);
        GroomingItem extra = item(300, null, false);
        petComboLockService.lockCombo(dog.getId(), List.of(
                new ComboLine(bath.getId(), 900), new ComboLine(hc.getId(), 700), new ComboLine(teeth.getId(), 100)));
        bath.setPrice(1500.0); // 鎖定後店家調價，不影響這隻狗
        groomingItemRepository.save(bath);

        AppointmentResponse comboOnly = appointmentService.book(withItems(dog), customer.getUsername());
        AppointmentResponse withExtra = appointmentService.book(withItems(dog, extra), customer.getUsername());

        assertThat(comboOnly.getTotalAmount()).isEqualTo(900 + 700 + 100);
        assertThat(withExtra.getTotalAmount()).isEqualTo(900 + 700 + 100 + 300);
    }

    @Test
    void walkInOrderAddsMissingComboItemsAtLockedPrices() {
        User customer = testData.customer();
        PetResponse dog = testData.dog(customer);
        GroomingItem bath = item(1000, DogWeightTier.MEDIUM_SMALL, false);
        GroomingItem ad = item(500, DogWeightTier.MEDIUM_SMALL, true);
        petComboLockService.lockCombo(dog.getId(), List.of(
                new ComboLine(bath.getId(), 950), new ComboLine(ad.getId(), 450)));

        WalkInOrderCreateRequest req = new WalkInOrderCreateRequest();
        req.setMemberUsername(customer.getUsername());
        req.setPetName(dog.getName());
        WalkInOrderCreateRequest.Item line = new WalkInOrderCreateRequest.Item();
        line.setItemCode(bath.getItemCode());
        req.setItems(new ArrayList<>(List.of(line)));
        req.setRetailItems(new ArrayList<>());
        WalkInOrderResponse order = walkInOrderService.create(req, testData.staff().getUsername());

        assertThat(order.getTotalAmount()).isEqualTo(950 + 450);
    }

    @Test
    void comboNeedsExactlyOnePackageAndUnlockClearsIt() {
        PetResponse dog = testData.dog(testData.customer());
        GroomingItem hc = item(800, DogWeightTier.MEDIUM_SMALL, true);
        GroomingItem bath = item(1000, DogWeightTier.MEDIUM_SMALL, false);

        assertThatThrownBy(() -> petComboLockService.lockCombo(dog.getId(), List.of(new ComboLine(hc.getId(), 800))))
                .isInstanceOf(PetException.class)
                .hasMessageContaining("精緻洗");

        PetResponse locked = petComboLockService.lockCombo(dog.getId(), List.of(
                new ComboLine(bath.getId(), 1000), new ComboLine(hc.getId(), 800)));
        assertThat(locked.getLockedGroomingItemId()).isEqualTo(bath.getId());
        assertThat(locked.getLockedItems()).hasSize(2);
        assertThat(locked.getLockedItemPrice()).isEqualTo(1800.0);

        PetResponse unlocked = petService.unlockGroomingItem(dog.getId());
        assertThat(unlocked.getLockedGroomingItemId()).isNull();
        assertThat(unlocked.getLockedItems()).isEmpty();
    }

    private PetResponse returningDog(User owner) {
        PetResponse dog = testData.dog(owner);
        appointmentRepository.save(Appointment.builder()
                .user(owner)
                .petName(dog.getName())
                .petType("DOG")
                .date(LocalDate.now().minusMonths(1))
                .startTime(LocalTime.NOON)
                .endTime(LocalTime.of(14, 0))
                .totalAmount(1000)
                .paid(true)
                .status(AppointmentStatus.COMPLETED)
                .build());
        return dog;
    }

    private GroomingItem item(int price, DogWeightTier tier, boolean tierAddon) {
        String code = "L" + UUID.randomUUID().toString().substring(0, 8);
        return groomingItemRepository.save(GroomingItem.builder()
                .itemCode(code)
                .name("鎖定測試項目" + code)
                .price((double) price)
                .dogWeightTier(tier)
                .tierAddon(tierAddon)
                .build());
    }

    private AppointmentRequest withItems(PetResponse pet, GroomingItem... items) {
        AppointmentRequest req = bookingRequest(pet.getId(), testData.openFutureDate(), LocalTime.of(14, 0));
        req.setSelectedItems(List.of(items).stream().map(GroomingItem::getItemCode).toList());
        return req;
    }
}
