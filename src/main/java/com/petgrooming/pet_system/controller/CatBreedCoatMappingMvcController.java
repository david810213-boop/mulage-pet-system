package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.CatCoatCategory;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.CatBreedCoatMapping;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.CatBreedCoatMappingRepository;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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

    private final CatBreedCoatMappingRepository repository;
    private final UserService userService;
    private final OperationLogService operationLogService;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (Exception e) {
            return null;
        }
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public String list(HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("mappings", repository.findAllByOrderBySortOrderAscBreedNameAsc());
        model.addAttribute("categories", CatCoatCategory.values());
        return "admin/cat-breed-coat-mapping";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/add")
    public String add(HttpServletRequest request,
                       @RequestParam String breedName,
                       @RequestParam CatCoatCategory coatCategory,
                       @RequestParam(defaultValue = "0") Integer sortOrder,
                       RedirectAttributes ra) {
        User user = getLoginUser(request);
        String trimmed = breedName == null ? "" : breedName.trim();
        if (trimmed.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "品種名稱不能空白");
            return "redirect:/admin/cat-breed-coat-mapping";
        }
        if (repository.existsByBreedName(trimmed)) {
            ra.addFlashAttribute("errorMsg", "「" + trimmed + "」已經在對照表裡了，不能重複新增");
            return "redirect:/admin/cat-breed-coat-mapping";
        }
        repository.save(CatBreedCoatMapping.builder()
                .breedName(trimmed)
                .coatCategory(coatCategory)
                .sortOrder(sortOrder)
                .build());
        operationLogService.log(user, "PET", "ADD_CAT_BREED_MAPPING",
                "新增品種對照：" + trimmed + " → " + coatCategory.getLabel(), null);
        ra.addFlashAttribute("successMsg", "已新增「" + trimmed + "」");
        return "redirect:/admin/cat-breed-coat-mapping";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/update")
    public String update(HttpServletRequest request,
                          @PathVariable Long id,
                          @RequestParam String breedName,
                          @RequestParam CatCoatCategory coatCategory,
                          @RequestParam(defaultValue = "0") Integer sortOrder,
                          RedirectAttributes ra) {
        User user = getLoginUser(request);
        CatBreedCoatMapping mapping = repository.findById(id).orElse(null);
        if (mapping == null) {
            ra.addFlashAttribute("errorMsg", "找不到這筆對照資料");
            return "redirect:/admin/cat-breed-coat-mapping";
        }
        String trimmed = breedName == null ? "" : breedName.trim();
        if (trimmed.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "品種名稱不能空白");
            return "redirect:/admin/cat-breed-coat-mapping";
        }
        mapping.setBreedName(trimmed);
        mapping.setCoatCategory(coatCategory);
        mapping.setSortOrder(sortOrder);
        repository.save(mapping);
        operationLogService.log(user, "PET", "UPDATE_CAT_BREED_MAPPING",
                "修改品種對照 #" + id + "：" + trimmed + " → " + coatCategory.getLabel(), null);
        ra.addFlashAttribute("successMsg", "已更新「" + trimmed + "」");
        return "redirect:/admin/cat-breed-coat-mapping";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/delete")
    public String delete(HttpServletRequest request, @PathVariable Long id, RedirectAttributes ra) {
        User user = getLoginUser(request);
        CatBreedCoatMapping mapping = repository.findById(id).orElse(null);
        if (mapping != null) {
            repository.deleteById(id);
            operationLogService.log(user, "PET", "DELETE_CAT_BREED_MAPPING",
                    "刪除品種對照：" + mapping.getBreedName(), null);
            ra.addFlashAttribute("successMsg", "已刪除「" + mapping.getBreedName() + "」");
        }
        return "redirect:/admin/cat-breed-coat-mapping";
    }
}
