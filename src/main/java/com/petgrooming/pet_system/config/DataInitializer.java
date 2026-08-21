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
    private final com.petgrooming.pet_system.repository.GroomingItemComponentRepository groomingItemComponentRepository; // 需求（追加）：套餐組成
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

        // ── 需求（追加）：貓咪 24 種組合服務項目 + 2 個加購項目（種子資料）──────
        // 用獨立的 CAT### 代碼區段，跟既有的 GS001~GS012、BATH_S 等代碼完全不會撞號。
        // 只在還沒建過的時候才建（用第一筆 CAT001 判斷），店家之後在後台改名/改價都不受影響，
        // 不會被這段種子資料覆蓋回去。
        if (!groomingItemRepository.existsByItemCode("CAT001")) {
            // 初體驗價目表（12 項）
            saveItem("CAT001", "小美容-單層毛-初體驗", "精緻洗，單層毛貓咪初次到店價格", 990.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT002", "小美容-雙層毛-初體驗", "精緻洗，雙層毛貓咪初次到店價格", 1150.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT003", "小美容-長毛貓-初體驗", "精緻洗，長毛貓咪初次到店價格", 1400.0, PerformanceCategory.BATH_CAT_L);
            saveItem("CAT004", "大美容-單層毛-初體驗", "洗+剃，單層毛貓咪初次到店價格", 1980.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT005", "大美容-雙層毛-初體驗", "洗+剃，雙層毛貓咪初次到店價格", 1980.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT006", "大美容-長毛貓-初體驗", "洗+剃，長毛貓咪初次到店價格", 2150.0, PerformanceCategory.BATH_CAT_L);
            saveItem("CAT007", "頂級專業定制洗護-單層毛-初體驗", "單層毛貓咪初次到店價格", 1800.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT008", "頂級專業定制洗護-雙層毛-初體驗", "雙層毛貓咪初次到店價格", 2150.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT009", "頂級專業定制洗護-長毛貓-初體驗", "長毛貓咪初次到店價格", 2500.0, PerformanceCategory.BATH_CAT_L);
            saveItem("CAT010", "大美容+定制洗護-單層毛-初體驗", "單層毛貓咪初次到店價格", 2500.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT011", "大美容+定制洗護-雙層毛-初體驗", "雙層毛貓咪初次到店價格", 2500.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT012", "大美容+定制洗護-長毛貓-初體驗", "長毛貓咪初次到店價格", 2700.0, PerformanceCategory.BATH_CAT_L);

            // 單次服務價目表（12 項）
            saveItem("CAT013", "小美容-單層毛-單次", "精緻洗，單層毛貓咪單次服務價格", 1100.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT014", "小美容-雙層毛-單次", "精緻洗，雙層毛貓咪單次服務價格", 1300.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT015", "小美容-長毛貓-單次", "精緻洗，長毛貓咪單次服務價格", 1600.0, PerformanceCategory.BATH_CAT_L);
            saveItem("CAT016", "大美容-單層毛-單次", "洗+剃，單層毛貓咪單次服務價格", 2200.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT017", "大美容-雙層毛-單次", "洗+剃，雙層毛貓咪單次服務價格", 2200.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT018", "大美容-長毛貓-單次", "洗+剃，長毛貓咪單次服務價格", 2400.0, PerformanceCategory.BATH_CAT_L);
            saveItem("CAT019", "頂級專業定制洗護-單層毛-單次", "單層毛貓咪單次服務價格", 2000.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT020", "頂級專業定制洗護-雙層毛-單次", "雙層毛貓咪單次服務價格", 2400.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT021", "頂級專業定制洗護-長毛貓-單次", "長毛貓咪單次服務價格", 2800.0, PerformanceCategory.BATH_CAT_L);
            saveItem("CAT022", "大美容+定制洗護-單層毛-單次", "單層毛貓咪單次服務價格", 2800.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT023", "大美容+定制洗護-雙層毛-單次", "雙層毛貓咪單次服務價格", 2800.0, PerformanceCategory.BATH_CAT_S);
            saveItem("CAT024", "大美容+定制洗護-長毛貓-單次", "長毛貓咪單次服務價格", 3000.0, PerformanceCategory.BATH_CAT_L);

            // 加購項目（2 項，不計積分、不打折）
            saveItem("CAT025", "牙齒清潔（進階）", "貓咪加購項目", 100.0, PerformanceCategory.OTHER);
            saveItem("CAT026", "天然草本泥浴", "貓咪加購項目", 1200.0, PerformanceCategory.OTHER);

            log.info("✨ [系統通知] 貓咪 26 項服務/加購項目已成功初始化入庫！");
        }

        // ── 需求（追加）：貓咪基礎保養（低銷選項）+ 長毛貓加購項目（2 項）─────────
        // 分開一個獨立區塊（不是塞進上面 CAT001 那個判斷式），因為上面那個判斷式
        // 只在「完全沒種過」時才會跑，CAT001~026 已經上線過的環境不會再重新執行；
        // 這裡用自己的 CAT027 判斷，不管上面那批種過沒有都會生效。
        // 只做基礎美容，不含洗澡/吹毛，所以沒有主組成一定要用 saveItem() 的固定寫法，
        // 這裡直接手動建立才能一併設定 requiresExistingCustomer。
        if (!groomingItemRepository.existsByItemCode("CAT027")) {
            GroomingItem catBasicCare = GroomingItem.builder()
                    .itemCode("CAT027")
                    .name("貓咪基礎保養")
                    .description("剃腳底毛、肛門周邊毛髮修整、耳道清潔、牙齒清潔、修剪指甲；僅限既有客戶，不適用初次來店貓咪")
                    .price(400.0)
                    .isDeleted(false)
                    .performanceCategory(PerformanceCategory.BASIC)
                    .points(PerformanceCategory.BASIC.getDefaultPoints())
                    .requiresExistingCustomer(true) // 需求（追加）：不適用初次來店貓咪
                    .build();
            groomingItemRepository.save(catBasicCare);

            GroomingItem catBasicCareAddon = GroomingItem.builder()
                    .itemCode("CAT028")
                    .name("修剪圓圓饅頭腳（長毛貓加購）")
                    .description("搭配貓咪基礎保養加購，僅限長毛貓")
                    .price(100.0)
                    .isDeleted(false)
                    .performanceCategory(PerformanceCategory.OTHER)
                    .points(0.0)
                    .discountEligible(false)
                    .requiresExistingCustomer(true)
                    .build();
            groomingItemRepository.save(catBasicCareAddon);

            log.info("✨ [系統通知] 貓咪基礎保養（低銷）+ 長毛貓加購項目已成功初始化入庫！");
        }

        // 需求（追加）：CAT013~024 描述文字裡原本寫「回訪價格」，容易讓人誤以為選這個
        // 項目本身就代表回洗優惠有沒有觸發——這是兩件事，折扣資格是系統依「距上次洗澡
        // 是否在90天內」自動判斷（回洗名單那套邏輯），跟店員/顧客選了哪個價格級距無關。
        // 每次啟動都校正，不用等全新安裝才生效。
        java.util.Map<String, String> catDescriptionFix = new java.util.HashMap<>();
        catDescriptionFix.put("CAT013", "精緻洗，單層毛貓咪單次服務價格");
        catDescriptionFix.put("CAT014", "精緻洗，雙層毛貓咪單次服務價格");
        catDescriptionFix.put("CAT015", "精緻洗，長毛貓咪單次服務價格");
        catDescriptionFix.put("CAT016", "洗+剃，單層毛貓咪單次服務價格");
        catDescriptionFix.put("CAT017", "洗+剃，雙層毛貓咪單次服務價格");
        catDescriptionFix.put("CAT018", "洗+剃，長毛貓咪單次服務價格");
        catDescriptionFix.put("CAT019", "單層毛貓咪單次服務價格");
        catDescriptionFix.put("CAT020", "雙層毛貓咪單次服務價格");
        catDescriptionFix.put("CAT021", "長毛貓咪單次服務價格");
        catDescriptionFix.put("CAT022", "單層毛貓咪單次服務價格");
        catDescriptionFix.put("CAT023", "雙層毛貓咪單次服務價格");
        catDescriptionFix.put("CAT024", "長毛貓咪單次服務價格");
        catDescriptionFix.forEach((code, desc) ->
                groomingItemRepository.findByItemCode(code).ifPresent(item -> {
                    if (!desc.equals(item.getDescription())) {
                        item.setDescription(desc);
                        groomingItemRepository.save(item);
                    }
                }));

        // 需求（追加）：菜單只顯示套餐，不要讓「洗澡/吹毛」這種單一積分分類的舊項目
        // 混在選單裡跟套餐並列選——這些是套餐化改版之前的舊資料（BATH_S/BATH_L/
        // BATH_CS/BATH_CL/BLOW_S/BLOW_L/BLOW_CS/BLOW_CL），現在積分已經改成套餐
        // 自動展開的副組成在算，不需要也不該讓人單獨選這幾項。用「下架」（isDeleted=true）
        // 處理，沿用既有的下架機制，歷史交易紀錄不受影響（品項名稱/價格本來就是快照）。
        for (String code : java.util.List.of(
                "BATH_S", "BATH_L", "BATH_CS", "BATH_CL", "BLOW_S", "BLOW_L", "BLOW_CS", "BLOW_CL")) {
            groomingItemRepository.findByItemCode(code).ifPresent(item -> {
                if (!item.isDeleted()) {
                    item.setDeleted(true);
                    groomingItemRepository.save(item);
                    log.info("已下架舊版單一積分分類項目：{}", code);
                }
            });
        }

        // 需求（追加）：CAT025/CAT026 是加購項目，不應該參與任何折扣，跟 GS001~GS012 一樣道理，
        // 這裡比照上面既有的雙向校正邏輯，一併補進不打折清單，每次啟動都會重新校正。
        java.util.List<String> catNonDiscountCodes = java.util.List.of("CAT025", "CAT026");
        for (String code : catNonDiscountCodes) {
            groomingItemRepository.findByItemCode(code).ifPresent(item -> {
                if (item.isDiscountEligible()) {
                    item.setDiscountEligible(false);
                    groomingItemRepository.save(item);
                    log.info("已校正項目 {} 的折扣資格為 false", code);
                }
            });
        }

        // 需求（追加）：DOG019~021（中大型犬-短毛）之前誤判成大狗積分，這裡每次啟動都校正一次，
        // 不管 DOG001~036 是不是已經種過了都會生效（比照上面 CAT025/026 折扣校正的做法）。
        java.util.Map<String, PerformanceCategory> dogCategoryFix = java.util.Map.of(
                "DOG019", PerformanceCategory.BATH_SMALL,
                "DOG020", PerformanceCategory.BATH_SMALL,
                "DOG021", PerformanceCategory.BATH_SMALL
        );
        dogCategoryFix.forEach((code, correctCategory) ->
                groomingItemRepository.findByItemCode(code).ifPresent(item -> {
                    if (item.getPerformanceCategory() != correctCategory) {
                        item.setPerformanceCategory(correctCategory);
                        groomingItemRepository.save(item);
                        log.info("已校正項目 {} 的績效分類為 {}", code, correctCategory);
                    }
                }));

        // ── 需求（追加）：狗狗初體驗單次價目表——固定價格部分（36 項）─────────
        // 6 個體重級距 × 短毛/長毛 × 3 個服務等級（精緻洗／基礎定制調理／中階定制調理）。
        // 「基礎/中階定制調理」欄位在價目表上是「加價多少」，這裡已經換算成「精緻洗價格 + 加價」
        // 的實際總價存進去，不是店家看到的那個加價數字本身。
        // 體型分類：小型/中小型/中型 → BATH_SMALL；中大型/大型/特大型 → BATH_LARGE
        // （對應「體重定價門檻設定」裡小型犬/大型犬的分野，只影響積分分類跟首次體驗優惠判斷，
        // 不影響這裡每一項各自的實際定價）。
        // ⚠️ 「高階定制調理」（Xup，開放式報價）跟各種浮動加價（厚毛/長毛/剪毛方式/特殊情況）
        // 目前系統沒有「現場自訂金額加購」的機制，先不建這部分，等這個新功能做完再處理。
        if (!groomingItemRepository.existsByItemCode("DOG001")) {
            // 小型 5kg以下
            saveItem("DOG001", "小型犬-短毛-精緻洗", "體重5kg以下，短毛", 700.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG002", "小型犬-短毛-基礎定制調理", "體重5kg以下，短毛（精緻洗+300）", 1000.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG003", "小型犬-短毛-中階定制調理", "體重5kg以下，短毛（精緻洗+500）", 1200.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG004", "小型犬-長毛-精緻洗", "體重5kg以下，長毛", 800.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG005", "小型犬-長毛-基礎定制調理", "體重5kg以下，長毛（精緻洗+300）", 1100.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG006", "小型犬-長毛-中階定制調理", "體重5kg以下，長毛（精緻洗+500）", 1300.0, PerformanceCategory.BATH_SMALL);

            // 中小型 6-10kg
            saveItem("DOG007", "中小型犬-短毛-精緻洗", "體重6-10kg，短毛", 800.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG008", "中小型犬-短毛-基礎定制調理", "體重6-10kg，短毛（精緻洗+1100）", 1900.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG009", "中小型犬-短毛-中階定制調理", "體重6-10kg，短毛（精緻洗+1300）", 2100.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG010", "中小型犬-長毛-精緻洗", "體重6-10kg，長毛", 1200.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG011", "中小型犬-長毛-基礎定制調理", "體重6-10kg，長毛（精緻洗+1500）", 2700.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG012", "中小型犬-長毛-中階定制調理", "體重6-10kg，長毛（精緻洗+1700）", 2900.0, PerformanceCategory.BATH_SMALL);

            // 中型 11-16kg
            saveItem("DOG013", "中型犬-短毛-精緻洗", "體重11-16kg，短毛", 1000.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG014", "中型犬-短毛-基礎定制調理", "體重11-16kg，短毛（精緻洗+1300）", 2300.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG015", "中型犬-短毛-中階定制調理", "體重11-16kg，短毛（精緻洗+1500）", 2500.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG016", "中型犬-長毛-精緻洗", "體重11-16kg，長毛", 1400.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG017", "中型犬-長毛-基礎定制調理", "體重11-16kg，長毛（精緻洗+1700）", 3100.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG018", "中型犬-長毛-中階定制調理", "體重11-16kg，長毛（精緻洗+2000）", 3400.0, PerformanceCategory.BATH_SMALL);

            // 中大型 17-22kg
            // 需求（追加）修正：短毛的小型/大型門檻是23kg（見「體重定價門檻設定」），
            // 17-22kg 整段都在23kg以下，積分要算小狗（BATH_SMALL），不是大狗。
            // 長毛門檻是17kg，17-22kg整段超過門檻，維持算大狗（BATH_LARGE）沒有錯。
            saveItem("DOG019", "中大型犬-短毛-精緻洗", "體重17-22kg，短毛", 1200.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG020", "中大型犬-短毛-基礎定制調理", "體重17-22kg，短毛（精緻洗+1500）", 2700.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG021", "中大型犬-短毛-中階定制調理", "體重17-22kg，短毛（精緻洗+1800）", 3000.0, PerformanceCategory.BATH_SMALL);
            saveItem("DOG022", "中大型犬-長毛-精緻洗", "體重17-22kg，長毛", 1800.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG023", "中大型犬-長毛-基礎定制調理", "體重17-22kg，長毛（精緻洗+2200）", 4000.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG024", "中大型犬-長毛-中階定制調理", "體重17-22kg，長毛（精緻洗+2600）", 4400.0, PerformanceCategory.BATH_LARGE);

            // 大型 23-27kg
            saveItem("DOG025", "大型犬-短毛-精緻洗", "體重23-27kg，短毛", 1600.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG026", "大型犬-短毛-基礎定制調理", "體重23-27kg，短毛（精緻洗+2000）", 3600.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG027", "大型犬-短毛-中階定制調理", "體重23-27kg，短毛（精緻洗+2400）", 4000.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG028", "大型犬-長毛-精緻洗", "體重23-27kg，長毛", 2200.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG029", "大型犬-長毛-基礎定制調理", "體重23-27kg，長毛（精緻洗+2600）", 4800.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG030", "大型犬-長毛-中階定制調理", "體重23-27kg，長毛（精緻洗+3000）", 5200.0, PerformanceCategory.BATH_LARGE);

            // 特大型 28-33kg
            saveItem("DOG031", "特大型犬-短毛-精緻洗", "體重28-33kg，短毛", 2000.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG032", "特大型犬-短毛-基礎定制調理", "體重28-33kg，短毛（精緻洗+2400）", 4400.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG033", "特大型犬-短毛-中階定制調理", "體重28-33kg，短毛（精緻洗+3000）", 5000.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG034", "特大型犬-長毛-精緻洗", "體重28-33kg，長毛", 2600.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG035", "特大型犬-長毛-基礎定制調理", "體重28-33kg，長毛（精緻洗+3200）", 5800.0, PerformanceCategory.BATH_LARGE);
            saveItem("DOG036", "特大型犬-長毛-中階定制調理", "體重28-33kg，長毛（精緻洗+3800）", 6400.0, PerformanceCategory.BATH_LARGE);

            log.info("✨ [系統通知] 狗狗 36 項固定價格服務項目已成功初始化入庫！");
        }

        // ── 需求（追加）：套餐化——回填「適用物種」+ 建立套餐組成 ─────────────
        // 這兩段都是每次啟動都會跑（不是只在全新安裝時跑一次），確保不管上面的
        // CAT001/DOG001 判斷式有沒有觸發過，這裡都會把缺的資料補齊，
        // 也不會覆蓋已經跑過的資料（用「存不存在」判斷，不會重複新增）。

        // 貓咪 26+2 項、狗狗 36 項回填適用物種（之前建立時漏設，null 代表兩者皆可，
        // 導致貓狗選單過濾對這批舊資料完全沒作用）。
        for (String code : java.util.List.of(
                "CAT001","CAT002","CAT003","CAT004","CAT005","CAT006","CAT007","CAT008","CAT009","CAT010",
                "CAT011","CAT012","CAT013","CAT014","CAT015","CAT016","CAT017","CAT018","CAT019","CAT020",
                "CAT021","CAT022","CAT023","CAT024","CAT025","CAT026","CAT027","CAT028")) {
            groomingItemRepository.findByItemCode(code).ifPresent(item -> {
                if (item.getApplicablePetType() != com.petgrooming.pet_system.enums.PetType.CAT) {
                    item.setApplicablePetType(com.petgrooming.pet_system.enums.PetType.CAT);
                    groomingItemRepository.save(item);
                }
            });
        }
        for (int i = 1; i <= 36; i++) {
            String code = String.format("DOG%03d", i);
            groomingItemRepository.findByItemCode(code).ifPresent(item -> {
                if (item.getApplicablePetType() != com.petgrooming.pet_system.enums.PetType.DOG) {
                    item.setApplicablePetType(com.petgrooming.pet_system.enums.PetType.DOG);
                    groomingItemRepository.save(item);
                }
            });
        }

        // 套餐組成：貓咪 24 項（CAT001~024），依「服務深度」決定副組成——
        // 小美容=吹貓+基礎美容；大美容=+剪毛；頂級專業定制洗護=+AD；大美容+定制洗護=+剪毛+AD。
        // 吹貓的大小（BLOW_CAT_S/L）要跟主組成（洗貓）的大小一致。
        addComponents("CAT001", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC);
        addComponents("CAT002", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC);
        addComponents("CAT003", PerformanceCategory.BLOW_CAT_L, PerformanceCategory.BASIC);
        addComponents("CAT004", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.TRIM);
        addComponents("CAT005", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.TRIM);
        addComponents("CAT006", PerformanceCategory.BLOW_CAT_L, PerformanceCategory.BASIC, PerformanceCategory.TRIM);
        addComponents("CAT007", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.AD);
        addComponents("CAT008", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.AD);
        addComponents("CAT009", PerformanceCategory.BLOW_CAT_L, PerformanceCategory.BASIC, PerformanceCategory.AD);
        addComponents("CAT010", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.TRIM, PerformanceCategory.AD);
        addComponents("CAT011", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.TRIM, PerformanceCategory.AD);
        addComponents("CAT012", PerformanceCategory.BLOW_CAT_L, PerformanceCategory.BASIC, PerformanceCategory.TRIM, PerformanceCategory.AD);
        addComponents("CAT013", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC);
        addComponents("CAT014", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC);
        addComponents("CAT015", PerformanceCategory.BLOW_CAT_L, PerformanceCategory.BASIC);
        addComponents("CAT016", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.TRIM);
        addComponents("CAT017", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.TRIM);
        addComponents("CAT018", PerformanceCategory.BLOW_CAT_L, PerformanceCategory.BASIC, PerformanceCategory.TRIM);
        addComponents("CAT019", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.AD);
        addComponents("CAT020", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.AD);
        addComponents("CAT021", PerformanceCategory.BLOW_CAT_L, PerformanceCategory.BASIC, PerformanceCategory.AD);
        addComponents("CAT022", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.TRIM, PerformanceCategory.AD);
        addComponents("CAT023", PerformanceCategory.BLOW_CAT_S, PerformanceCategory.BASIC, PerformanceCategory.TRIM, PerformanceCategory.AD);
        addComponents("CAT024", PerformanceCategory.BLOW_CAT_L, PerformanceCategory.BASIC, PerformanceCategory.TRIM, PerformanceCategory.AD);
        // CAT025~028 是加購/低銷單一分類項目，沒有副組成。

        // 套餐組成：狗狗 36 項（DOG001~036），每 3 筆一組（精緻洗／基礎定制調理／中階定制調理），
        // 精緻洗跟基礎定制調理組成相同（只差在積分分類/價格反映服務深度，不是分類本身），
        // 中階定制調理額外加 AD。吹狗大小跟著主組成（洗狗）的大小走。
        addDogTripletComponents(1, PerformanceCategory.BATH_SMALL);   // DOG001-003 小型-短毛
        addDogTripletComponents(4, PerformanceCategory.BATH_SMALL);   // DOG004-006 小型-長毛
        addDogTripletComponents(7, PerformanceCategory.BATH_SMALL);   // DOG007-009 中小型-短毛
        addDogTripletComponents(10, PerformanceCategory.BATH_SMALL);  // DOG010-012 中小型-長毛
        addDogTripletComponents(13, PerformanceCategory.BATH_SMALL);  // DOG013-015 中型-短毛
        addDogTripletComponents(16, PerformanceCategory.BATH_SMALL);  // DOG016-018 中型-長毛
        addDogTripletComponents(19, PerformanceCategory.BATH_SMALL);  // DOG019-021 中大型-短毛（依23kg門檻算小狗）
        addDogTripletComponents(22, PerformanceCategory.BATH_LARGE);  // DOG022-024 中大型-長毛
        addDogTripletComponents(25, PerformanceCategory.BATH_LARGE);  // DOG025-027 大型-短毛
        addDogTripletComponents(28, PerformanceCategory.BATH_LARGE);  // DOG028-030 大型-長毛
        addDogTripletComponents(31, PerformanceCategory.BATH_LARGE);  // DOG031-033 特大型-短毛
        addDogTripletComponents(34, PerformanceCategory.BATH_LARGE);  // DOG034-036 特大型-長毛

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

    // 需求（追加）：套餐化——幫某個服務項目建立副組成（不重複建，已經有組成資料的項目跳過）。
    private void addComponents(String itemCode, PerformanceCategory... categories) {
        var itemOpt = groomingItemRepository.findByItemCode(itemCode);
        if (itemOpt.isEmpty()) return;
        var item = itemOpt.get();
        if (!groomingItemComponentRepository.findByGroomingItemId(item.getId()).isEmpty()) return;
        for (PerformanceCategory cat : categories) {
            groomingItemComponentRepository.save(com.petgrooming.pet_system.model.GroomingItemComponent.builder()
                    .groomingItem(item)
                    .performanceCategory(cat)
                    .points(cat.getDefaultPoints())
                    .build());
        }
    }

    // 需求（追加）：狗狗價目表每 3 筆一組（精緻洗／基礎定制調理／中階定制調理），
    // 前兩筆組成相同，第三筆多一個 AD；吹狗大小跟著傳入的 size（BATH_SMALL/BATH_LARGE）決定。
    private void addDogTripletComponents(int startIndex, PerformanceCategory size) {
        PerformanceCategory blow = size == PerformanceCategory.BATH_SMALL
                ? PerformanceCategory.BLOW_SMALL : PerformanceCategory.BLOW_LARGE;
        addComponents(String.format("DOG%03d", startIndex), blow, PerformanceCategory.BASIC);
        addComponents(String.format("DOG%03d", startIndex + 1), blow, PerformanceCategory.BASIC);
        addComponents(String.format("DOG%03d", startIndex + 2), blow, PerformanceCategory.BASIC, PerformanceCategory.AD);
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