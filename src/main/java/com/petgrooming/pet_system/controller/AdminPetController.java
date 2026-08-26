package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.SetCoatTypeRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.PetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 需求 2：店家後台管理寵物毛長
 *
 * PUT /api/admin/pets/{petId}/coat-type   由店家定義毛長並寫入資料庫
 */
@RestController
@RequestMapping("/api/admin/pets")
@RequiredArgsConstructor
public class AdminPetController {

    private final PetService petService;

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{petId}/coat-type")
    public ResponseEntity<?> setCoatType(@PathVariable Long petId,
                                         @Valid @RequestBody SetCoatTypeRequest req) {
        try {
            PetResponse res = petService.setCoatType(petId, req.getCoatType());
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── 需求（追加，2026-08-24）：狗狗定價流程簡化 ─────────────────────────

    /** 成犬結帳核對時，店員選出真正對應的套餐項目後鎖定，之後不用再重複選單。 */
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{petId}/lock-grooming-item")
    public ResponseEntity<?> lockGroomingItem(@PathVariable Long petId,
                                               @RequestBody java.util.Map<String, Long> body) {
        try {
            Long groomingItemId = body.get("groomingItemId");
            if (groomingItemId == null) {
                return ResponseEntity.badRequest().body("請提供 groomingItemId");
            }
            return ResponseEntity.ok(petService.lockGroomingItem(petId, groomingItemId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** 狗狗生病消瘦、換季毛況差很多、或當初選錯了，店員手動解鎖，恢復依體重自動篩選。 */
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{petId}/unlock-grooming-item")
    public ResponseEntity<?> unlockGroomingItem(@PathVariable Long petId) {
        try {
            return ResponseEntity.ok(petService.unlockGroomingItem(petId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** 結帳完成後提醒店員更新的體重，寫入這裡（同時會重新計算體型分類）。 */
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{petId}/weight")
    public ResponseEntity<?> updateWeight(@PathVariable Long petId,
                                           @RequestBody java.util.Map<String, Double> body) {
        try {
            Double weight = body.get("weight");
            if (weight == null || weight <= 0) {
                return ResponseEntity.badRequest().body("請提供有效的體重");
            }
            return ResponseEntity.ok(petService.updateWeight(petId, weight));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 需求（追加，2026-08-26）：店家後台刪除寵物。有預約或消費紀錄的話會被
    // PetService.deletePet() 擋下（見該方法說明），不用在這裡重複檢查。
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @DeleteMapping("/{petId}")
    public ResponseEntity<?> deletePet(@PathVariable Long petId) {
        try {
            petService.deletePet(petId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
