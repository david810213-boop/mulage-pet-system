package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.ConsumptionImportRow;
import com.petgrooming.pet_system.dto.MemberImportResult;
import com.petgrooming.pet_system.dto.MemberImportRow;
import com.petgrooming.pet_system.dto.SimpleImportResult;
import com.petgrooming.pet_system.dto.WalletImportRow;
import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.enums.PetSizeCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.Map;

/**
 * 店家轉型，既有紙本/舊系統會員資料批次匯入。
 *
 * 匯入邏輯：
 * - CSV 一列＝一隻毛孩，依「電話號碼」分組，同一組只建立一筆會員帳號，底下掛多隻寵物
 * - 電話號碼在系統裡已經存在的（不管是已經真的用過 LINE 登入、或之前已經匯入過），
 *   整組直接跳過，不覆蓋、不重複建立——這份匯入功能設計成可以重複執行同一份 CSV
 *   也不會出錯或製造重複資料，方便店家分批匯入或匯入失敗後重跑
 * - 匯入建立的帳號 lineUserId 是 null（還沒被任何人認領），username 用
 *   「imported_電話號碼」這種內部識別碼（顧客不會用這個登入，只是資料庫裡需要
 *   一個唯一值），之後靠 {@link #claimByPhone} 讓顧客自己用 LINE 登入認領
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberImportService {

    private final UserRepository userRepository;
    private final MemberCsvParser memberCsvParser;
    private final PetRepository petRepository;
    private final PetService petService; // 重用 resolveCatCoatCategory
    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository; // 儲值餘額匯入
    private final WalkInOrderRepository walkInOrderRepository; // 消費紀錄匯入

    private static final Set<String> CAT_LABELS = Set.of("貓", "CAT", "cat");
    private static final Set<String> DOG_LABELS = Set.of("狗", "DOG", "dog");
    private static final Set<String> TRUE_LABELS = Set.of("是", "Y", "y", "TRUE", "true", "1");

    @Transactional
    public MemberImportResult importFromCsv(MultipartFile file) throws IOException {
        List<MemberImportRow> rows = memberCsvParser.parseCsv(file);

        List<String> rowErrors = new ArrayList<>();
        List<MemberImportRow> validRows = new ArrayList<>();
        for (MemberImportRow row : rows) {
            String error = validateRow(row);
            if (error != null) {
                rowErrors.add("第 " + row.getRowNumber() + " 列寵物資料解析失敗：" + error);
            } else {
                validRows.add(row);
            }
        }

        // 依電話號碼分組（同一位家長可能有好幾隻毛孩，對應好幾列）
        Map<String, List<MemberImportRow>> grouped = new LinkedHashMap<>();
        for (MemberImportRow row : validRows) {
            grouped.computeIfAbsent(normalizePhone(row.getPhone()), k -> new ArrayList<>()).add(row);
        }

        int membersCreated = 0;
        int petsCreated = 0;
        List<String> skippedPhones = new ArrayList<>();

        for (var entry : grouped.entrySet()) {
            String phone = entry.getKey();
            List<MemberImportRow> petRows = entry.getValue();

            if (userRepository.findByPhone(phone).isPresent()) {
                // 這支電話已經是系統裡的會員（不管是已經用 LINE 登入過、還是之前匯入過），
                // 整組跳過，不覆蓋既有資料——這是這份匯入功能可以重複執行的關鍵防呆。
                skippedPhones.add(phone);
                continue;
            }

            User owner = User.builder()
                    .username("imported_" + phone)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .name(petRows.get(0).getOwnerName())
                    .role(UserRole.CUSTOMER)
                    .phone(phone)
                    .isActive(true)
                    .build();
            owner = userRepository.save(owner);
            membersCreated++;

            for (MemberImportRow row : petRows) {
                PetType petType = parsePetType(row.getPetTypeRaw());
                double weight = Double.parseDouble(row.getWeightRaw().trim());
                double age = Double.parseDouble(row.getAgeRaw().trim()); // 允許小數年齡
                boolean anxiety = row.getSeparationAnxietyRaw() != null
                        && TRUE_LABELS.contains(row.getSeparationAnxietyRaw().trim());

                Pet pet = Pet.builder()
                        .name(row.getPetName().trim())
                        .petType(petType)
                        .breed(row.getBreed().trim())
                        .weight(weight)
                        .age(age)
                        .sizeCategory(PetSizeCategory.determine(petType, weight))
                        .catCoatCategory(petService.resolveCatCoatCategory(petType, row.getBreed().trim()))
                        .hasSeparationAnxiety(anxiety)
                        .notes(row.getNotes())
                        .owner(owner)
                        .build();
                petRepository.save(pet);
                petsCreated++;
            }
        }

        log.info("[會員資料匯入] 新建 {} 筆會員、{} 隻寵物，跳過 {} 筆已存在的電話號碼",
                membersCreated, petsCreated, skippedPhones.size());

        return MemberImportResult.builder()
                .totalRows(rows.size())
                .membersCreated(membersCreated)
                .petsCreated(petsCreated)
                .membersSkipped(skippedPhones.size())
                .skippedPhones(skippedPhones)
                .rowErrors(rowErrors)
                .build();
    }

    // ── 既有儲值餘額批次匯入 ─────────────────────────────
    // CSV 欄位：電話,儲值餘額,會員等級,到期日（後兩欄選填）
    // 這支電話必須是已經匯入過（或本來就存在）的會員，找不到就整列報錯。
    // 既有會員的等級與到期日照店家紙本記錄帶入，
    // 等級與到期日是「覆蓋」（不是加總）；只想補等級不想動餘額時，餘額填 0。
    // ⚠️ 用「加總」不是「覆蓋」——避免萬一這支電話的顧客已經自己儲值過（不管是
    // 認領帳號之後自己儲值、或這批匯入分好幾次跑），覆蓋會把顧客已經存進去的
    // 真實金額洗掉。如果要重新匯入同一份餘額資料，請先確認這支電話目前餘額，
    // 自己算好差額再匯入，不要整份原始金額重複匯入第二次。
    @Transactional
    public SimpleImportResult importWalletBalances(MultipartFile file) throws IOException {
        List<WalletImportRow> rows = memberCsvParser.parseWalletCsv(file);
        List<String> errors = new ArrayList<>();
        int succeeded = 0;

        for (WalletImportRow row : rows) {
            String phone = normalizePhone(row.getPhone());
            int balance;
            try {
                balance = Integer.parseInt(row.getBalanceRaw().trim());
                if (balance < 0) throw new NumberFormatException();
            } catch (Exception e) {
                errors.add("第 " + row.getRowNumber() + " 列：儲值餘額格式錯誤：" + row.getBalanceRaw());
                continue;
            }

            // 會員等級＋到期日：兩欄都空白 = 不處理等級；有填等級就一定要有到期日
            MemberCardTier tier = MemberCardTier.fromLabel(row.getTierRaw());
            if (tier == null) {
                errors.add("第 " + row.getRowNumber() + " 列：會員等級看不懂：" + row.getTierRaw()
                        + "（可填：村民優惠方案、普卡、金卡、鑽石卡、慕沐村VIP，沒有等級留空白）");
                continue;
            }
            LocalDate expiresAt = null;
            if (tier == MemberCardTier.VILLAGE) {
                // 村民優惠方案沒有期限，到期日填了也忽略
            } else if (tier != MemberCardTier.NONE) {
                expiresAt = parseLenientDate(row.getExpiresRaw());
                if (expiresAt == null) {
                    errors.add("第 " + row.getRowNumber() + " 列：有填會員等級但到期日空白或格式錯誤（要 2026-09-30 或 2026/9/30）："
                            + row.getExpiresRaw());
                    continue;
                }
            } else if (!row.getExpiresRaw().isBlank()) {
                errors.add("第 " + row.getRowNumber() + " 列：有填到期日但沒填會員等級");
                continue;
            }

            Optional<User> userOpt = userRepository.findByPhone(phone);
            if (userOpt.isEmpty()) {
                errors.add("第 " + row.getRowNumber() + " 列：查無電話 " + phone + " 對應的會員，請先匯入會員資料");
                continue;
            }
            User user = userOpt.get();

            Wallet wallet = walletRepository.findByUserId(user.getId())
                    .orElseGet(() -> walletRepository.save(Wallet.builder().user(user).balance(0).build()));
            wallet.setBalance(wallet.getBalance() + balance);
            if (tier != MemberCardTier.NONE) {
                // 紙本只有到期日，開卡日用「到期日＋1 天往前推一年」回推（到期日＝起算日＋1 年－1 天）；
                // 村民優惠方案沒有期限，開卡日記匯入當天
                wallet.setCardTier(tier);
                wallet.setCardExpiresAt(expiresAt);
                wallet.setCardActivatedAt(expiresAt != null ? expiresAt.plusDays(1).minusYears(1) : LocalDate.now());
            }
            walletRepository.save(wallet);
            succeeded++;
        }

        log.info("[儲值餘額匯入] 成功 {} 筆，錯誤 {} 筆", succeeded, errors.size());
        return SimpleImportResult.builder()
                .totalRows(rows.size())
                .succeeded(succeeded)
                .rowErrors(errors)
                .build();
    }

    // ── 既有消費紀錄批次匯入 ─────────────────────────────
    // CSV 欄位：電話,毛孩名字,消費日期(yyyy-MM-dd),金額,備註
    // 電話+毛孩名字都要能對應到已存在的會員/寵物，找不到就整列報錯。
    //
    // 設計取捨（重要，README 會再提醒一次）：
    // 每一筆匯入的消費紀錄會建立一筆「已結帳」的現場開單（WalkInOrder），底下掛
    // 一個單一項目，分類設為 OTHER、積分固定 0——不計入任何店員的績效積分（畢竟
    // 匯入資料本來就沒有「誰做的」這個資訊，也不該讓現在的店員無端背上不知道
    // 哪來的積分），也因此**不會出現在「待補經手人」矩陣表單裡**（那個表單只
    // 顯示積分>0 的項目），不會被匯入的舊資料洗版。
    //
    // 這筆訂單的建立時間（createdAt）會設成 CSV 給的歷史日期，所以財務報表
    // 用日期區間查詢時，這筆歷史消費「會」被算進當初實際發生的那個月份/日期，
    // 不會出現在「今天」或「這個月」的報表（除非店家真的去查那個歷史月份），
    // 這是符合真實情況的正確行為，不是資料污染。
    //
    // ⚠️ 已知限制：因為是用單一 OTHER 分類項目匯入，不是真正的「洗澡」服務項目，
    // 貓咪 90 天回洗優惠的「上次洗澡日期」判斷不會認得這筆匯入紀錄（那個判斷
    // 只認 BATH_CAT_S/BATH_CAT_L 分類的項目）。如果店家需要匯入的歷史紀錄也能
    // 正確觸發回洗優惠判斷，需要另外處理，目前這版本先不支援，只保證「這是
    // 既有客戶」（hasPriorPaidService）這個判斷會正確生效。
    @Transactional
    public SimpleImportResult importConsumptionHistory(MultipartFile file) throws IOException {
        List<ConsumptionImportRow> rows = memberCsvParser.parseConsumptionCsv(file);
        List<String> errors = new ArrayList<>();
        int succeeded = 0;

        for (ConsumptionImportRow row : rows) {
            String phone = normalizePhone(row.getPhone());

            Optional<User> userOpt = userRepository.findByPhone(phone);
            if (userOpt.isEmpty()) {
                errors.add("第 " + row.getRowNumber() + " 列：查無電話 " + phone + " 對應的會員，請先匯入會員資料");
                continue;
            }
            User user = userOpt.get();

            boolean petExists = petRepository.findByOwnerId(user.getId()).stream()
                    .anyMatch(p -> p.getName().equals(row.getPetName().trim()));
            if (!petExists) {
                errors.add("第 " + row.getRowNumber() + " 列：這位會員名下查無寵物「" + row.getPetName() + "」，請先匯入寵物資料");
                continue;
            }

            LocalDate date;
            int amount;
            try {
                date = LocalDate.parse(row.getDateRaw().trim());
            } catch (Exception e) {
                errors.add("第 " + row.getRowNumber() + " 列：日期格式錯誤（要 yyyy-MM-dd）：" + row.getDateRaw());
                continue;
            }
            try {
                amount = Integer.parseInt(row.getAmountRaw().trim());
                if (amount < 0) throw new NumberFormatException();
            } catch (Exception e) {
                errors.add("第 " + row.getRowNumber() + " 列：金額格式錯誤：" + row.getAmountRaw());
                continue;
            }

            WalkInOrder order = WalkInOrder.builder()
                    .member(user)
                    .petName(row.getPetName().trim())
                    .totalAmount(amount)
                    .chargedAmount(amount)
                    .paid(true)
                    .paymentMethod(PaymentMethod.CASH) // 歷史資料無從得知原始付款方式，固定填現金
                    .createdAt(date.atTime(12, 0)) // 只知道日期不知道時間，統一填中午，避免影響「當日」排序
                    .build();
            order = walkInOrderRepository.save(order);

            WalkInOrderItem item = WalkInOrderItem.builder()
                    .order(order)
                    .itemName(row.getNote() != null && !row.getNote().isBlank()
                            ? "歷史消費（匯入）：" + row.getNote().trim()
                            : "歷史消費紀錄（匯入）")
                    .price(amount)
                    .points(0.0) // 不計積分，見上方類別註解說明
                    .performanceCategory(PerformanceCategory.OTHER)
                    .discountEligible(false)
                    .build();
            order.addItem(item);
            walkInOrderRepository.save(order);

            succeeded++;
        }

        log.info("[消費紀錄匯入] 成功 {} 筆，錯誤 {} 筆", succeeded, errors.size());
        return SimpleImportResult.builder()
                .totalRows(rows.size())
                .succeeded(succeeded)
                .rowErrors(errors)
                .build();
    }


    private String validateRow(MemberImportRow row) {
        // 編碼異常優先判斷：欄位裡出現 U+FFFD（置換字元）代表讀檔時該欄位沒被正確解碼，
        // 這種列直接擋下並講清楚是哪個欄位，不要讓亂碼資料寫進資料庫。
        String garbledField = firstGarbledField(row);
        if (garbledField != null) {
            return "疑似編碼異常，" + garbledField + "欄位內含無法辨識的字元，請將原始 CSV 另存成 UTF-8 或 Big5 後重新上傳";
        }
        if (isBlank(row.getOwnerName())) return "家長姓名欄位為空";
        if (isBlank(row.getPhone())) return "電話欄位為空";
        if (isBlank(row.getPetName())) return "毛孩名字欄位為空";
        if (isBlank(row.getPetTypeRaw())) return "物種欄位為空";
        if (isBlank(row.getBreed())) return "品種欄位為空";
        try {
            double w = Double.parseDouble(row.getWeightRaw().trim());
            if (w <= 0) return "體重欄位必須大於 0";
        } catch (Exception e) {
            return "體重欄位格式錯誤：" + row.getWeightRaw();
        }
        try {
            double a = Double.parseDouble(row.getAgeRaw().trim()); // 允許小數年齡
            if (a < 0) return "年齡欄位不能是負數";
        } catch (Exception e) {
            return "年齡欄位格式錯誤：" + row.getAgeRaw();
        }
        return null;
    }

    // 回傳第一個含有 U+FFFD 置換字元的欄位中文名稱，沒有就回傳 null。
    // 用來在編碼 fallback 仍無法乾淨解碼時，讓錯誤訊息指出是哪一欄出問題。
    private String firstGarbledField(MemberImportRow row) {
        if (containsReplacementChar(row.getOwnerName())) return "家長姓名";
        if (containsReplacementChar(row.getPhone())) return "電話";
        if (containsReplacementChar(row.getPetName())) return "毛孩名字";
        if (containsReplacementChar(row.getPetTypeRaw())) return "物種";
        if (containsReplacementChar(row.getBreed())) return "品種";
        if (containsReplacementChar(row.getNotes())) return "注意事項";
        return null;
    }

    // U+FFFD：解碼器遇到無法對應的位元組時填入的「置換字元」，出現在欄位裡幾乎
    // 一定代表原始檔案編碼跟解碼用的編碼對不上。
    private static final char REPLACEMENT_CHAR = 0xFFFD;

    private boolean containsReplacementChar(String s) {
        return s != null && s.indexOf(REPLACEMENT_CHAR) >= 0;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private PetType parsePetType(String raw) {
        String trimmed = raw.trim();
        if (CAT_LABELS.contains(trimmed)) return PetType.CAT;
        if (DOG_LABELS.contains(trimmed)) return PetType.DOG;
        return PetType.OTHER;
    }

    // 電話號碼正規化：去除空白、破折號，統一格式，避免「0912-345-678」跟「0912345678」
    // 被系統當成兩支不同電話（既無法比對到既有會員、認領時也比對不到）。
    // Excel 儲存格如果設成「一般」或「數字」格式，
    // 使用者輸入 0917684717 這種台灣手機號碼，Excel 會把它當數字存，開頭的 0
    // 是無意義的前導零直接被吃掉，存成 CSV 時原始內容就已經是 917684717，不是
    // 我們這邊解析錯誤。這裡加一道自動修正：只要格式剛好符合「拿掉開頭 0 後的
    // 台灣手機號碼長相」（9 碼、以 9 開頭），就自動補回開頭的 0，減少店家因為
    // Excel 存檔習慣而匯入失敗或資料錯誤的機率。只處理這一種明確的情況，避免
    // 誤判其他本來就不到 10 碼的市話號碼或其他格式。
    // 改成 public static，UserService 填資料防重複也要用同一套正規化
    public static String normalizePhone(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim().replaceAll("[\\s\\-()]", "");
        if (cleaned.matches("9\\d{8}")) {
            cleaned = "0" + cleaned;
        }
        return cleaned;
    }

    // 到期日寬鬆解析——Excel 另存 CSV 常變成 2026/9/30，
    // 所以 yyyy-MM-dd、yyyy/M/d、yyyy-M-d 都接受；看不懂回傳 null。
    private LocalDate parseLenientDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] p = raw.trim().split("[/\\-.]");
        if (p.length != 3) return null;
        try {
            return LocalDate.of(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return null;
        }
    }
}
