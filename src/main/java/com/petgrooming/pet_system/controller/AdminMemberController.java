package com.petgrooming.pet_system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.MemberMergeRequest;
import com.petgrooming.pet_system.dto.MemberNoteRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.PetSearchResponse;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.MemberClaimService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 店家後台備注會員特殊資訊
 *
 * GET /api/admin/members/{username}/note   取得會員備注
 * PUT /api/admin/members/{username}/note   設定會員備注
 *
 * 僅 STAFF / ADMIN 可存取；此備注永不出現在顧客端 API。
 */
@Tag(name = "管理端：會員", description = "會員搜尋、備註、名下寵物、匯入資料合併")
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final UserService userService;
    private final PetService petService;
    private final MemberClaimService memberClaimService;

    // ── GET /api/admin/members/search?keyword=xxx ───────────────────────────
    // 現場開單：依姓名/帳號搜尋會員，解決 LINE 登入會員帳號是 line_xxx 內碼問題
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/search")
    // withPets=true 時連寵物名一起搜，結果帶 matchedPetNames。
    // 現場開單、代客預約另外有 search-by-pet 分區顯示，所以預設不開，避免同一位會員出現兩次。
    public ResponseEntity<List<UserResponse>> search(@RequestParam String keyword,
            @RequestParam(defaultValue = "false") boolean withPets) {
        if (!withPets) {
            return ResponseEntity.ok(userService.searchCustomers(keyword).stream()
                    .map(UserResponse::from).toList());
        }
        List<UserResponse> results = userService.searchCustomersIncludingPets(keyword).stream()
                .map(e -> {
                    UserResponse r = UserResponse.from(e.getKey());
                    r.setMatchedPetNames(e.getValue());
                    return r;
                }).toList();
        return ResponseEntity.ok(results);
    }

    // ── GET /api/admin/members/search-by-pet?keyword=xxx ────────────────────
    // 依寵物名稱搜尋，自動對應所屬家長姓名/電話/寵物完整資料。
    // 若有同名寵物，displayLabel 會附上家長姓名＋電話末四碼方便店員辨識選對象。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/search-by-pet")
    public ResponseEntity<List<PetSearchResponse>> searchByPet(
            @RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<PetSearchResponse> results = petService.searchForStaff(keyword);
        return ResponseEntity.ok(results);
    }

    // ── GET /api/admin/members/{userId}/pets ────────────────────────────────
    // 現場開單：選定會員後自動帶出名下寵物清單
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{userId:\\d+}/pets")
    public ResponseEntity<List<PetResponse>> petsById(@PathVariable Long userId) {
        return pets(usernameOf(userId));
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{userId:\\d+}/note")
    public ResponseEntity<?> getNoteById(@PathVariable Long userId) {
        return getNote(usernameOf(userId));
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{userId:\\d+}/note")
    public ResponseEntity<?> setNoteById(@PathVariable Long userId, @RequestBody MemberNoteRequest req) {
        return setNote(usernameOf(userId), req);
    }

    private String usernameOf(Long userId) {
        return userService.getUserEntityById(userId).getUsername();
    }

    // 以下三個是舊路徑（網址帶帳號），改用上方以 userId 識別的路徑。
    // 帳號限定含非數字字元，純數字一律視為 userId，兩組路徑不會同時符合。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{username:.*\\D.*}/pets")
    public ResponseEntity<List<PetResponse>> pets(@PathVariable String username) {
        return ResponseEntity.ok(petService.getMyPets(username));
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{username:.*\\D.*}/note")
    public ResponseEntity<?> getNote(@PathVariable String username) {
        return ResponseEntity.ok(Map.of(
                "username", username,
                "adminNote", userService.getAdminNote(username) == null
                        ? "" : userService.getAdminNote(username)));
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{username:.*\\D.*}/note")
    public ResponseEntity<?> setNote(@PathVariable String username,
                                     @RequestBody MemberNoteRequest req) {
        String saved = userService.setAdminNote(username, req.getAdminNote());
        return ResponseEntity.ok(Map.of(
                "username", username,
                "adminNote", saved == null ? "" : saved));
    }

    // ── POST /api/admin/members/merge ───────────────────────────────────────
    // 手動綁定既有匯入資料。用在顧客的 LINE 帳號
    // 自己已經先填過資料，導致 claimByPhone() 的防呆擋下自動認領時，由店家
    // 人工判斷後手動合併。importedUsername 必須是 imported_ 開頭的匯入暫時
    // 帳號，targetUsername 是要合併過去的真正會員帳號（通常是 line_ 開頭）。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/merge")
    public ResponseEntity<?> merge(@Valid @RequestBody MemberMergeRequest req) {
        String importedUsername = req.importedUsername();
        String targetUsername = req.targetUsername();
        memberClaimService.manualMerge(importedUsername, targetUsername);
        return ResponseEntity.ok(Map.of(
                "message", "已將 " + importedUsername + " 的資料合併到 " + targetUsername));
    }
}
