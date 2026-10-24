package com.petgrooming.pet_system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.CreateStaffRequest;
import com.petgrooming.pet_system.dto.UpdateProfileRequest;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// UserController 只保留「需要登入後」才能使用的端點
@Tag(name = "使用者", description = "個人資料、會員清單、員工帳號")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OperationLogService operationLogService;

    // ── GET /api/users/me ──────────────────────────────────────────────────
    // 查詢自己的資料，身分取自 JWT（店家帳密登入或顧客 LINE 登入皆適用）
    @GetMapping("/api/users/me")
    public ResponseEntity<?> getMe(@CurrentUser String username) {
        UserResponse res = userService.getMe(username);
        return ResponseEntity.ok(res);
    }

    // ── PUT /api/users/me ───────────────────────────────────────────────────
    // 會員編輯自己的基本資料（年齡／職業／居住區域／得知來源），身分取自 JWT
    @PutMapping("/api/users/me")
    public ResponseEntity<?> updateMe(@Valid @RequestBody UpdateProfileRequest req, @CurrentUser String username) {
        UserResponse res = userService.updateProfile(username, req);
        operationLogService.logByUsername(username, "CUSTOMER", "UPDATE_PROFILE", "會員 " + username, null);
        return ResponseEntity.ok(res);
    }

    // ── GET /api/admin/users ─────────────────────────────────────────────────────
    // 查詢所有使用者（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @GetMapping({"/api/admin/users", "/api/users"})
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ── GET /api/admin/staff ───────────────────────────────────────────────
    // 查詢所有員工清單（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @GetMapping({"/api/admin/staff", "/api/users/staff"})
    public ResponseEntity<List<UserResponse>> getAllStaff() {
        return ResponseEntity.ok(userService.getAllStaff());
    }

    // ── POST /api/admin/staff ──────────────────────────────────────────────
    // 新增員工帳號（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @PostMapping({"/api/admin/staff", "/api/users/staff"})
    public ResponseEntity<?> createStaff(@Valid @RequestBody CreateStaffRequest req, @CurrentUser String username) {
        UserResponse res = userService.createStaff(req);
        operationLogService.logByUsername(username,
                "AUTH", "CREATE_STAFF", "新員工 " + res.getUsername(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

}
