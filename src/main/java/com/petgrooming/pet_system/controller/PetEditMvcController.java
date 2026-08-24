package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 需求（追加，2026-08-24）：後台「完整編輯寵物資料」頁面。原本「會員信息」
 * 寵物分頁裡的快速編輯表單只有名字/品種/體重/年齡，跟 LIFF「新增/編輯毛孩」
 * 蒐集的完整資料（性別、絕育、晶片、個性、病史、指定獸醫院）對不上，店家在
 * 後台看不到、也改不了這些欄位。這裡新增一個獨立頁面，內容跟 LIFF 那份
 * 完全對齊，用一般的 select/checkbox 呈現（後台是店員用，不用像 LIFF 那樣
 * 做成大按鈕點選的樣式）。
 */
@Controller
@RequestMapping("/admin/pets")
@RequiredArgsConstructor
public class PetEditMvcController {

    private final PetService petService;
    private final PetRepository petRepository;
    private final UserService userService;

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
    @GetMapping("/{id}/edit")
    public String editPage(@PathVariable Long id, HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));
        var pet = petRepository.findById(id).orElse(null);
        if (pet == null) {
            model.addAttribute("errorMsg", "找不到這隻寵物");
            return "admin/pet-edit";
        }
        model.addAttribute("pet", com.petgrooming.pet_system.dto.PetResponse.from(pet));
        model.addAttribute("catBreeds", petService.listCatBreedOptions());
        // 個性/病史選項，跟 LIFF add-pet.html 用同一份清單，維持兩邊一致
        model.addAttribute("personalityOptions", java.util.List.of(
                "親近人", "親近狗", "會咬人", "不會咬人", "會咬狗貓", "不會咬狗貓", "容易緊張", "有攻擊性"));
        model.addAttribute("healthOptions", java.util.List.of(
                "心臟病", "氣喘", "氣管塌陷", "白內障", "癲癇", "心絲蟲", "艾利希體", "腹膜炎",
                "腹積水", "手術外傷未癒合", "髖關節問題", "骨折", "腸炎", "血便", "血尿", "懷孕", "傳染性疾病"));
        return "admin/pet-edit";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/edit")
    public String submit(@PathVariable Long id,
                          @ModelAttribute PetRequest petRequest,
                          @RequestParam(required = false) String returnTo,
                          HttpServletRequest request, RedirectAttributes ra) {
        User user = getLoginUser(request);
        var existing = petRepository.findById(id).orElse(null);
        if (existing == null) {
            ra.addFlashAttribute("errorMsg", "找不到這隻寵物");
            return "redirect:/admin/pets/" + id + "/edit";
        }
        // petType 不開放修改（理由跟 PetService.updatePet() 的說明一致），
        // 表單裡沒有這個欄位，這裡直接用資料庫既有值，避免 @NotNull 驗證擋下整筆送出。
        petRequest.setPetType(existing.getPetType());
        try {
            petService.updatePet(id, petRequest);
            ra.addFlashAttribute("successMsg", "寵物資料已更新");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", "更新失敗：" + e.getMessage());
        }
        return "redirect:" + (returnTo != null && !returnTo.isBlank() ? returnTo : "/admin/pets/" + id + "/edit");
    }
}
