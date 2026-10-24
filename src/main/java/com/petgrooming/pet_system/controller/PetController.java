package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PetException;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PetDiscountStatusService;
import com.petgrooming.pet_system.service.PetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final OperationLogService operationLogService;
    private final PetDiscountStatusService petDiscountStatusService; // 寵物折扣狀態查詢

    // ── POST /api/pets ─────────────────────────────────────────────────────
    // 新增寵物，以 JWT 解析出的 username 識別飼主
    // @Valid 觸發 PetRequest 的 Bean Validation，驗證失敗自動回 400
    @PostMapping
    public ResponseEntity<?> addPet(
            @Valid @RequestBody PetRequest petRequest,
            @CurrentUser String username) {
        PetResponse res = petService.addPet(username, petRequest);
        operationLogService.logByUsername(username, "CUSTOMER", "ADD_PET",
                "寵物 " + res.getName() + " #" + res.getId(), res.getBreed());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // ── PUT /api/pets/{petId} ────────────────────────────────────────────────
    // 顧客在 LIFF「我的寵物」編輯自己的寵物資料。
    // 物種（petType）不開放修改，PetRequest 裡即使傳了也會被忽略——
    // 實際更新邏輯（PetService.updatePet）只採用 name/breed/weight/age 等欄位，
    // 物種沿用資料庫既有值。
    @PutMapping("/{petId}")
    public ResponseEntity<?> updatePet(
            @PathVariable Long petId,
            @Valid @RequestBody PetRequest petRequest,
            @CurrentUser String username) {
        petService.assertOwnership(petId, username);
        PetResponse res = petService.updatePet(petId, petRequest);
        operationLogService.logByUsername(username, "CUSTOMER", "UPDATE_PET",
                "寵物 " + res.getName() + " #" + res.getId(), res.getBreed());
        return ResponseEntity.ok(res);
    }

    // ── DELETE /api/pets/{petId} ──────────────────────────────────────────
    // 顧客自己在 LIFF「我的毛孩」刪除自己的寵物。
    // 有預約或消費紀錄的話會被 PetService.deletePet() 擋下（見該方法說明）。
    @DeleteMapping("/{petId}")
    public ResponseEntity<?> deletePet(@PathVariable Long petId, @CurrentUser String username) {
        petService.assertOwnership(petId, username);
        petService.deletePet(petId);
        operationLogService.logByUsername(username, "CUSTOMER", "DELETE_PET",
                "寵物 #" + petId, null);
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/pets/cat-breeds ────────────────────────────────────────────
    // LIFF「新增毛孩」頁面貓咪品種下拉選單資料來源，不需要特別檢查
    // 身分是誰（只是回傳一份對照表清單，不含任何使用者個資），有登入即可呼叫。
    @GetMapping("/cat-breeds")
    public ResponseEntity<?> listCatBreeds() {
        var breeds = petService.listCatBreedOptions().stream()
                .map(b -> Map.of(
                        "breedName", b.getBreedName(),
                        "coatCategory", b.getCoatCategory().name(),
                        "coatCategoryLabel", b.getCoatCategory().getLabel()))
                .toList();
        return ResponseEntity.ok(breeds);
    }

    // ── GET /api/pets/my ───────────────────────────────────────────────────
    @GetMapping("/my")
    public ResponseEntity<?> getMyPets(@CurrentUser String username) {
        List<PetResponse> res = petService.getMyPets(username);
        return ResponseEntity.ok(res);
    }

    // ── POST /api/pets/{id}/photo ────────────────────────────────────────
    // LIFF「我的毛孩」上傳/更換寵物照片。只能傳自己名下的寵物。
    @PostMapping("/{id}/photo")
    public ResponseEntity<?> uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @CurrentUser String username) {
        var pet = petService.getPetEntity(id);
        if (!pet.getOwner().getUsername().equals(username)) {
            throw new PetException(ErrorCode.FORBIDDEN, "只能上傳自己寵物的照片");
        }
        PetResponse res = petService.updatePhoto(id, file);
        operationLogService.logByUsername(username, "CUSTOMER", "UPLOAD_PET_PHOTO",
                "寵物 " + res.getName() + " #" + res.getId(), null);
        return ResponseEntity.ok(res);
    }

    // ── GET /api/pets/{petId}/grooming-history ────────────────────────────
    // LIFF「我的毛孩」查看每次美容的狀況備註與照片
    @GetMapping("/{petId}/grooming-history")
    public ResponseEntity<?> getGroomingHistory(@PathVariable Long petId, @CurrentUser String username) {
        petService.assertOwnership(petId, username);
        return ResponseEntity.ok(petService.getGroomingHistory(petId));
    }

    // ── GET /api/pets/{petId}/discount-status ──────────────────────────────
    // LIFF 預約頁顯示的折扣資格（規則見 PetDiscountStatusService）
    @GetMapping("/{petId}/discount-status")
    public ResponseEntity<?> getDiscountStatus(
            @PathVariable Long petId,
            // 距上次洗澡幾天／是否符合定期養護禮遇資格，
            // 應該用「顧客實際選的預約日期」去算，不是永遠用「今天」——
            // 例如顧客選好幾個月後的日期，等到那天早就超過90天，不該還顯示
            // 符合資格。前端在選好日期後會帶這個參數重新查一次；還沒選日期時
            // （例如剛選完毛孩、日期欄位還空著）不帶這個參數，退回用「今天」
            // 當基準日，先讓使用者看到一個大概的狀態。
            @RequestParam(required = false) String asOfDate,
            @CurrentUser String username) {
        return ResponseEntity.ok(petDiscountStatusService.statusFor(petId, username, referenceDate(asOfDate)));
    }

    /** 還沒選日期或格式錯誤時退回用今天，不要因為這個附加參數壞掉而讓整支 API 掛掉。 */
    private static LocalDate referenceDate(String asOfDate) {
        if (asOfDate == null || asOfDate.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(asOfDate);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}

