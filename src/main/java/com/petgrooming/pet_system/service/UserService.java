package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.ChangePasswordRequest;
import com.petgrooming.pet_system.dto.CreateStaffRequest;
import com.petgrooming.pet_system.dto.RegisterRequest;
import com.petgrooming.pet_system.dto.ResetPasswordRequest;
import com.petgrooming.pet_system.dto.UpdateProfileRequest;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── 1. 認證登入（回傳 Optional<User>，讓 Controller 自行決定處理方式）──
    public Optional<User> authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()));
    }

    // ── 2. 查 User entity（AuthMvcController 登入後建立 Session 用）
    public User getUserEntityByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + username));
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
            throw new IllegalArgumentException("帳號已存在：" + req.getUsername());
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
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + username));
    }

    // ── 4.5 會員編輯自己的基本資料（年齡／職業／居住區域／來源）──────────
    public UserResponse updateProfile(String username, UpdateProfileRequest req) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + username));

        if (req.getName() != null && !req.getName().isBlank()) {
            user.setName(req.getName().trim());
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
        return getAllCustomers().stream()
                .filter(u -> u.getName().toLowerCase().contains(kw)
                        || u.getUsername().toLowerCase().contains(kw))
                .limit(20)
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
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + id));
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
            throw new IllegalArgumentException("帳號已存在：" + req.getUsername());
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
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + userId));
        if (user.isCustomer()) {
            // 顧客帳號走 LINE 登入，沒有密碼登入需求，不開放重設
            throw new IllegalArgumentException("顧客帳號不支援密碼重設");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    // ── 10. 員工／管理員自行修改密碼（需驗證舊密碼）─────────────────────
    public void changePassword(String username, ChangePasswordRequest req) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + username));

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("目前密碼不正確");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    // ── 新需求：切換使用者用的 PIN 碼 ─────────────────────────────────
    // 員工/管理員自己設定 4 位數 PIN（需先驗證密碼，避免旁人隨便亂設）
    public void setSwitchPin(String username, String currentPassword, String newPin) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + username));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("目前密碼不正確");
        }
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new IllegalArgumentException("PIN 碼必須是 4 位數字");
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
