package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final OperationLogService operationLogService;

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
}

