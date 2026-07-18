package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.MemberNoteRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 需求 8：店家後台備注會員特殊資訊
 *
 * GET /api/admin/members/{username}/note   取得會員備注
 * PUT /api/admin/members/{username}/note   設定會員備注
 *
 * 僅 STAFF / ADMIN 可存取；此備注永不出現在顧客端 API。
 */
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final UserService userService;
    private final PetService petService;

    // ── GET /api/admin/members/search?keyword=xxx ───────────────────────────
    // 現場開單：依姓名/帳號搜尋會員，解決 LINE 登入會員帳號是 line_xxx 內碼問題
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> search(@RequestParam String keyword) {
        List<UserResponse> results = userService.searchCustomers(keyword).stream()
                .map(UserResponse::from).toList();
        return ResponseEntity.ok(results);
    }

    // ── GET /api/admin/members/{username}/pets ──────────────────────────────
    // 現場開單：選定會員後自動帶出名下寵物清單
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{username}/pets")
    public ResponseEntity<List<PetResponse>> pets(@PathVariable String username) {
        return ResponseEntity.ok(petService.getMyPets(username));
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{username}/note")
    public ResponseEntity<?> getNote(@PathVariable String username) {
        try {
            return ResponseEntity.ok(Map.of(
                    "username", username,
                    "adminNote", userService.getAdminNote(username) == null
                            ? "" : userService.getAdminNote(username)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{username}/note")
    public ResponseEntity<?> setNote(@PathVariable String username,
                                     @RequestBody MemberNoteRequest req) {
        try {
            String saved = userService.setAdminNote(username, req.getAdminNote());
            return ResponseEntity.ok(Map.of(
                    "username", username,
                    "adminNote", saved == null ? "" : saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
