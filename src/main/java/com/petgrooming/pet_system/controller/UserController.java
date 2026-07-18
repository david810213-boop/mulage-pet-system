package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.CreateStaffRequest;
import com.petgrooming.pet_system.dto.UpdateProfileRequest;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// UserController 只保留「需要登入後」才能使用的端點
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── GET /api/users/me ──────────────────────────────────────────────────
    // 查詢自己的資料，身分取自 JWT（店家帳密登入或顧客 LINE 登入皆適用）
    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("tokenUsername");
            UserResponse res = userService.getMe(username);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── PUT /api/users/me ───────────────────────────────────────────────────
    // 會員編輯自己的基本資料（年齡／職業／居住區域／得知來源），身分取自 JWT
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@Valid @RequestBody UpdateProfileRequest req, HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("tokenUsername");
            UserResponse res = userService.updateProfile(username, req);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── GET /api/users ─────────────────────────────────────────────────────
    // 查詢所有使用者（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ── GET /api/users/staff ───────────────────────────────────────────────
    // 查詢所有員工清單（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/staff")
    public ResponseEntity<List<UserResponse>> getAllStaff() {
        return ResponseEntity.ok(userService.getAllStaff());
    }

    // ── POST /api/users/staff ──────────────────────────────────────────────
    // 新增員工帳號（ADMIN 限定）
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/staff")
    public ResponseEntity<?> createStaff(@Valid @RequestBody CreateStaffRequest req) {
        try {
            UserResponse res = userService.createStaff(req);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
