package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.PetDiscountStatusResponse;
import com.petgrooming.pet_system.service.CatFirstVisitDiscountService;
import com.petgrooming.pet_system.service.CatRewashDiscountService;
import com.petgrooming.pet_system.service.DogFirstVisitDiscountService;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PetConsumptionHistoryService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final OperationLogService operationLogService;
    // 需求（追加，2026-09-04）：寵物折扣狀態查詢用
    private final PetConsumptionHistoryService petConsumptionHistoryService;
    private final CatRewashDiscountService catRewashDiscountService;
    private final CatFirstVisitDiscountService catFirstVisitDiscountService;
    private final DogFirstVisitDiscountService dogFirstVisitDiscountService;
    private final WalletService walletService;

    // 從 LoginInterceptor 解析 JWT 後存入的 request attribute 取得目前登入者
    // 不論是店家網頁登入（WEB）還是顧客 LINE 登入（LINE），走同一套機制
    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute("tokenUsername");
    }

    // ── POST /api/pets ─────────────────────────────────────────────────────
    // 新增寵物，以 JWT 解析出的 username 識別飼主
    // @Valid 觸發 PetRequest 的 Bean Validation，驗證失敗自動回 400
    @PostMapping
    public ResponseEntity<?> addPet(
            HttpServletRequest request,
            @Valid @RequestBody PetRequest petRequest) {
        try {
            PetResponse res = petService.addPet(currentUsername(request), petRequest);
            operationLogService.logByUsername(currentUsername(request), "CUSTOMER", "ADD_PET",
                    "寵物 " + res.getName() + " #" + res.getId(), res.getBreed());
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── PUT /api/pets/{petId} ────────────────────────────────────────────────
    // 需求（追加）：顧客在 LIFF「我的寵物」編輯自己的寵物資料。
    // 物種（petType）不開放修改，PetRequest 裡即使傳了也會被忽略——
    // 實際更新邏輯（PetService.updatePet）只採用 name/breed/weight/age 等欄位，
    // 物種沿用資料庫既有值。
    @PutMapping("/{petId}")
    public ResponseEntity<?> updatePet(
            HttpServletRequest request,
            @PathVariable Long petId,
            @Valid @RequestBody PetRequest petRequest) {
        try {
            petService.assertOwnership(petId, currentUsername(request));
            PetResponse res = petService.updatePet(petId, petRequest);
            operationLogService.logByUsername(currentUsername(request), "CUSTOMER", "UPDATE_PET",
                    "寵物 " + res.getName() + " #" + res.getId(), res.getBreed());
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── DELETE /api/pets/{petId} ──────────────────────────────────────────
    // 需求（追加，2026-08-26）：顧客自己在 LIFF「我的毛孩」刪除自己的寵物。
    // 有預約或消費紀錄的話會被 PetService.deletePet() 擋下（見該方法說明）。
    @DeleteMapping("/{petId}")
    public ResponseEntity<?> deletePet(HttpServletRequest request, @PathVariable Long petId) {
        try {
            petService.assertOwnership(petId, currentUsername(request));
            petService.deletePet(petId);
            operationLogService.logByUsername(currentUsername(request), "CUSTOMER", "DELETE_PET",
                    "寵物 #" + petId, null);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── GET /api/pets/cat-breeds ────────────────────────────────────────────
    // 需求（追加）：LIFF「新增毛孩」頁面貓咪品種下拉選單資料來源，不需要特別檢查
    // 身分是誰（只是回傳一份對照表清單，不含任何使用者個資），有登入即可呼叫。
    @GetMapping("/cat-breeds")
    public ResponseEntity<?> listCatBreeds() {
        var breeds = petService.listCatBreedOptions().stream()
                .map(b -> java.util.Map.of(
                        "breedName", b.getBreedName(),
                        "coatCategory", b.getCoatCategory().name(),
                        "coatCategoryLabel", b.getCoatCategory().getLabel()))
                .toList();
        return ResponseEntity.ok(breeds);
    }

    // ── GET /api/pets/my ───────────────────────────────────────────────────
    @GetMapping("/my")
    public ResponseEntity<?> getMyPets(HttpServletRequest request) {
        try {
            List<PetResponse> res = petService.getMyPets(currentUsername(request));
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── POST /api/pets/{id}/photo ────────────────────────────────────────
    // 需求 17：LIFF「我的毛孩」上傳/更換寵物照片。只能傳自己名下的寵物。
    @PostMapping("/{id}/photo")
    public ResponseEntity<?> uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest request) {
        try {
            var pet = petService.getPetEntity(id);
            if (!pet.getOwner().getUsername().equals(currentUsername(request))) {
                return ResponseEntity.status(403).body("只能上傳自己寵物的照片");
            }
            PetResponse res = petService.updatePhoto(id, file);
            operationLogService.logByUsername(currentUsername(request), "CUSTOMER", "UPLOAD_PET_PHOTO",
                    "寵物 " + res.getName() + " #" + res.getId(), null);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── GET /api/pets/{petId}/discount-status ──────────────────────────────
    // 需求（追加，2026-09-04）：LIFF 預約頁顯示「距上次洗澡幾天、優惠期內/
    // 已過期」，以及服務項目要不要顯示「原價劃掉+折扣價」的判斷資料來源。
    // 三種折扣互斥擇優：首次體驗優惠／90天回洗優惠（兩者本身也互斥，貓咪
    // 才可能兩者都適用，狗狗只有首次體驗）跟會員儲值折扣分開回傳，前端渲染
    // 時自己取較優惠者，邏輯跟後端 resolvePreferredDiscount() 一致。
    @GetMapping("/{petId}/discount-status")
    public ResponseEntity<?> getDiscountStatus(
            HttpServletRequest request,
            @PathVariable Long petId,
            // 需求（追加，2026-09-06）：距上次洗澡幾天／是否符合定期養護禮遇資格，
            // 應該用「顧客實際選的預約日期」去算，不是永遠用「今天」——
            // 例如顧客選好幾個月後的日期，等到那天早就超過90天，不該還顯示
            // 符合資格。前端在選好日期後會帶這個參數重新查一次；還沒選日期時
            // （例如剛選完毛孩、日期欄位還空著）不帶這個參數，退回用「今天」
            // 當基準日，先讓使用者看到一個大概的狀態。
            @org.springframework.web.bind.annotation.RequestParam(required = false) String asOfDate) {
        try {
            petService.assertOwnership(petId, currentUsername(request));
            var pet = petService.getPetEntity(petId);
            var owner = pet.getOwner();
            String petType = pet.getPetType().name();
            boolean isCat = "CAT".equals(petType);
            boolean isDog = "DOG".equals(petType);

            LocalDate referenceDate = LocalDate.now();
            if (asOfDate != null && !asOfDate.isBlank()) {
                try {
                    referenceDate = LocalDate.parse(asOfDate);
                } catch (java.time.format.DateTimeParseException e) {
                    // 格式錯誤就忽略，退回用今天，不要因為這個附加參數壞掉
                    // 而讓整支 API 掛掉
                }
            }

            boolean firstVisitEligible = !petConsumptionHistoryService.hasPriorPaidService(
                    owner.getId(), pet.getName(), null);

            boolean rewashEligible = false;
            Long lastBathDaysAgo = null;
            if (isCat) {
                var lastBath = catRewashDiscountService.findLastBathDate(owner.getId(), pet.getName());
                if (lastBath.isPresent()) {
                    lastBathDaysAgo = ChronoUnit.DAYS.between(lastBath.get(), referenceDate);
                    rewashEligible = lastBathDaysAgo >= 0 && lastBathDaysAgo < CatRewashDiscountService.REWASH_WINDOW_DAYS;
                }
            }

            double memberDiscountRate = walletService.getWallet(owner.getUsername()).getDiscount();

            String specialLabel = null;
            Double specialRate = null;
            List<String> specialCategories = List.of();

            // 首次體驗跟定期養護禮遇互斥，首次體驗優先判斷（邏輯跟結帳時
            // populateDiscountInfo() 的判斷順序一致：先看是不是首次消費）
            if (isCat && firstVisitEligible) {
                specialLabel = "首次體驗優惠";
                specialRate = CatFirstVisitDiscountService.FIRST_VISIT_DISCOUNT_RATE;
                specialCategories = List.of("BATH_CAT_S", "BATH_CAT_L");
            } else if (isDog && firstVisitEligible) {
                specialLabel = "首次體驗優惠";
                specialRate = DogFirstVisitDiscountService.FIRST_VISIT_DISCOUNT_RATE;
                specialCategories = List.of("BATH_SMALL", "BATH_LARGE");
            } else if (isCat && rewashEligible) {
                // 需求（追加，2026-09-06）：文案改版，顧客端一律顯示「定期養護禮遇」，
                // 不用「回洗優惠」這個比較像內部行話的講法。後台（例如「貓咪回洗
                // 名單」管理頁）維持原本用語不變，這裡只改對顧客顯示的文字。
                specialLabel = "定期養護禮遇";
                specialRate = CatRewashDiscountService.REWASH_DISCOUNT_RATE;
                specialCategories = List.of("BATH_CAT_S", "BATH_CAT_L");
            }

            return ResponseEntity.ok(new PetDiscountStatusResponse(
                    petType, firstVisitEligible, rewashEligible, lastBathDaysAgo,
                    memberDiscountRate, specialLabel, specialRate, specialCategories));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

