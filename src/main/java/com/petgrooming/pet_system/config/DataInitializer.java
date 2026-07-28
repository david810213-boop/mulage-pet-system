package com.petgrooming.pet_system.config;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.User;
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
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 1. 初始化預設帳號
        createIfNotExists("admin@pet.com", "admin123", "系統管理員", UserRole.ADMIN);
        createIfNotExists("staff@pet.com", "staff123", "美容師小洪", UserRole.STAFF);
        createIfNotExists("user@pet.com", "user123", "測試會員", UserRole.CUSTOMER);
        log.info("預設帳號初始化完成");

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