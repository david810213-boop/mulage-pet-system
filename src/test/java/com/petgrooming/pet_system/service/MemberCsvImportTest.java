package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.MemberImportResult;
import com.petgrooming.pet_system.dto.SimpleImportResult;
import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 既有會員資料匯入（CSV）：會員＋寵物、儲值餘額、消費紀錄；編碼偵測（UTF-8 BOM／Big5）、
 * 電話正規化、逐列錯誤訊息與重複匯入防呆。
 */
class MemberCsvImportTest extends IntegrationTestBase {

    private static final String MEMBER_HEADER = "家長姓名,電話,毛孩名字,物種,品種,體重,年齡,是否分離焦慮,注意事項";

    @Autowired private MemberImportService memberImportService;
    @Autowired private UserRepository userRepository;
    @Autowired private PetRepository petRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalkInOrderRepository walkInOrderRepository;

    @Test
    void importsMembersWithPetsFromUtf8WithBomAndReportsBadRows() throws Exception {
        String phone = phone();
        String withoutLeadingZero = phone.substring(1); // Excel 吃掉開頭 0 的情況
        String csv = MEMBER_HEADER + "\n"
                + "王小明," + withoutLeadingZero + ",咪咪,貓,米克斯,4.5,3,是,怕生\n"
                + "王小明," + phone + ",旺財,狗,柴犬,10,2.5,否,\n"
                + "\n"
                + "李大華," + phone() + ",小黑,狗,柴犬,abc,3,否,\n";

        MemberImportResult result = memberImportService.importFromCsv(file("﻿" + csv, StandardCharsets.UTF_8));

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getMembersCreated()).isEqualTo(1);
        assertThat(result.getPetsCreated()).isEqualTo(2);
        assertThat(result.getRowErrors()).singleElement().asString()
                .startsWith("第 3 列寵物資料解析失敗：").contains("體重欄位格式錯誤：abc");
        User owner = userRepository.findByPhone(phone).orElseThrow();
        assertThat(owner.getName()).isEqualTo("王小明");
        assertThat(owner.getUsername()).isEqualTo("imported_" + phone);
        assertThat(petRepository.findByOwnerId(owner.getId()))
                .extracting(Pet::getName, Pet::getPetType, Pet::getHasSeparationAnxiety)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("咪咪", PetType.CAT, true),
                        org.assertj.core.groups.Tuple.tuple("旺財", PetType.DOG, false));

        MemberImportResult again = memberImportService.importFromCsv(file(csv, StandardCharsets.UTF_8));
        assertThat(again.getMembersCreated()).isZero();
        assertThat(again.getMembersSkipped()).isEqualTo(1);
        assertThat(again.getSkippedPhones()).containsExactly(phone);
    }

    @Test
    void readsBig5EncodedFile() throws Exception {
        String phone = phone();
        String csv = MEMBER_HEADER + "\n" + "陳美麗," + phone + ",豆豆,狗,貴賓,5,1,否,會咬人\n";

        MemberImportResult result = memberImportService.importFromCsv(file(csv, Charset.forName("Big5")));

        assertThat(result.getRowErrors()).isEmpty();
        User owner = userRepository.findByPhone(phone).orElseThrow();
        assertThat(owner.getName()).isEqualTo("陳美麗");
        assertThat(petRepository.findByOwnerId(owner.getId())).singleElement()
                .satisfies(pet -> {
                    assertThat(pet.getName()).isEqualTo("豆豆");
                    assertThat(pet.getBreed()).isEqualTo("貴賓");
                    assertThat(pet.getNotes()).isEqualTo("會咬人");
                });
    }

    @Test
    void importsWalletBalancesWithCardTier() throws Exception {
        String phone = phone();
        memberImportService.importFromCsv(file(MEMBER_HEADER + "\n林先生," + phone + ",皮皮,狗,柴犬,8,2,否,\n",
                StandardCharsets.UTF_8));
        String csv = "電話,儲值餘額,會員等級,到期日\n"
                + phone + ",15000,金卡,2027/3/31\n"
                + phone() + ",500,,\n"
                + phone + ",-1,,\n";

        SimpleImportResult result = memberImportService.importWalletBalances(file(csv, StandardCharsets.UTF_8));

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getSucceeded()).isEqualTo(1);
        assertThat(result.getRowErrors()).hasSize(2);
        assertThat(result.getRowErrors().get(0)).startsWith("第 2 列：查無電話");
        assertThat(result.getRowErrors().get(1)).startsWith("第 3 列：儲值餘額格式錯誤");
        User owner = userRepository.findByPhone(phone).orElseThrow();
        Wallet wallet = walletRepository.findByUserId(owner.getId()).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(15000);
        assertThat(wallet.getCardTier()).isEqualTo(MemberCardTier.GOLD);
        assertThat(wallet.getCardExpiresAt()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(wallet.getCardActivatedAt()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void importsConsumptionHistoryAsPaidWalkInOrders() throws Exception {
        String phone = phone();
        memberImportService.importFromCsv(file(MEMBER_HEADER + "\n黃小姐," + phone + ",妹妹,貓,米克斯,4,5,否,\n",
                StandardCharsets.UTF_8));
        String csv = "電話,毛孩名字,消費日期,金額,備註\n"
                + phone + ",妹妹,2026-05-01,1200,洗澡\n"
                + phone + ",不存在的貓,2026-05-01,800,\n"
                + phone + ",妹妹,2026/05/01,800,\n";

        SimpleImportResult result = memberImportService.importConsumptionHistory(file(csv, StandardCharsets.UTF_8));

        assertThat(result.getSucceeded()).isEqualTo(1);
        assertThat(result.getRowErrors()).hasSize(2);
        assertThat(result.getRowErrors().get(0)).contains("查無寵物「不存在的貓」");
        assertThat(result.getRowErrors().get(1)).contains("日期格式錯誤");
        User owner = userRepository.findByPhone(phone).orElseThrow();
        assertThat(walkInOrderRepository.findByMemberUsernameOrderByCreatedAtDesc(owner.getUsername()))
                .singleElement().satisfies(order -> {
                    assertThat(order.isPaid()).isTrue();
                    assertThat(order.getChargedAmount()).isEqualTo(1200);
                    assertThat(order.getCreatedAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 1));
                });
    }

    private static MockMultipartFile file(String content, Charset charset) {
        return new MockMultipartFile("file", "import.csv", "text/csv", content.getBytes(charset));
    }

    private static String phone() {
        return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }
}
