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
}
