package com.petgrooming.pet_system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.exception.InventoryException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.RetailProductRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 零售商品會員價：會員卡在有效期內的顧客用會員價，其他人用售價；沒設會員價的商品一律售價。
 */
class RetailMemberPriceTest extends IntegrationTestBase {

    @Autowired private RetailProductService retailProductService;
    @Autowired private AppointmentItemService appointmentItemService;
    @Autowired private WalkInOrderService walkInOrderService;
    @Autowired private InventoryImportService inventoryImportService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentItemRepository appointmentItemRepository;
    @Autowired private WalkInOrderRepository walkInOrderRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private RetailProductRepository retailProductRepository;
    @Autowired private TestData testData;

    @Test
    void activeCardMemberPaysMemberPrice() {
        RetailProduct product = product(450, 400);
        User member = memberWithCard(LocalDate.now().plusDays(30));
        Appointment appointment = appointment(member);

        appointmentItemService.addRetailItem(appointment.getId(), product.getId(), 2, "staff");

        List<AppointmentItem> items = appointmentItemRepository.findByAppointmentId(appointment.getId());
        assertThat(items).extracting(AppointmentItem::getPrice).containsExactly(400, 400);
        assertThat(items.get(0).getItemName()).endsWith("（會員價）");
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getTotalAmount()).isEqualTo(1800);
    }

    @Test
    void expiredCardOrNoMemberPricePaysRegularPrice() {
        User expired = memberWithCard(LocalDate.now().minusDays(1));
        Appointment appointment = appointment(expired);
        appointmentItemService.addRetailItem(appointment.getId(), product(450, 400).getId(), 1, "staff");

        User active = memberWithCard(LocalDate.now().plusDays(30));
        Appointment other = appointment(active);
        appointmentItemService.addRetailItem(other.getId(), product(300, null).getId(), 1, "staff");

        assertThat(appointmentItemRepository.findByAppointmentId(appointment.getId()))
                .extracting(AppointmentItem::getPrice).containsExactly(450);
        assertThat(appointmentItemRepository.findByAppointmentId(other.getId()))
                .extracting(AppointmentItem::getPrice).containsExactly(300);
    }

    @Test
    void walkInUsesMemberPriceOnlyWhenMemberHasActiveCard() {
        RetailProduct product = product(450, 400);
        WalkInOrder withMember = walkIn(memberWithCard(LocalDate.now().plusDays(30)));
        WalkInOrder anonymous = walkIn(null);

        assertThat(walkInOrderService.addRetailItem(withMember.getId(), product.getId(), 1, "staff")
                .getTotalAmount()).isEqualTo(400);
        assertThat(walkInOrderService.addRetailItem(anonymous.getId(), product.getId(), 1, "staff")
                .getTotalAmount()).isEqualTo(450);
    }

    @Test
    void memberPriceCannotExceedPriceAndCanBeClearedOrLowered() {
        RetailProduct product = product(450, 400);

        assertThatThrownBy(() -> retailProductService.update(product.getId(), product.getName(), 450, null, 0,
                null, 500)).isInstanceOf(InventoryException.class);

        // 售價調降到舊會員價以下，同時改會員價
        retailProductService.update(product.getId(), product.getName(), 350, null, 0, null, 300);
        assertThat(reload(product).getMemberPrice()).isEqualTo(300);

        retailProductService.update(product.getId(), product.getName(), 350, null, 0, null, null);
        assertThat(reload(product).getMemberPrice()).isNull();
    }

    @Test
    void csvImportReadsOptionalMemberPriceColumn() throws Exception {
        String a = "會員價商品" + UUID.randomUUID().toString().substring(0, 6);
        String b = "舊格式商品" + UUID.randomUUID().toString().substring(0, 6);
        String csv = "名稱,條碼,售價,成本,庫存,說明,會員價\n"
                + a + ",,450,200,3,,400\n"
                + b + ",,300,100,2,\n";
        var result = inventoryImportService.importRetail(new MockMultipartFile("file", "retail.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.getRowErrors()).isEmpty();
        assertThat(retailProductRepository.findFirstByNameAndIsDeletedFalse(a).orElseThrow().getMemberPrice())
                .isEqualTo(400);
        assertThat(retailProductRepository.findFirstByNameAndIsDeletedFalse(b).orElseThrow().getMemberPrice())
                .isNull();
    }

    private RetailProduct product(int price, Integer memberPrice) {
        return retailProductService.create("商品" + UUID.randomUUID().toString().substring(0, 8), price, 10, null, 0,
                null, memberPrice);
    }

    private RetailProduct reload(RetailProduct product) {
        return retailProductRepository.findById(product.getId()).orElseThrow();
    }

    private User memberWithCard(LocalDate expiresAt) {
        User user = testData.customer();
        walletRepository.save(Wallet.builder()
                .user(user)
                .balance(5000)
                .cardTier(MemberCardTier.GOLD)
                .cardActivatedAt(expiresAt.minusYears(1).plusDays(1))
                .cardExpiresAt(expiresAt)
                .build());
        return user;
    }

    private Appointment appointment(User customer) {
        return appointmentRepository.save(Appointment.builder()
                .user(customer)
                .petName("毛孩")
                .petType("DOG")
                .date(TestData.uniqueFutureDate())
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(14, 0))
                .totalAmount(1000)
                .paid(false)
                .status(AppointmentStatus.IN_PROGRESS)
                .build());
    }

    private WalkInOrder walkIn(User member) {
        return walkInOrderRepository.save(WalkInOrder.builder()
                .petName("現場毛孩")
                .member(member)
                .createdBy("測試")
                .build());
    }
}
