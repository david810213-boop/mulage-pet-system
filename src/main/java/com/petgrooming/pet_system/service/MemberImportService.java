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
import com.petgrooming.pet_system.exception.MemberException;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.repository.TopUpRequestRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.model.TopUpRequest;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.utils.CsvImportFileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 需求（追加，2026-08-23）：店家轉型，既有紙本/舊系統會員資料批次匯入。
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
    private final PetRepository petRepository;
    private final PetService petService; // 重用 resolveCatCoatCategory
    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository; // 需求（追加）：儲值餘額匯入
    private final WalkInOrderRepository walkInOrderRepository; // 需求（追加）：消費紀錄匯入
    private final TopUpRequestRepository topUpRequestRepository; // 需求（追加，2026-09-08）：手動綁定
    private final AppointmentRepository appointmentRepository; // 需求（追加，2026-09-08）：手動綁定

    private static final Set<String> CAT_LABELS = Set.of("貓", "CAT", "cat");
    private static final Set<String> DOG_LABELS = Set.of("狗", "DOG", "dog");
    private static final Set<String> TRUE_LABELS = Set.of("是", "Y", "y", "TRUE", "true", "1");

    @Transactional
    public MemberImportResult importFromCsv(MultipartFile file) throws IOException {
        List<MemberImportRow> rows = parseCsv(file);

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
                double age = Double.parseDouble(row.getAgeRaw().trim()); // 需求（追加）：允許小數年齡
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

        log.info("✨ [會員資料匯入] 新建 {} 筆會員、{} 隻寵物，跳過 {} 筆已存在的電話號碼",
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

    // ── 需求（追加）：既有儲值餘額批次匯入 ─────────────────────────────
    // CSV 欄位：電話,儲值餘額,會員等級,到期日（後兩欄選填）
    // 這支電話必須是已經匯入過（或本來就存在）的會員，找不到就整列報錯。
    // 需求（追加，2026-09-30）：既有會員的等級與到期日照店家紙本記錄帶入，
    // 等級與到期日是「覆蓋」（不是加總）；只想補等級不想動餘額時，餘額填 0。
    // ⚠️ 用「加總」不是「覆蓋」——避免萬一這支電話的顧客已經自己儲值過（不管是
    // 認領帳號之後自己儲值、或這批匯入分好幾次跑），覆蓋會把顧客已經存進去的
    // 真實金額洗掉。如果要重新匯入同一份餘額資料，請先確認這支電話目前餘額，
    // 自己算好差額再匯入，不要整份原始金額重複匯入第二次。
    @Transactional
    public SimpleImportResult importWalletBalances(MultipartFile file) throws IOException {
        List<WalletImportRow> rows = parseWalletCsv(file);
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
            if (tier != MemberCardTier.NONE) {
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
                // 紙本只有到期日，開卡日用「到期日往前推一年」回推（規則：每次儲值效期一年）
                wallet.setCardTier(tier);
                wallet.setCardExpiresAt(expiresAt);
                wallet.setCardActivatedAt(expiresAt.minusYears(1));
            }
            walletRepository.save(wallet);
            succeeded++;
        }

        log.info("✨ [儲值餘額匯入] 成功 {} 筆，錯誤 {} 筆", succeeded, errors.size());
        return SimpleImportResult.builder()
                .totalRows(rows.size())
                .succeeded(succeeded)
                .rowErrors(errors)
                .build();
    }

    // ── 需求（追加）：既有消費紀錄批次匯入 ─────────────────────────────
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
    // 不會出現在「今天」或「這個月」的報表（除非店家真的去查那個历史月份），
    // 這是符合真實情況的正確行為，不是資料污染。
    //
    // ⚠️ 已知限制：因為是用單一 OTHER 分類項目匯入，不是真正的「洗澡」服務項目，
    // 貓咪 90 天回洗優惠的「上次洗澡日期」判斷不會認得這筆匯入紀錄（那個判斷
    // 只認 BATH_CAT_S/BATH_CAT_L 分類的項目）。如果店家需要匯入的歷史紀錄也能
    // 正確觸發回洗優惠判斷，需要另外處理，目前這版本先不支援，只保證「這是
    // 既有客戶」（hasPriorPaidService）這個判斷會正確生效。
    @Transactional
    public SimpleImportResult importConsumptionHistory(MultipartFile file) throws IOException {
        List<ConsumptionImportRow> rows = parseConsumptionCsv(file);
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

        log.info("✨ [消費紀錄匯入] 成功 {} 筆，錯誤 {} 筆", succeeded, errors.size());
        return SimpleImportResult.builder()
                .totalRows(rows.size())
                .succeeded(succeeded)
                .rowErrors(errors)
                .build();
    }


    // 用途：顧客第一次用 LINE 登入時系統會自動建一筆空白新帳號（既有機制，不動它），
    // 這裡是額外的動作——把電話號碼比對到的「匯入但還沒被認領」的舊資料，整批
    // 過戶到目前這筆已經綁好 LINE 的帳號上，然後把那筆匯入用的暫時帳號刪掉，
    // 避免產生一個人對應兩筆帳號的重複資料。
    //
    // 安全限制：只有目前這個 LINE 帳號「還沒填過資料」（profileCompletedAt 為 null）
    // 才能認領，避免已經自己填好資料的人不小心把別人的資料領走蓋掉自己的。
    //
    // 需求（追加，2026-09-30）：新客報到改成「先輸入電話分流」，認領時多一道確認——
    // 匯入帳號有毛孩就要輸入其中一隻毛孩的名字，沒有毛孩就輸入登記的姓名。
    // 原因：帳號裡可能有儲值金與會員卡，只憑電話就能認領的話，知道別人電話的人
    // 可以把整個帳號連錢包一起領走。
    @Transactional
    public User claimByPhone(User currentUser, String phone, String verification) {
        User imported = findClaimable(currentUser, phone)
                .orElseThrow(() -> new MemberException("查無符合這支電話的既有會員資料"));
        if (!verifyClaim(imported, verification)) {
            throw new MemberException(hasActivePets(imported)
                    ? "毛孩名字不符，請再確認一次（輸入任一隻登記的毛孩名字即可）"
                    : "姓名不符，請輸入當初在店裡登記的姓名");
        }
        String normalized = normalizePhone(phone);

        // 過戶基本資料（這裡的 currentUser 一定是還沒填過資料的全新帳號，見上面
        // profileCompletedAt 檢查，所以整批覆蓋沒有蓋掉顧客自己資料的風險）
        currentUser.setName(imported.getName());
        currentUser.setPhone(imported.getPhone());
        currentUser.setProfileCompletedAt(java.time.LocalDateTime.now());
        userRepository.save(currentUser);

        TransferSummary summary = transferAllDataAndDeleteSource(imported, currentUser);

        log.info("✨ [會員認領] {} 認領了電話 {} 的匯入資料，過戶 {} 隻寵物",
                currentUser.getUsername(), normalized, summary.pets().size());

        return currentUser;
    }

    // ── 需求（追加，2026-09-30）：新客報到第一步「輸入電話分流」────────────
    // 查到還沒被認領的既有會員 → 回傳遮罩後的姓名、毛孩數、要用什麼確認；
    // 查不到 → matched=false，前端改走一般新客流程。不回傳毛孩名字等可直接
    // 拿來認領的資訊。
    public java.util.Map<String, Object> lookupForClaim(User currentUser, String phone) {
        Optional<User> found = findClaimable(currentUser, phone);
        if (found.isEmpty()) {
            return java.util.Map.of("matched", false);
        }
        User imported = found.get();
        long petCount = activePets(imported).size();
        return java.util.Map.of(
                "matched", true,
                "maskedName", maskName(imported.getName()),
                "petCount", petCount,
                "verifyBy", petCount > 0 ? "PET" : "NAME");
    }

    private Optional<User> findClaimable(User currentUser, String phone) {
        if (currentUser.getProfileCompletedAt() != null) {
            throw new MemberException("這個帳號已經填過資料了，無法再認領其他會員資料");
        }
        if (phone == null || phone.isBlank()) return Optional.empty();
        return userRepository
                .findFirstByPhoneAndLineUserIdIsNullAndRoleOrderByIdAsc(normalizePhone(phone), UserRole.CUSTOMER)
                .filter(u -> !u.getId().equals(currentUser.getId()));
    }

    private List<Pet> activePets(User owner) {
        return petRepository.findByOwnerId(owner.getId()).stream().filter(p -> !p.isDeleted()).toList();
    }

    private boolean hasActivePets(User owner) {
        return !activePets(owner).isEmpty();
    }

    private boolean verifyClaim(User imported, String verification) {
        String input = squash(verification);
        if (input.isEmpty()) return false;
        List<Pet> pets = activePets(imported);
        if (!pets.isEmpty()) {
            return pets.stream().anyMatch(p -> squash(p.getName()).equalsIgnoreCase(input));
        }
        return squash(imported.getName()).equalsIgnoreCase(input);
    }

    // 去掉所有空白（含全形空白）再比對，避免多打一個空格就被判定不符
    private static String squash(String s) {
        return s == null ? "" : s.replaceAll("[\\s\u3000]", "");
    }

    // 王小明 → 王○明、王明 → 王○、歐陽小明 → 歐○○明
    static String maskName(String name) {
        String n = squash(name);
        if (n.isEmpty()) return "（未登記姓名）";
        if (n.length() == 1) return "○";
        if (n.length() == 2) return n.charAt(0) + "○";
        return n.charAt(0) + "○".repeat(n.length() - 2) + n.charAt(n.length() - 1);
    }

    // ── 需求（追加，2026-09-08）：店家後台手動綁定既有匯入資料 ──────────────
    //
    // 使用時機：顧客的 LINE 帳號自己已經先填過個人資料/新增過毛孩（例如測試時
    // 不小心先手動填了，沒有先用電話號碼認領），導致 claimByPhone() 的防呆
    // 擋下自動認領（"這個帳號已經填過資料了，無法再認領其他會員資料"）。這種
    // 情況只能由店家在後台人工判斷「這確實是同一個人」，手動把匯入的暫時帳號
    // 資料合併過去。
    //
    // 跟 claimByPhone() 的差異：
    // ① 不覆蓋目標會員已經填的姓名——目標帳號通常已經有自己填的真實資料，
    //    這是店家人工判斷合併，不該用匯入資料蓋掉顧客自己填寫的內容。電話
    //    則是「目標帳號原本沒填才帶入」（需求追加，2026-09-11）——因為
    //    LINE 帳號常常還沒填過電話，這種情況匯入資料裡的電話應該要補上，
    //    只有目標已經自己填過電話時才不覆蓋
    // ② 實際的資料過戶（寵物/消費/預約/儲值/刪除來源帳號）跟 claimByPhone()
    //    共用同一支 transferAllDataAndDeleteSource()，這是 2026-09-11 修正
    //    claimByPhone() 的 bug 時一併重構的——避免同一套邏輯分別維護兩份，
    //    改一邊漏改另一邊（這正是這次 claimByPhone() bug 一直沒被發現的原因）
    @Transactional
    public void manualMerge(String importedUsername, String targetUsername) {
        User imported = userRepository.findByUsername(importedUsername)
                .orElseThrow(() -> new MemberException("找不到這個匯入帳號：" + importedUsername));
        if (!imported.getUsername().startsWith("imported_")) {
            throw new MemberException("只能合併「既有會員資料匯入」建立的暫時帳號（帳號以 imported_ 開頭），這筆不是");
        }
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new MemberException("找不到目標會員帳號：" + targetUsername));
        if (imported.getId().equals(target.getId())) {
            throw new MemberException("來源跟目標是同一個帳號");
        }
        if (target.getUsername().startsWith("imported_")) {
            throw new MemberException("目標帳號也是匯入用的暫時帳號，請選一個真正在使用的會員帳號");
        }

        // 需求（追加，2026-09-11）：目標帳號原本沒填電話才帶入匯入資料的電話，
        // 已經自己填過電話的話維持不覆蓋——姓名不論如何都不覆蓋。
        if (isBlank(target.getPhone()) && !isBlank(imported.getPhone())) {
            target.setPhone(imported.getPhone());
            userRepository.save(target);
        }

        TransferSummary summary = transferAllDataAndDeleteSource(imported, target);

        log.info("✨ [手動綁定] 店家手動把匯入帳號 {} 的資料合併到會員 {}，過戶 {} 隻寵物、{} 筆消費紀錄、{} 筆預約、{} 筆儲值申請",
                importedUsername, targetUsername, summary.pets().size(), summary.orders().size(),
                summary.appointments().size(), summary.topUps().size());
    }

    // ── 需求（重構，2026-09-11）：claimByPhone() 跟 manualMerge() 共用的過戶邏輯 ──
    //
    // 抽出來的原因：claimByPhone() 原本自己另外寫了一份簡化版（只過戶寵物），
    // manualMerge() 開發時修正了兩個問題（JPA 級聯刪除坑、錢包外鍵未清理），
    // 但沒有回頭同步套用到 claimByPhone()，導致顧客自助認領時如果匯入帳號有
    // 錢包（幾乎每個帳號都有），claimByPhone() 一定會在刪除來源帳號那一步
    // 撞到外鍵限制，整筆操作失敗且錯誤訊息不明確（前端顯示「找不到符合的
    // 資料」，實際上是錢包外鍵擋下刪除，這兩者完全無關）。統一抽成一支方法，
    // 之後不會再有「改一邊漏改另一邊」的風險。
    //
    // 過戶內容：寵物、消費紀錄（現場開單）、預約、待處理儲值申請、儲值餘額
    // （加總，不是覆蓋），最後刪除來源帳號。
    //
    // ⚠️ 這裡處理了兩個真正的 bug：
    // ① User.pets 是 cascade = CascadeType.ALL 的雙向一對多關聯，只透過
    //    petRepository 改 pets 資料表的 owner_id，imported 物件自己記憶體裡的
    //    pets 集合不會同步更新，userRepository.delete(imported) 觸發 cascade
    //    刪除時會把已經過戶的寵物一起級聯刪除掉（已經實際發生過一次資料遺失）。
    //    修法：saveAll 後強制 flush，再明確清空 imported.getPets()，兩步都要做。
    // ② 沒有處理 imported 的錢包，刪除來源帳號時會撞上 wallets 表的外鍵限制，
    //    整個交易在 commit 階段失敗回滾（不會真的遺失資料，但操作會整個失敗，
    //    且拋出的例外訊息跟前端顯示的訊息對不上，很難排查）。修法：跟寵物一樣，
    //    有餘額就加總到目標帳號，再把來源帳號的錢包刪掉。
    private TransferSummary transferAllDataAndDeleteSource(User imported, User target) {
        // 過戶寵物（改 owner，不是複製一份新的）
        List<Pet> pets = petRepository.findByOwnerId(imported.getId());
        for (Pet pet : pets) {
            pet.setOwner(target);
        }
        petRepository.saveAll(pets);
        petRepository.flush();
        imported.getPets().clear();

        // 過戶消費紀錄（現場開單）
        List<WalkInOrder> orders = walkInOrderRepository.findByMemberId(imported.getId());
        for (WalkInOrder order : orders) {
            order.setMember(target);
        }
        walkInOrderRepository.saveAll(orders);

        // 過戶預約紀錄
        List<Appointment> appointments = appointmentRepository.findByUserId(imported.getId());
        for (Appointment appt : appointments) {
            appt.setUser(target);
        }
        appointmentRepository.saveAll(appointments);

        // 過戶待處理/歷史儲值申請
        List<TopUpRequest> topUps = topUpRequestRepository.findByUserId(imported.getId());
        for (TopUpRequest t : topUps) {
            t.setUser(target);
        }
        topUpRequestRepository.saveAll(topUps);

        // 儲值餘額加總到目標會員的錢包，不是覆蓋；沒有餘額也要把來源帳號的
        // 空錢包刪掉，不然會卡外鍵讓下面刪除來源帳號失敗。
        // 需求（追加，2026-09-30）：會員卡等級／開卡日／到期日也要一起帶過去，
        // 不然匯入時帶入的等級在顧客認領後就消失了。兩邊都有卡時保留「比較好的那張」。
        walletRepository.findByUserId(imported.getId()).ifPresent(importedWallet -> {
            boolean hasBalance = importedWallet.getBalance() != null && importedWallet.getBalance() > 0;
            boolean hasCard = importedWallet.getCardTier() != null
                    && importedWallet.getCardTier() != MemberCardTier.NONE;
            if (hasBalance || hasCard) {
                Wallet targetWallet = walletRepository.findByUserId(target.getId())
                        .orElseGet(() -> walletRepository.save(Wallet.builder().user(target).balance(0).build()));
                if (hasBalance) {
                    int targetBalance = targetWallet.getBalance() != null ? targetWallet.getBalance() : 0;
                    targetWallet.setBalance(targetBalance + importedWallet.getBalance());
                }
                if (hasCard && isBetterCard(importedWallet, targetWallet)) {
                    targetWallet.setCardTier(importedWallet.getCardTier());
                    targetWallet.setCardActivatedAt(importedWallet.getCardActivatedAt());
                    targetWallet.setCardExpiresAt(importedWallet.getCardExpiresAt());
                }
                walletRepository.save(targetWallet);
            }
            walletRepository.delete(importedWallet);
        });

        userRepository.delete(imported);

        return new TransferSummary(pets, orders, appointments, topUps);
    }

    // 兩張會員卡比較：有效的優先 → 等級高的優先 → 到期日晚的優先
    private boolean isBetterCard(Wallet candidate, Wallet current) {
        if (current.getCardTier() == null || current.getCardTier() == MemberCardTier.NONE) return true;
        boolean candActive = candidate.isCardActive();
        boolean currActive = current.isCardActive();
        if (candActive != currActive) return candActive;
        int byTier = Integer.compare(candidate.getCardTier().ordinal(), current.getCardTier().ordinal());
        if (byTier != 0) return byTier > 0;
        if (candidate.getCardExpiresAt() == null) return false;
        return current.getCardExpiresAt() == null || candidate.getCardExpiresAt().isAfter(current.getCardExpiresAt());
    }

    private record TransferSummary(
            List<Pet> pets, List<WalkInOrder> orders, List<Appointment> appointments, List<TopUpRequest> topUps) {
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
            double a = Double.parseDouble(row.getAgeRaw().trim()); // 需求（追加）：允許小數年齡
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
    // 需求（修正，2026-09-11）：Excel 儲存格如果設成「一般」或「數字」格式，
    // 使用者輸入 0917684717 這種台灣手機號碼，Excel 會把它當數字存，開頭的 0
    // 是無意義的前導零直接被吃掉，存成 CSV 時原始內容就已經是 917684717，不是
    // 我們這邊解析錯誤。這裡加一道自動修正：只要格式剛好符合「拿掉開頭 0 後的
    // 台灣手機號碼長相」（9 碼、以 9 開頭），就自動補回開頭的 0，減少店家因為
    // Excel 存檔習慣而匯入失敗或資料錯誤的機率。只處理這一種明確的情況，避免
    // 誤判其他本來就不到 10 碼的市話號碼或其他格式。
    // 需求（追加，2026-09-30）：改成 public static，UserService 填資料防重複也要用同一套正規化
    public static String normalizePhone(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim().replaceAll("[\\s\\-()]", "");
        if (cleaned.matches("9\\d{8}")) {
            cleaned = "0" + cleaned;
        }
        return cleaned;
    }

    // ── CSV 解析 ──────────────────────────────────────────────────────────
    // 極簡手刻解析，不依賴額外套件（專案目前沒有 CSV 函式庫的依賴）。
    // 欄位順序固定：家長姓名,電話,毛孩名字,物種,品種,體重,年齡,是否分離焦慮,注意事項
    // 不支援欄位值裡包含逗號（例如注意事項寫「怕生,咬人」會被誤判成兩欄）——
    // 這是簡化實作的已知限制，交付時要在後台頁面上明確提醒店家避免在內容裡打逗號。
    //
    // 編碼：交給 CsvImportFileReader 偵測（UTF-8 BOM / 無 BOM 時嚴格試 UTF-8→Big5→GBK），
    // 不再假設檔案一定是 UTF-8；BOM 也在那邊一併去掉，避免黏在第一個欄位值前面。
    private List<MemberImportRow> parseCsv(MultipartFile file) throws IOException {
        List<MemberImportRow> rows = new ArrayList<>();
        CsvImportFileReader.Parsed parsed = CsvImportFileReader.read(file);
        logDetectedCharset("會員資料", parsed);

        List<String> lines = parsed.lines();
        int rowNumber = 0;
        for (int i = 1; i < lines.size(); i++) { // i=0 是表頭，跳過不處理
            String line = lines.get(i);
            if (line.isBlank()) continue;
            rowNumber++;
            String[] cols = line.split(",", -1);
            MemberImportRow row = new MemberImportRow();
            row.setRowNumber(rowNumber);
            row.setOwnerName(col(cols, 0));
            row.setPhone(col(cols, 1));
            row.setPetName(col(cols, 2));
            row.setPetTypeRaw(col(cols, 3));
            row.setBreed(col(cols, 4));
            row.setWeightRaw(col(cols, 5));
            row.setAgeRaw(col(cols, 6));
            row.setSeparationAnxietyRaw(col(cols, 7));
            row.setNotes(col(cols, 8));
            rows.add(row);
        }
        return rows;
    }

    // 讀檔時把偵測到的編碼寫進 log，方便店家上傳出問題時對照排查。
    private void logDetectedCharset(String label, CsvImportFileReader.Parsed parsed) {
        if (parsed.isLenientFallback()) {
            log.warn("⚠️ [{}匯入] 無法確定 CSV 檔案編碼，已改用 UTF-8 寬鬆模式讀取，內容可能有亂碼，"
                    + "請確認原始檔另存為 UTF-8 或 Big5", label);
        } else {
            log.info("📄 [{}匯入] 偵測到 CSV 檔案編碼：{}", label, parsed.getCharset().displayName());
        }
    }

    private String col(String[] cols, int idx) {
        return idx < cols.length ? cols[idx].trim() : "";
    }

    // 需求（追加）：到期日寬鬆解析——Excel 另存 CSV 常變成 2026/9/30，
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

    // 需求（追加）：儲值餘額 CSV 解析。欄位順序：電話,儲值餘額,會員等級,到期日（後兩欄選填）
    // 編碼同 parseCsv，交給 CsvImportFileReader 偵測，不假設一定是 UTF-8。
    private List<WalletImportRow> parseWalletCsv(MultipartFile file) throws IOException {
        List<WalletImportRow> rows = new ArrayList<>();
        CsvImportFileReader.Parsed parsed = CsvImportFileReader.read(file);
        logDetectedCharset("儲值餘額", parsed);

        List<String> lines = parsed.lines();
        int rowNumber = 0;
        for (int i = 1; i < lines.size(); i++) { // i=0 是表頭，跳過
            String line = lines.get(i);
            if (line.isBlank()) continue;
            rowNumber++;
            String[] cols = line.split(",", -1);
            WalletImportRow row = new WalletImportRow();
            row.setRowNumber(rowNumber);
            row.setPhone(col(cols, 0));
            row.setBalanceRaw(col(cols, 1));
            row.setTierRaw(col(cols, 2));
            row.setExpiresRaw(col(cols, 3));
            rows.add(row);
        }
        return rows;
    }

    // 需求（追加）：消費紀錄 CSV 解析。欄位順序：電話,毛孩名字,消費日期,金額,備註
    // 編碼同 parseCsv，交給 CsvImportFileReader 偵測，不假設一定是 UTF-8。
    private List<ConsumptionImportRow> parseConsumptionCsv(MultipartFile file) throws IOException {
        List<ConsumptionImportRow> rows = new ArrayList<>();
        CsvImportFileReader.Parsed parsed = CsvImportFileReader.read(file);
        logDetectedCharset("消費紀錄", parsed);

        List<String> lines = parsed.lines();
        int rowNumber = 0;
        for (int i = 1; i < lines.size(); i++) { // i=0 是表頭，跳過
            String line = lines.get(i);
            if (line.isBlank()) continue;
            rowNumber++;
            String[] cols = line.split(",", -1);
            ConsumptionImportRow row = new ConsumptionImportRow();
            row.setRowNumber(rowNumber);
            row.setPhone(col(cols, 0));
            row.setPetName(col(cols, 1));
            row.setDateRaw(col(cols, 2));
            row.setAmountRaw(col(cols, 3));
            row.setNote(col(cols, 4));
            rows.add(row);
        }
        return rows;
    }
}
