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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 啟動時建立「建立一次、之後由店家在後台自行調整」的種子資料：預設帳號、積分獎勵金級距、
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

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 1. 初始化預設帳號
        createIfNotExists("admin@pet.com", "admin123", "系統管理員", UserRole.ADMIN);
        createIfNotExists("staff@pet.com", "staff123", "美容師小洪", UserRole.STAFF);
        createIfNotExists("user@pet.com", "user123", "測試會員", UserRole.CUSTOMER);
        log.info("預設帳號初始化完成");

        // 資安修正：這三組帳密是寫死在原始碼裡的固定值，而且這個 repo 是公開的，
        // 任何人都能在 GitHub 上看到。只在「本來就沒有這個帳號」時才會建立
        // （createIfNotExists 本身已經保證這件事），但如果店家從來沒有登入
        // 把密碼改掉，正式站等於任何人都能直接用這組公開密碼登入拿到對應權限。
        // 每次啟動都檢查一次目前密碼是否仍是這組已公開的預設值，是的話在啟動
        // log 印出無法忽略的警告，提醒店家立刻上後台改密碼。
        warnIfDefaultPasswordStillActive("admin@pet.com", "admin123", "系統管理員（ADMIN，權限最高）");
        warnIfDefaultPasswordStillActive("staff@pet.com", "staff123", "美容師小洪（STAFF）");
        warnIfDefaultPasswordStillActive("user@pet.com", "user123", "測試會員（CUSTOMER）");

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

    // 資安修正：檢查目前密碼是否仍等於原始碼裡寫死、且已公開在 GitHub 上的預設值。
    // 用 passwordEncoder.matches() 而不是直接比對雜湊字串，因為 bcrypt 每次加鹽結果不同，
    // 沒辦法用字串相等判斷，一定要透過 encoder 本身的比對方法。
    private void warnIfDefaultPasswordStillActive(String username, String defaultPassword, String roleDescription) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (passwordEncoder.matches(defaultPassword, user.getPassword())) {
                log.warn("帳號 {}（{}）目前密碼仍是原始碼裡的預設值，這組帳密已經公開在 GitHub repo 上，" +
                        "任何人都看得到！請立刻登入後台（帳號設定 → 修改密碼）改成一組別人猜不到的新密碼。",
                        username, roleDescription);
            }
        });
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
