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
 * 寵物信息管理頁面：集中列出全站所有寵物（跨會員）與固定組合，
 * 不用等到結帳核對就能鎖定、編輯或解鎖。寫入走 AdminPetController 的 API。
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
