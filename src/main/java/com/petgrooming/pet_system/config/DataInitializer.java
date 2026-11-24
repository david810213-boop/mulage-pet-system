package com.petgrooming.pet_system.config;

import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.BankAccountInfo;
import com.petgrooming.pet_system.model.BonusTier;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WeeklyClosureSetting;
import com.petgrooming.pet_system.repository.BankAccountInfoRepository;
import com.petgrooming.pet_system.repository.BonusTierRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WeeklyClosureSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 啟動時建立「建立一次、之後由店家在後台自行調整」的種子資料：第一個管理員帳號、積分獎勵金級距、
 * 匯款收款帳戶、固定公休星期，一律逐筆檢查是否已存在才建立。
 *
 * 服務項目（GroomingItem）與套餐組成的種子資料由 Flyway 遷移檔管理（db/migration），不要加在這裡：
 * 用 count()==0 之類的條件判斷「只在全新資料庫建立」，在已有資料的正式站永遠不會成立，
 * 新種子資料會種不進去。見 docs/adr/0001-flyway-after-hibernate.md。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BonusTierRepository bonusTierRepository;
    private final BankAccountInfoRepository bankAccountInfoRepository;
    private final WeeklyClosureSettingRepository weeklyClosureSettingRepository; // 固定公休星期
    private final PasswordEncoder passwordEncoder;

    @Value("${INITIAL_ADMIN_PASSWORD:}")
    private String initialAdminPassword;

    private static final String INITIAL_ADMIN_USERNAME = "admin@pet.com";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        createInitialAdminIfNone();

        // 積分獎勵金級距：種子資料，僅在資料表是空的時候建立一次，之後由店家在後台編輯
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

        // 店家匯款帳號：結帳收款與儲值金收款（大額專用）是兩組獨立帳戶，各自檢查、各自補齊
        // 預設（佔位）資料，店家再到後台修改
        if (bankAccountInfoRepository.findByPurpose(BankAccountPurpose.CHECKOUT).isEmpty()) {
            bankAccountInfoRepository.save(BankAccountInfo.builder()
                    .purpose(BankAccountPurpose.CHECKOUT)
                    .bankName("請於後台設定銀行名稱")
                    .accountNumber("請於後台設定帳號")
                    .accountHolder("請於後台設定戶名")
                    .build());
            log.info("結帳收款帳號預設資料初始化完成，請記得到後台修改成實際帳號");
        }
        if (bankAccountInfoRepository.findByPurpose(BankAccountPurpose.TOPUP).isEmpty()) {
            bankAccountInfoRepository.save(BankAccountInfo.builder()
                    .purpose(BankAccountPurpose.TOPUP)
                    .bankName("請於後台設定銀行名稱")
                    .accountNumber("請於後台設定帳號")
                    .accountHolder("請於後台設定戶名")
                    .build());
            log.info("儲值金收款帳號（大額專用）預設資料初始化完成，請記得到後台修改成實際帳號");
        }

        // 固定公休星期：預設週四、週五公休，呼應契約文字本來就寫的「固定公休：週四、週五」
        // （這段文字之前只是契約說明，系統沒有真的去擋，這裡補上讓它成為實際生效的規則）。
        // 只在還沒有任何設定時建立這筆預設值，之後店家在後台改過的設定不會被這段覆蓋。
        if (weeklyClosureSettingRepository.count() == 0) {
            weeklyClosureSettingRepository.save(WeeklyClosureSetting.builder()
                    .closedThursday(true)
                    .closedFriday(true)
                    .build());
            log.info("固定公休星期預設資料初始化完成（週四、週五），可到後台調整");
        }
    }

    /**
     * 全新資料庫還沒有任何管理員時，用環境變數 INITIAL_ADMIN_PASSWORD 建立第一個管理員 admin@pet.com。
     * 密碼不寫在原始碼裡（repo 是公開的）；已經有管理員就不建立任何帳號，刪掉的帳號不會在重新部署後又出現。
     */
    private void createInitialAdminIfNone() {
        if (userRepository.countByRole(UserRole.ADMIN) > 0) {
            return;
        }
        if (initialAdminPassword == null || initialAdminPassword.isBlank()) {
            log.warn("系統還沒有管理員帳號，請設定環境變數 INITIAL_ADMIN_PASSWORD 後重新啟動，"
                    + "會建立管理員 {}", INITIAL_ADMIN_USERNAME);
            return;
        }
        userRepository.save(User.builder()
                .username(INITIAL_ADMIN_USERNAME)
                .password(passwordEncoder.encode(initialAdminPassword))
                .name("系統管理員")
                .role(UserRole.ADMIN)
                .isActive(true)
                .build());
        log.info("已建立第一個管理員帳號 {}，登入後請到帳號設定改密碼", INITIAL_ADMIN_USERNAME);
    }
}
