package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.ChangePasswordRequest;
import com.petgrooming.pet_system.dto.CreateStaffRequest;
import com.petgrooming.pet_system.dto.RegisterRequest;
import com.petgrooming.pet_system.dto.ResetPasswordRequest;
import com.petgrooming.pet_system.dto.UpdateProfileRequest;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.exception.AuthException;
import com.petgrooming.pet_system.exception.MemberException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.petgrooming.pet_system.repository.PetRepository petRepository; // 需求（2026-10-02）：會員搜尋支援寵物名

    // ── 需求（追加，2026-09-17）：登入暴力破解防護 ─────────────────────────
    // 連續密碼答錯達到這個次數，帳號鎖定一段時間，鎖定期間內即使密碼正確
    // 也一律拒絕，避免帳密被無限次嘗試。用 @Value 讓店家/工程之後可以透過
    // Railway 環境變數調整門檻，不用改程式碼重新部署。只影響帳密登入
    // （AuthMvcController），LINE 顧客端用 idToken 驗證，不是猜密碼，
    // 不受影響。
    @Value("${LOGIN_MAX_FAILED_ATTEMPTS:5}")
    private int maxFailedAttempts;

    @Value("${LOGIN_LOCKOUT_MINUTES:15}")
    private int lockoutMinutes;

    // ── 1. 認證登入（回傳 Optional<User>，讓 Controller 自行決定處理方式）──
    // 帳號在鎖定期間時，直接丟 AuthException（帶友善訊息跟剩餘分鐘數），
    // 讓 Controller 可以跟「帳號或密碼錯誤」分開顯示不同文字，不會混淆使用者。
    public Optional<User> authenticate(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()));
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        LocalDateTime now = LocalDateTime.now();

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                long minutesLeft = ChronoUnit.MINUTES.between(now, user.getLockedUntil()) + 1;
                throw new AuthException(
                        "登入失敗次數過多，帳號已暫時鎖定，請約 " + minutesLeft + " 分鐘後再試",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            // 鎖定時間已過，清空鎖定狀態，允許重新開始嘗試
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        if (passwordEncoder.matches(password, user.getPassword())) {
            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            }
            return Optional.of(user);
        }

        // 密碼錯誤：累計失敗次數，達到門檻就鎖定帳號
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= maxFailedAttempts) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(now.plusMinutes(lockoutMinutes));
        } else {
            user.setFailedLoginAttempts(attempts);
        }
        userRepository.save(user);
        return Optional.empty();
    }


    // ── 2. 查 User entity（AuthMvcController 登入後建立 Session 用）
    public User getUserEntityByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new MemberException("找不到使用者：" + username));
    }

    // ── 需求 8：店家後台備注會員特殊資訊（後台專用）─────────────────────────
    public String getAdminNote(String username) {
        return getUserEntityByUsername(username).getAdminNote();
    }

    @org.springframework.transaction.annotation.Transactional
    public String setAdminNote(String username, String adminNote) {
        User user = getUserEntityByUsername(username);
        user.setAdminNote(adminNote);
        userRepository.save(user);
        return user.getAdminNote();
    }

    // ── 3. 註冊（CUSTOMER）────────────────────────────────────────────────
    public UserResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new MemberException("帳號已存在：" + req.getUsername());
        }
        User user = User.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    // ── 4. 查自己的資料（DTO）─────────────────────────────────────────────
    public UserResponse getMe(String username) {
        return userRepository.findByUsername(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new MemberException("找不到使用者：" + username));
    }

    // ── 4.5 會員編輯自己的基本資料（年齡／職業／居住區域／來源）──────────
    public UserResponse updateProfile(String username, UpdateProfileRequest req) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new MemberException("找不到使用者：" + username));

        if (req.getName() != null && !req.getName().isBlank()) {
            user.setName(req.getName().trim());
        }
        // 需求（追加，2026-09-30）：電話改成店家匯入、還沒被認領的既有會員電話時擋下來，
        // 避免老客人跳過認領自己重填，產生一人兩筆帳號。只在「電話有改」時檢查，
        // 不然原本就撞號的舊帳號會連其他欄位都改不了。回 409 讓 LIFF 導回新客報到認領。
        String newPhone = com.petgrooming.pet_system.service.MemberImportService.normalizePhone(req.getPhone());
        String oldPhone = com.petgrooming.pet_system.service.MemberImportService.normalizePhone(user.getPhone());
        if (!newPhone.equals(oldPhone)) {
            userRepository.findFirstByPhoneAndLineUserIdIsNullAndRoleOrderByIdAsc(newPhone, UserRole.CUSTOMER)
                    .filter(u -> !u.getId().equals(user.getId()))
                    .ifPresent(u -> {
                        throw new MemberException("這支電話是本店的既有會員資料，請回到「新客報到」輸入電話直接帶入，"
                                + "不需要重新填寫（店家代填請改用會員資料頁的手動綁定）", HttpStatus.CONFLICT);
                    });
        }
        user.setPhone(req.getPhone().trim()); // 需求（追加）：電話改為必填，不再是「填了才更新」
        if (req.getAge() != null) {
            user.setAge(req.getAge());
        }
        if (req.getOccupation() != null) {
            user.setOccupation(req.getOccupation().isBlank() ? null : req.getOccupation().trim());
        }
        if (req.getResidenceArea() != null) {
            user.setResidenceArea(req.getResidenceArea().isBlank() ? null : req.getResidenceArea().trim());
        }
        if (req.getSource() != null) {
            user.setSource(req.getSource());
        }
        // 需求 19：定型化契約要求蒐集的資料（皆選填，只需填一次）
        if (req.getMailingAddress() != null) {
            user.setMailingAddress(req.getMailingAddress().isBlank() ? null : req.getMailingAddress().trim());
        }
        if (req.getEmergencyContactName() != null) {
            user.setEmergencyContactName(req.getEmergencyContactName().isBlank() ? null : req.getEmergencyContactName().trim());
        }
        if (req.getEmergencyContactPhone() != null) {
            user.setEmergencyContactPhone(req.getEmergencyContactPhone().isBlank() ? null : req.getEmergencyContactPhone().trim());
        }
        if (req.getEmergencyContactRelation() != null) {
            user.setEmergencyContactRelation(req.getEmergencyContactRelation().isBlank() ? null : req.getEmergencyContactRelation().trim());
        }
        // 第一次完整填寫基本資料時記錄時間點，供後台判斷「已完成資料填寫」的會員數
        if (user.getProfileCompletedAt() == null
                && user.getAge() != null
                && user.getOccupation() != null
                && user.getResidenceArea() != null
                && user.getSource() != null) {
            user.setProfileCompletedAt(LocalDateTime.now());
        }

        return UserResponse.from(userRepository.save(user));
    }

    // ── 5. 查所有使用者（ADMIN）──────────────────────────────────────────
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    // ── 6. 查所有員工（ADMIN）────────────────────────────────────────────
    public List<UserResponse> getAllStaff() {
        return userRepository.findByRole(UserRole.STAFF).stream().map(UserResponse::from).toList();
    }

    public List<User> getAllCustomers() {
        return userRepository.findByRole(UserRole.CUSTOMER);
    }

    // 需求：現場開單時依姓名/帳號搜尋會員（解決 LINE 登入會員帳號是 line_xxx 內碼，
    // 店家不可能知道要打什麼的問題）
    public List<User> searchCustomers(String keyword) {
        if (keyword == null || keyword.isBlank())
            return List.of();
        String kw = keyword.trim().toLowerCase();
        // 需求（追加，2026-10-02）：電話也能搜（輸入 3 碼以上數字，忽略 - 與空白）
        String digits = kw.replaceAll("[^0-9]", "");
        return getAllCustomers().stream()
                .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(kw))
                        || u.getUsername().toLowerCase().contains(kw)
                        || (digits.length() >= 3 && u.getPhone() != null
                                && u.getPhone().replaceAll("[^0-9]", "").contains(digits)))
                .limit(20)
                .toList();
    }

    // ── 需求（追加，2026-10-02）：會員搜尋支援寵物名 ──────────────────────
    // 回傳「會員帳號 → 符合關鍵字的毛孩名字（多隻用、隔開）」，已停用的毛孩不算。
    // 各個會員列表／搜尋共用這一支，搜尋結果可以標出「毛孩：盆莓」方便分辨同名會員。
    public java.util.Map<String, String> petNamesMatching(String keyword) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        if (keyword == null || keyword.isBlank()) return result;
        for (var pet : petRepository.findByNameContainingIgnoreCase(keyword.trim())) {
            if (pet.isDeleted() || pet.getOwner() == null || pet.getOwner().getRole() != UserRole.CUSTOMER) continue;
            result.merge(pet.getOwner().getUsername(), pet.getName(), (a, b) -> a + "、" + b);
        }
        return result;
    }

    // 姓名／帳號／電話＋寵物名一起搜，回傳會員與（如果是靠寵物名搜到的）毛孩名字
    public List<java.util.Map.Entry<User, String>> searchCustomersIncludingPets(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        java.util.Map<String, String> pets = petNamesMatching(keyword);
        java.util.Map<String, java.util.Map.Entry<User, String>> found = new java.util.LinkedHashMap<>();
        for (User u : searchCustomers(keyword)) {
            found.put(u.getUsername(), new java.util.AbstractMap.SimpleEntry<>(u, pets.get(u.getUsername())));
        }
        for (var e : pets.entrySet()) {
            if (found.size() >= 20) break;
            if (!found.containsKey(e.getKey())) {
                userRepository.findByUsername(e.getKey()).ifPresent(u ->
                        found.put(u.getUsername(), new java.util.AbstractMap.SimpleEntry<>(u, e.getValue())));
            }
        }
        return List.copyOf(found.values());
    }

    // 需求（追加，2026-10-02）：經手人下拉選單（員工＋管理者），只給 id 跟姓名，不把整個 User 丟進頁面
    public List<java.util.Map<String, Object>> operatorOptions() {
        List<User> list = new java.util.ArrayList<>(getAllStaffEntities());
        list.addAll(getAllAdminEntities());
        return list.stream()
                .<java.util.Map<String, Object>>map(u -> java.util.Map.of("id", u.getId(), "name", u.getName()))
                .toList();
    }

    public List<User> getAllStaffEntities() {
        return userRepository.findByRole(UserRole.STAFF);
    }

    public List<User> getAllAdminEntities() {
        return userRepository.findByRole(UserRole.ADMIN);
    }

    public User getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new MemberException("找不到使用者：" + id));
    }

    // ── 8. LINE 登入：依 lineUserId 查找會員，找不到就自動建立（CUSTOMER）──
    // 回傳 [User, isNewMember]
    public AbstractMap.SimpleEntry<User, Boolean> findOrCreateByLine(String lineUserId, String displayName) {
        Optional<User> existing = userRepository.findByLineUserId(lineUserId);
        if (existing.isPresent()) {
            return new AbstractMap.SimpleEntry<>(existing.get(), false);
        }

        // LINE 會員沒有帳密登入需求，username/password 用內部規則產生佔位值
        User newUser = User.builder()
                .username("line_" + lineUserId)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(displayName != null ? displayName : "LINE會員")
                .role(UserRole.CUSTOMER)
                .lineUserId(lineUserId)
                .isActive(true)
                .build();

        return new AbstractMap.SimpleEntry<>(userRepository.save(newUser), true);
    }

    public UserResponse createStaff(CreateStaffRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new MemberException("帳號已存在：" + req.getUsername());
        }
        User staff = User.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .role(UserRole.STAFF)
                .isActive(true)
                .build();
        return UserResponse.from(userRepository.save(staff));
    }

    // ── 9. ADMIN 重設員工／管理員密碼（不需驗證舊密碼，僅 ADMIN 可操作）──────
    public void resetPassword(Long userId, ResetPasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MemberException("找不到使用者：" + userId));
        if (user.isCustomer()) {
            // 顧客帳號走 LINE 登入，沒有密碼登入需求，不開放重設
            throw new MemberException("顧客帳號不支援密碼重設");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    // ── 10. 員工／管理員自行修改密碼（需驗證舊密碼）─────────────────────
    public void changePassword(String username, ChangePasswordRequest req) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new MemberException("找不到使用者：" + username));

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new MemberException("目前密碼不正確");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    // ── 新需求：切換使用者用的 PIN 碼 ─────────────────────────────────
    // 員工/管理員自己設定 4 位數 PIN（需先驗證密碼，避免旁人隨便亂設）
    public void setSwitchPin(String username, String currentPassword, String newPin) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new MemberException("找不到使用者：" + username));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new MemberException("目前密碼不正確");
        }
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new MemberException("PIN 碼必須是 4 位數字");
        }
        user.setSwitchPin(passwordEncoder.encode(newPin));
        userRepository.save(user);
    }

    // 驗證 PIN 是否正確，用於快速切換使用者（不需要輸入完整密碼）
    public boolean verifySwitchPin(User user, String pin) {
        if (user.getSwitchPin() == null) return false;
        return passwordEncoder.matches(pin, user.getSwitchPin());
    }
}
