package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pets")
@RequiredArgsConstructor
public class PetMvcController {

    private final PetService petService;
    private final UserService userService;
    private final OperationLogService operationLogService;

    /**
     * JWT 版獲取當前登入使用者
     * 從 LoginInterceptor 存入的 request attribute 拿取 username，
     * 再用 UserService 查出完整的 User entity，沒有就回傳 null
     */
    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping
    public String list(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";
        model.addAttribute("user", user);
        model.addAttribute("pets", petService.getMyPets(user.getUsername()));
        return "pets/list";
    }

    @GetMapping("/new")
    public String newForm(HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";
        model.addAttribute("user", user);
        model.addAttribute("petRequest", new PetRequest());
        model.addAttribute("petTypes", PetType.values());
        model.addAttribute("coatTypes", CoatType.values());
        return "pets/form";
    }

    @PostMapping("/submit")
    public String submit(@Valid @ModelAttribute PetRequest req,
                         BindingResult bindingResult,
                         // 需求 2：毛長由店家（後台）定義；此 param 僅後台 Thymeleaf 表單使用
                         @RequestParam(name = "coatType", required = false) CoatType coatType,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes,
                         Model model) {

        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("petTypes", PetType.values());
            model.addAttribute("coatTypes", CoatType.values());
            return "pets/form";
        }

        try {
            var created = petService.addPet(user.getUsername(), req);
            // 後台建立時若有選毛長，直接由店家定義寫入
            if (coatType != null && created != null && created.getId() != null) {
                petService.setCoatType(created.getId(), coatType);
            }
            operationLogService.log(user, "CUSTOMER", "ADD_PET",
                    "寵物 " + (created != null ? created.getName() : "") + " #" + (created != null ? created.getId() : ""),
                    req.getBreed());
            redirectAttributes.addFlashAttribute("successMsg", "寵物新增成功！");
            return "redirect:/pets";
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", user);
            model.addAttribute("petTypes", PetType.values());
            model.addAttribute("coatTypes", CoatType.values());
            model.addAttribute("errorMsg", e.getMessage());
            return "pets/form";
        }
    }

    // ── POST /pets/{id}/photo ────────────────────────────────────────────
    // 網頁版毛孩照片上傳（跟 LIFF 版一樣的概念：先建立寵物，再上傳照片，
    // 因為建立當下還沒有 petId）。只能傳自己名下的寵物。
    @PostMapping("/{id}/photo")
    public String uploadPhoto(@PathVariable Long id,
                              @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        User user = getLoginUser(request);
        if (user == null) return "redirect:/auth/login";

        try {
            var pet = petService.getPetEntity(id);
            if (!pet.getOwner().getUsername().equals(user.getUsername())) {
                ra.addFlashAttribute("errorMsg", "只能上傳自己寵物的照片");
                return "redirect:/pets";
            }
            var updated = petService.updatePhoto(id, file);
            operationLogService.log(user, "CUSTOMER", "UPLOAD_PET_PHOTO",
                    "寵物 " + updated.getName() + " #" + updated.getId(), null);
            ra.addFlashAttribute("successMsg", "照片已更新");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", "上傳失敗：" + e.getMessage());
        }
        return "redirect:/pets";
    }
}
