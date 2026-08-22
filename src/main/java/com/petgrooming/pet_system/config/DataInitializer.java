package com.petgrooming.pet_system.config;

import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.BankAccountInfo;
import com.petgrooming.pet_system.model.BonusTier;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.BankAccountInfoRepository;
import com.petgrooming.pet_system.repository.BonusTierRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 資安修正 / 架構調整（2026-08-22）：服務項目（GroomingItem）相關的種子資料、
 * 套餐組成、以及各種「每次啟動都跑一次」的資料校正邏輯，已經全部搬到 Flyway
 * 遷移檔案（見 src/main/resources/db/migration/V1~V7），改由
 * {@link DataSeedMigrationRunner} 負責執行。
 *
 * 搬移原因：這裡原本的寫法（用 groomingItemRepository.count()==0 判斷「只在全新
 * 資料庫時建立」）已經造成過兩次事故——正式站資料庫早就不是空的，count()==0
 * 永遠不成立，新增的種子資料實際上從來沒有真正被種進正式站過，直到改用
 * existsByItemCode() 逐筆檢查才修正。Flyway 用資料庫本身的
 * flyway_schema_history 表追蹤每個遷移檔案「保證只執行一次」，不用再靠
 * 這種手刻的條件判斷去模擬，從根本上解決這個反覆出現的問題。
 *
 * 這個檔案現在只保留「跟 GroomingItem 無關」的種子資料：預設帳號、積分獎勵金
 * 級距、匯款收款帳戶、固定公休星期——這幾項本來就沒有踩過上面那個坑（要嘛用
 * existsByUsername()/findByPurpose().isEmpty() 這種正確的逐筆檢查，要嘛是
 * 「建立一次讓店家後續自行在後台調整」這種真正只需要跑一次的情境，沒有
 * Flyway 要解決的那種「需要持續新增資料」的問題，所以保留原本寫法即可，
 * 不用跟著搬。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BonusTierRepository bonusTierRepository;
    private final BankAccountInfoRepository bankAccountInfoRepository;
    private final com.petgrooming.pet_system.repository.WeeklyClosureSettingRepository weeklyClosureSettingRepository; // 固定公休星期
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

        // 需求 10：店家匯款帳號資訊，僅在還沒設定過時建立預設（佔位）資料，店家可自行到後台修改
        // 需求（追加）：改成兩組獨立帳戶——結帳收款 / 儲值金收款（大額專用），各自檢查、各自補齊
        if (bankAccountInfoRepository.findByPurpose(com.petgrooming.pet_system.enums.BankAccountPurpose.CHECKOUT).isEmpty()) {
            bankAccountInfoRepository.save(BankAccountInfo.builder()
                    .purpose(com.petgrooming.pet_system.enums.BankAccountPurpose.CHECKOUT)
                    .bankName("請於後台設定銀行名稱")
                    .accountNumber("請於後台設定帳號")
                    .accountHolder("請於後台設定戶名")
                    .build());
            log.info("結帳收款帳號預設資料初始化完成，請記得到後台修改成實際帳號");
        }
        if (bankAccountInfoRepository.findByPurpose(com.petgrooming.pet_system.enums.BankAccountPurpose.TOPUP).isEmpty()) {
            bankAccountInfoRepository.save(BankAccountInfo.builder()
                    .purpose(com.petgrooming.pet_system.enums.BankAccountPurpose.TOPUP)
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
            weeklyClosureSettingRepository.save(com.petgrooming.pet_system.model.WeeklyClosureSetting.builder()
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
                log.warn("⚠️⚠️⚠️ 帳號 {}（{}）目前密碼仍是原始碼裡的預設值，這組帳密已經公開在 GitHub repo 上，" +
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
