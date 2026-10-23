package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.CatCoatCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.CatBreedCoatMapping;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.CatBreedCoatMappingService;
import com.petgrooming.pet_system.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 需求（追加）：貓咪品種→毛髮分類對照表後台管理。店家自己就能新增/調整品種
 * 跟對應分類，不用改程式碼重新部署——之後遇到新品種或分類判斷需要調整，
 * 直接來這裡改就好。
 */
@Controller
@RequestMapping("/admin/cat-breed-coat-mapping")
@RequiredArgsConstructor
public class CatBreedCoatMappingMvcController {

    private static final String REDIRECT_LIST = "redirect:/admin/cat-breed-coat-mapping";

    private final CatBreedCoatMappingService mappingService;
    private final OperationLogService operationLogService;

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String list(Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        model.addAttribute("mappings", mappingService.listAll());
        model.addAttribute("categories", CatCoatCategory.values());
        return "admin/cat-breed-coat-mapping";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/add")
    public String add(@RequestParam String breedName,
                       @RequestParam CatCoatCategory coatCategory,
                       @RequestParam(defaultValue = "0") Integer sortOrder,
                       RedirectAttributes ra,
                       @CurrentUser User user) {
        try {
            CatBreedCoatMapping added = mappingService.add(breedName, coatCategory, sortOrder);
            operationLogService.log(user, "PET", "ADD_CAT_BREED_MAPPING",
                    "新增品種對照：" + added.getBreedName() + " → " + coatCategory.getLabel(), null);
            ra.addFlashAttribute("successMsg", "已新增「" + added.getBreedName() + "」");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return REDIRECT_LIST;
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                          @RequestParam String breedName,
                          @RequestParam CatCoatCategory coatCategory,
                          @RequestParam(defaultValue = "0") Integer sortOrder,
                          RedirectAttributes ra,
                          @CurrentUser User user) {
        try {
            CatBreedCoatMapping updated = mappingService.update(id, breedName, coatCategory, sortOrder);
            operationLogService.log(user, "PET", "UPDATE_CAT_BREED_MAPPING",
                    "修改品種對照 #" + id + "：" + updated.getBreedName() + " → " + coatCategory.getLabel(), null);
            ra.addFlashAttribute("successMsg", "已更新「" + updated.getBreedName() + "」");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return REDIRECT_LIST;
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra, @CurrentUser User user) {
        mappingService.delete(id).ifPresent(deleted -> {
            operationLogService.log(user, "PET", "DELETE_CAT_BREED_MAPPING",
                    "刪除品種對照：" + deleted.getBreedName(), null);
            ra.addFlashAttribute("successMsg", "已刪除「" + deleted.getBreedName() + "」");
        });
        return REDIRECT_LIST;
    }
}
