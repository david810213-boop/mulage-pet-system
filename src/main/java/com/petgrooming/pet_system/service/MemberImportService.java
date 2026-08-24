package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.MemberImportResult;
import com.petgrooming.pet_system.dto.MemberImportRow;
import com.petgrooming.pet_system.enums.PetSizeCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
                rowErrors.add("第 " + row.getRowNumber() + " 列：" + error);
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
                int age = Integer.parseInt(row.getAgeRaw().trim());
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

    // ── 顧客自助認領既有匯入資料 ─────────────────────────────────────────
    // 用途：顧客第一次用 LINE 登入時系統會自動建一筆空白新帳號（既有機制，不動它），
    // 這裡是額外的動作——把電話號碼比對到的「匯入但還沒被認領」的舊資料，整批
    // 過戶到目前這筆已經綁好 LINE 的帳號上，然後把那筆匯入用的暫時帳號刪掉，
    // 避免產生一個人對應兩筆帳號的重複資料。
    //
    // 安全限制：只有目前這個 LINE 帳號「還沒填過資料」（profileCompletedAt 為 null）
    // 才能認領，避免已經自己填好資料的人不小心把別人的資料領走蓋掉自己的。
    @Transactional
    public User claimByPhone(User currentUser, String phone) {
        if (currentUser.getProfileCompletedAt() != null) {
            throw new IllegalArgumentException("這個帳號已經填過資料了，無法再認領其他會員資料");
        }
        String normalized = normalizePhone(phone);
        User imported = userRepository.findByPhoneAndLineUserIdIsNull(normalized)
                .orElseThrow(() -> new IllegalArgumentException("查無符合這支電話的既有會員資料"));
        if (imported.getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("查無符合這支電話的既有會員資料");
        }

        // 過戶基本資料
        currentUser.setName(imported.getName());
        currentUser.setPhone(imported.getPhone());
        currentUser.setProfileCompletedAt(java.time.LocalDateTime.now());

        // 過戶寵物（改 owner，不是複製一份新的）
        List<Pet> pets = petRepository.findByOwnerId(imported.getId());
        for (Pet pet : pets) {
            pet.setOwner(currentUser);
        }
        petRepository.saveAll(pets);

        userRepository.save(currentUser);
        userRepository.delete(imported); // 匯入用的暫時帳號完成階段性任務，直接刪除避免留下重複資料

        log.info("✨ [會員認領] {} 認領了電話 {} 的匯入資料，過戶 {} 隻寵物",
                currentUser.getUsername(), normalized, pets.size());

        return currentUser;
    }

    private String validateRow(MemberImportRow row) {
        if (isBlank(row.getOwnerName())) return "家長姓名不能空白";
        if (isBlank(row.getPhone())) return "電話不能空白";
        if (isBlank(row.getPetName())) return "毛孩名字不能空白";
        if (isBlank(row.getPetTypeRaw())) return "物種不能空白";
        if (isBlank(row.getBreed())) return "品種不能空白";
        try {
            double w = Double.parseDouble(row.getWeightRaw().trim());
            if (w <= 0) return "體重必須大於 0";
        } catch (Exception e) {
            return "體重格式錯誤：" + row.getWeightRaw();
        }
        try {
            int a = Integer.parseInt(row.getAgeRaw().trim());
            if (a < 0) return "年齡不能是負數";
        } catch (Exception e) {
            return "年齡格式錯誤：" + row.getAgeRaw();
        }
        return null;
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
    private String normalizePhone(String raw) {
        return raw.trim().replaceAll("[\\s\\-()]", "");
    }

    // ── CSV 解析 ──────────────────────────────────────────────────────────
    // 極簡手刻解析，不依賴額外套件（專案目前沒有 CSV 函式庫的依賴）。
    // 欄位順序固定：家長姓名,電話,毛孩名字,物種,品種,體重,年齡,是否分離焦慮,注意事項
    // 不支援欄位值裡包含逗號（例如注意事項寫「怕生,咬人」會被誤判成兩欄）——
    // 這是簡化實作的已知限制，交付時要在後台頁面上明確提醒店家避免在內容裡打逗號。
    private List<MemberImportRow> parseCsv(MultipartFile file) throws IOException {
        List<MemberImportRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // 表頭，跳過不處理
            int rowNumber = 0;
            while ((line = reader.readLine()) != null) {
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
        }
        return rows;
    }

    private String col(String[] cols, int idx) {
        return idx < cols.length ? cols[idx].trim() : "";
    }
}
