package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * 後台「完整編輯寵物資料」頁面：欄位跟 LIFF「新增/編輯毛孩」蒐集的完整資料
 * （性別、絕育、晶片、個性、病史、指定獸醫院）完全對齊，用一般的 select/checkbox 呈現
 * （後台是店員用，不用做成 LIFF 那樣的大按鈕）。
 */
@Controller
@RequestMapping("/admin/pets")
@RequiredArgsConstructor
public class PetEditMvcController {

    private final PetService petService;

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{id}/edit")
    public String editPage(@PathVariable Long id, Model model, @CurrentUser User loginUser) {
        model.addAttribute("user", loginUser);
        var pet = petService.findPet(id).orElse(null);
        if (pet == null) {
            model.addAttribute("errorMsg", "找不到這隻寵物");
            return "admin/pet-edit";
        }
        var petResponse = PetResponse.from(pet);
        model.addAttribute("pet", petResponse);
        model.addAttribute("catBreeds", petService.listCatBreedOptions());
        // 個性/病史選項，跟 LIFF add-pet.html 用同一份清單，維持兩邊一致
        model.addAttribute("personalityOptions", List.of(
                "親近人", "親近狗", "會咬人", "不會咬人", "會咬狗貓", "不會咬狗貓", "容易緊張", "有攻擊性"));
        model.addAttribute("healthOptions", List.of(
                "心臟病", "氣喘", "氣管塌陷", "白內障", "癲癇", "心絲蟲", "艾利希體", "腹膜炎",
                "腹積水", "手術外傷未癒合", "髖關節問題", "骨折", "腸炎", "血便", "血尿", "懷孕", "傳染性疾病"));
        // 在後端先把逗號分隔字串轉成 List<String>，畫面只做 contains() 判斷。
        // 不要在樣板裡用 #strings.arraySplit(...).?[...] 做字串運算：空字串等邊界情況會噴例外，
        // Thymeleaf 串流輸出時畫面會從那裡整個截斷（後面的病史、獸醫院、儲存按鈕都不見）。
        model.addAttribute("selectedPersonalityTags", splitToList(petResponse.getPersonalityTags()));
        model.addAttribute("selectedHealthTags", splitToList(petResponse.getHealthHistory()));
        return "admin/pet-edit";
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/edit")
    public String submit(@PathVariable Long id,
                          @ModelAttribute PetRequest petRequest,
                          @RequestParam(required = false) String returnTo, RedirectAttributes ra,
                          @CurrentUser User user) {
        var existing = petService.findPet(id).orElse(null);
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

    // 把逗號分隔字串轉成 List<String>，null/空字串
    // 都回傳空 List（不是 null），避免 Thymeleaf 樣板裡還要另外判斷 null。
    private List<String> splitToList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
