package com.petgrooming.pet_system.config;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.BankAccountInfo;
import com.petgrooming.pet_system.model.BonusTier;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.BankAccountInfoRepository;
import com.petgrooming.pet_system.repository.BonusTierRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final GroomingItemRepository groomingItemRepository;
    private final BonusTierRepository bonusTierRepository;
    private final BankAccountInfoRepository bankAccountInfoRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 1. 初始化預設帳號
        createIfNotExists("admin@pet.com", "admin123", "系統管理員", UserRole.ADMIN);
        createIfNotExists("staff@pet.com", "staff123", "美容師小洪", UserRole.STAFF);
        createIfNotExists("user@pet.com", "user123", "測試會員", UserRole.CUSTOMER);
        log.info("預設帳號初始化完成");

        // 需求 3：積分獎勵金級距（改成可在後台編輯的資料表，這裡只是種子資料，僅在資料表是空的時候建立一次）
        if (bonusTierRepository.count() == 0) {
            bonusTierRepository.save(BonusTier.builder().minPoints(3001).maxPoints(3350).bonusAmount(1200).build());
            bonusTierRepository.save(BonusTier.builder().minPoints(3351).maxPoints(3700).bonusAmount(1600).build());
            bonusTierRepository.save(BonusTier.builder().minPoints(3701).maxPoints(4000).bonusAmount(2200).build());
            bonusTierRepository.save(BonusTier.builder().minPoints(4001).maxPoints(4300).bonusAmount(2800).build());
            bonusTierRepository.save(BonusTier.builder().minPoints(4301).maxPoints(4600).bonusAmount(3600).build());
            bonusTierRepository.save(BonusTier.builder().minPoints(4601).maxPoints(5000).bonusAmount(4400).build());
            bonusTierRepository.save(BonusTier.builder().minPoints(5001).maxPoints(5300).bonusAmount(5600).build());
            bonusTierRepository.save(BonusTier.builder().minPoints(5301).maxPoints(5600).bonusAmount(6600).build());
            log.info("積分獎勵金級距種子資料初始化完成");
        }

        // 需求 10：店家匯款帳號資訊，僅在還沒設定過時建立一筆預設（佔位）資料，店家可自行到後台修改
        if (bankAccountInfoRepository.count() == 0) {
            bankAccountInfoRepository.save(BankAccountInfo.builder()
                    .bankName("請於後台設定銀行名稱")
                    .accountNumber("請於後台設定帳號")
                    .accountHolder("請於後台設定戶名")
                    .build());
            log.info("匯款帳號預設資料初始化完成，請記得到後台修改成實際帳號");
        }

        if (groomingItemRepository.count() == 0) {

            // ── 主要美容項目（計積分）─────────────────────────────────
            // 洗澡（依體型）
            saveItem("BATH_S", "洗澡（小型犬）", "小型犬洗澡服務", 800.0, PerformanceCategory.BATH_SMALL);
            saveItem("BATH_L", "洗澡（大型犬）", "大型犬洗澡服務", 1200.0, PerformanceCategory.BATH_LARGE);
            saveItem("BATH_CS", "洗澡（小貓）", "小貓洗澡服務", 800.0, PerformanceCategory.BATH_CAT_S);
            saveItem("BATH_CL", "洗澡（大貓）", "大貓洗澡服務", 1200.0, PerformanceCategory.BATH_CAT_L);

            // 吹毛（依體型）
            saveItem("BLOW_S", "吹毛（小型犬）", "小型犬吹整毛髮", 600.0, PerformanceCategory.BLOW_SMALL);
            saveItem("BLOW_L", "吹毛（大型犬）", "大型犬吹整毛髮", 900.0, PerformanceCategory.BLOW_LARGE);
            saveItem("BLOW_CS", "吹毛（小貓）", "小貓吹整毛髮", 600.0, PerformanceCategory.BLOW_CAT_S);
            saveItem("BLOW_CL", "吹毛（大貓）", "大貓吹整毛髮", 900.0, PerformanceCategory.BLOW_CAT_L);

            // 基礎美容、剪毛
            saveItem("BASIC", "基礎美容", "基礎美容套餐服務", 500.0, PerformanceCategory.BASIC);
            saveItem("TRIM", "剪毛", "全身毛髮修剪造型", 600.0, PerformanceCategory.TRIM);

            // 特殊項目
            saveItem("AD", "AD藥浴", "抗菌除蟲藥浴療程", 400.0, PerformanceCategory.AD);
            saveItem("HC", "HC護毛", "深層護毛修護療程", 500.0, PerformanceCategory.HC);
            saveItem("PARTIAL", "局部修剪", "局部毛髮精修", 200.0, PerformanceCategory.PARTIAL);
            saveItem("SPECIAL", "特殊項目", "其他特殊美容項目", 0.0, PerformanceCategory.SPECIAL);

            // 接待、完成（流程記錄用）
            saveItem("CHECKIN", "接待入場", "顧客接待與寵物入場登記", 0.0, PerformanceCategory.CHECKIN);
            saveItem("CHECKOUT", "接待送出", "服務完成寵物送交顧客", 0.0, PerformanceCategory.CHECKOUT);
            saveItem("COMPLETE", "完成確認", "整筆服務完成確認", 0.0, PerformanceCategory.COMPLETE);

            // ── 加值服務項目（不計積分，GS001~GS012）───────────────────
            saveItem("GS001", "指甲修剪磨圓", "包含基本的指甲長度修剪與邊緣圓滑打磨", 200.0, PerformanceCategory.OTHER);
            saveItem("GS002", "剃腳底屁股毛", "局部毛髮修剪，保持寵物居家清潔與防滑", 150.0, PerformanceCategory.OTHER);
            saveItem("GS003", "擠肛門腺", "溫和清潔寵物肛門]'腺，減少異味與不適感", 180.0, PerformanceCategory.OTHER);
            saveItem("GS004", "耳道清潔", "使用寵物專用潔耳液，溫和清除耳垢", 100.0, PerformanceCategory.OTHER);
            saveItem("GS005", "手工吹整毛髮", "專業美容師手工吹整，打造蓬鬆順滑毛質", 300.0, PerformanceCategory.OTHER);
            saveItem("GS006", "腳緣修剪", "精修足部線條，讓腳掌看起來圓潤乾淨", 200.0, PerformanceCategory.OTHER);
            saveItem("GS007", "臉部精緻修容", "根據寵物臉型進行細緻修剪，視覺造型升級", 350.0, PerformanceCategory.OTHER);
            saveItem("GS008", "舒壓按摩", "舒緩寵物肌肉緊張，降低美容過程的焦慮感", 400.0, PerformanceCategory.OTHER);
            saveItem("GS009", "天然低敏結構式洗浴", "使用頂級天然低敏洗劑，深層修復受損毛髮", 500.0, PerformanceCategory.OTHER);
            saveItem("GS010", "護膚潤澤毛髮", "加強皮膚保濕與毛髮毛鱗片滋養", 280.0, PerformanceCategory.OTHER);
            saveItem("GS011", "毛鱗修復液", "專門針對乾枯毛髮設計的密集修護安瓶精華", 320.0, PerformanceCategory.OTHER);
            saveItem("GS012", "牙齒清潔", "使用寵物酵素牙膏，基本口腔清潔與除垢", 200.0, PerformanceCategory.OTHER);

            log.info("✨ [系統通知] {}項美容服務項目已成功初始化入庫！", groomingItemRepository.count());
        }

        // 需求 5 修正：折扣規則改為「所有計積分的項目都可以打折」，只有 GS001~GS012
        // 這種加值加購項目（不計積分，performanceCategory 為 OTHER）才不打折。
        // 局部修剪／特殊項目原本被排除，這次修正後也納入可打折範圍。
        // CHECKIN/CHECKOUT/COMPLETE 是流程記錄用項目（價格恆為 0），打不打折沒有實際差異，
        // 為了語意清楚（它們不是真正販售的服務）維持排除，但不影響任何金額計算。
        //
        // 這段邏輯改成「雙向強制」而非只糾正一邊：不在排除清單內的項目一律強制改回 true，
        // 排除清單內的一律強制改回 false，每次啟動都會重新校正一次，
        // 不管資料庫現有狀態是什麼（不管是全新安裝、舊資料庫殘留、或曾經被舊版清單設定錯誤過），
        // 啟動後一定會回到跟這份清單一致的正確狀態，不會再有「改過清單但舊資料沒跟著更新」的漏網之魚。
        java.util.List<String> nonDiscountCodes = java.util.List.of(
                "CHECKIN", "CHECKOUT", "COMPLETE",
                "GS001", "GS002", "GS003", "GS004", "GS005", "GS006",
                "GS007", "GS008", "GS009", "GS010", "GS011", "GS012");
        for (GroomingItem item : groomingItemRepository.findAll()) {
            boolean shouldBeDiscountEligible = !nonDiscountCodes.contains(item.getItemCode());
            if (item.isDiscountEligible() != shouldBeDiscountEligible) {
                item.setDiscountEligible(shouldBeDiscountEligible);
                groomingItemRepository.save(item);
                log.info("已校正項目 {} 的折扣資格為 {}", item.getItemCode(), shouldBeDiscountEligible);
            }
        }
    }

    private void saveItem(String code, String name, String desc, double price, PerformanceCategory category) {
        GroomingItem item = GroomingItem.builder()
                .itemCode(code)
                .name(name)
                .description(desc)
                .price(price)
                .isDeleted(false)
                .performanceCategory(category)
                .points(category.getDefaultPoints())
                .build();
        groomingItemRepository.save(item);
    }

    private void createIfNotExists(String username, String password, String name, UserRole role) {
        if (userRepository.existsByUsername(username))
            return;
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .name(name)
                .role(role)
                .isActive(true)
                .build();
        userRepository.save(user);
        log.info("建立預設帳號：{} ({})", username, role);
    }
}