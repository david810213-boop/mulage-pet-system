package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
import com.petgrooming.pet_system.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 需求（追加，2026-09-06）：寵物信息管理頁面。
 *
 * 背景：鎖定/解鎖固定套餐這個功能，原本只能在「會員信息」寵物卡片、
 * 「現場開單」／「預約現場加單」頁面操作，店家要先找到會員或那張單才
 * 摸得到。這裡集中列出全站所有寵物（跨會員），把鎖定狀態跟操作按鈕
 * 一起放進來，不用先繞去別的頁面。
 *
 * 鎖定/解鎖用的還是既有的 API（AdminPetController 的
 * PUT /api/admin/pets/{petId}/lock-grooming-item、.../unlock-grooming-item），
 * 這裡沒有新增任何寫入邏輯，純粹是多一個看得到、操作得到的入口。
 */
@Controller
@RequestMapping("/admin/pet-database")
@RequiredArgsConstructor
public class PetDatabaseMvcController {

    private final PetService petService;
    private final GroomingService groomingItemService;

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String index(Model model) {
        model.addAttribute("pets", petService.listAllPets());
        // 給前端 JS 篩選候選套餐用（依每隻狗的體重/毛長找出符合的項目讓店員選鎖定哪一個）
        model.addAttribute("groomingItems", groomingItemService.getAllItems());
        return "admin/pet-database";
    }
}
