package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.exception.MemberException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 既有會員自助認領：店家匯入的紙本會員資料，由顧客以 LINE 登入後輸入電話，
 * 再以毛孩名字（沒有毛孩則以姓名）確認身分後認領，資料連同錢包一併過戶。
 */
class MemberClaimTest extends IntegrationTestBase {

    @Autowired
    private MemberImportService memberImportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TestData testData;

    @Test
    void lookupShowsOnlyMaskedNameAndHowToVerify() {
        String phone = randomPhone();
        importedMember("王小明", phone, true);

        Map<String, Object> result = memberImportService.lookupForClaim(newLineUser(), phone);

        assertThat(result).containsEntry("matched", true)
                .containsEntry("maskedName", "王○明")
                .containsEntry("verifyBy", "PET");
    }

    @Test
    void lookupWithUnknownPhoneFindsNothing() {
        Map<String, Object> result = memberImportService.lookupForClaim(newLineUser(), randomPhone());

        assertThat(result).containsEntry("matched", false);
    }

    @Test
    void wrongPetNameIsRejectedAndImportedDataStays() {
        String phone = randomPhone();
        User imported = importedMember("王小明", phone, true);

        assertThatThrownBy(() -> memberImportService.claimByPhone(newLineUser(), phone, "不是這隻"))
                .isInstanceOf(MemberException.class);
        assertThat(userRepository.findById(imported.getId())).isPresent();
    }

    @Test
    void correctPetNameTransfersPetsWalletAndCardThenRemovesImportedAccount() {
        String phone = randomPhone();
        User imported = importedMember("王小明", phone, true);
        String petName = petRepository.findByOwnerId(imported.getId()).get(0).getName();
        User lineUser = newLineUser();

        User claimed = memberImportService.claimByPhone(lineUser, "  " + phone.substring(0, 4) + "-" + phone.substring(4) + " ", petName);

        assertThat(claimed.getName()).isEqualTo("王小明");
        assertThat(claimed.getPhone()).isEqualTo(phone);
        assertThat(petRepository.findByOwnerId(lineUser.getId())).hasSize(1);
        Wallet wallet = walletRepository.findByUserId(lineUser.getId()).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(3000);
        assertThat(wallet.getCardTier()).isEqualTo(MemberCardTier.GOLD);
        assertThat(userRepository.findById(imported.getId())).isEmpty();
    }

    @Test
    void memberWithoutPetsIsVerifiedByName() {
        String phone = randomPhone();
        importedMember("陳大文", phone, false);
        User lineUser = newLineUser();

        assertThatThrownBy(() -> memberImportService.claimByPhone(lineUser, phone, "陳小文"))
                .isInstanceOf(MemberException.class);

        User claimed = memberImportService.claimByPhone(lineUser, phone, "陳大文");
        assertThat(claimed.getName()).isEqualTo("陳大文");
    }

    @Test
    void accountThatAlreadyFilledProfileCannotClaim() {
        String phone = randomPhone();
        importedMember("王小明", phone, false);
        User lineUser = newLineUser();
        lineUser.setProfileCompletedAt(LocalDateTime.now());
        userRepository.save(lineUser);

        assertThatThrownBy(() -> memberImportService.claimByPhone(lineUser, phone, "王小明"))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("已經填過資料");
    }

    @Test
    void accountAlreadyLinkedToLineCannotBeClaimed() {
        String phone = randomPhone();
        User linked = importedMember("王小明", phone, false);
        linked.setLineUserId("U" + UUID.randomUUID().toString().replace("-", ""));
        userRepository.save(linked);

        assertThatThrownBy(() -> memberImportService.claimByPhone(newLineUser(), phone, "王小明"))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("查無");
    }

    private User importedMember(String name, String phone, boolean withPet) {
        User imported = testData.customer();
        imported.setName(name);
        imported.setPhone(phone);
        userRepository.save(imported);
        if (withPet) {
            testData.dog(imported);
        }
        walletRepository.save(Wallet.builder()
                .user(imported)
                .balance(3000)
                .cardTier(MemberCardTier.GOLD)
                .cardActivatedAt(LocalDate.now())
                .cardExpiresAt(WalletService.cardExpiryFrom(LocalDate.now()))
                .build());
        return imported;
    }

    private User newLineUser() {
        User user = testData.customer();
        user.setLineUserId("U" + UUID.randomUUID().toString().replace("-", ""));
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        return userRepository.save(user);
    }

    private static String randomPhone() {
        return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }
}
