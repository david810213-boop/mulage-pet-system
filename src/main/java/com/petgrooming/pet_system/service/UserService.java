package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CreateStaffRequest;
import com.petgrooming.pet_system.dto.RegisterRequest;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // ── 1. 認證登入（回傳 Optional<User>，讓 Controller 自行決定處理方式）──
    public Optional<User> authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(u -> u.getPassword().equals(password))
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()));
    }

    // ── 2. 查 User entity（AuthMvcController 登入後建立 Session 用）
    public User getUserEntityByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + username));
    }

    // ── 3. 註冊（CUSTOMER）────────────────────────────────────────────────
    public UserResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("帳號已存在：" + req.getUsername());
        }
        User user = User.builder()
                .username(req.getUsername())
                .password(req.getPassword())
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

    public List<User> getAllStaffEntities() {
        return userRepository.findByRole(UserRole.STAFF);
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
                .password(UUID.randomUUID().toString())
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
                .password(req.getPassword())
                .name(req.getName())
                .role(UserRole.STAFF)
                .isActive(true)
                .build();
        return UserResponse.from(userRepository.save(staff));
    }
}
