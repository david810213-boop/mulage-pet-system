package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PetGroomingNoteRepository;
import com.petgrooming.pet_system.repository.TransactionRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.CustomerAnalysisService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalletService;
import com.petgrooming.pet_system.service.WalkInOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 後台「會員信息」頁面：新客／回流客總覽 + 會員清單 + 會員完整資訊（資料/寵物/預約/消費）
@Controller
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
public class CustomerAnalysisMvcController {

    private final CustomerAnalysisService customerAnalysisService;
    private final UserService userService;
    private final PetService petService;
    private final WalletService walletService;
    private final AppointmentService appointmentService;
    private final PetGroomingNoteRepository petGroomingNoteRepository;
    private final TransactionRepository transactionRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final WalkInOrderService walkInOrderService;
    private final com.petgrooming.pet_system.service.MemberConsumptionService memberConsumptionService; // 需求（2026-09-24）

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null)
            return null;
        try {
            return userService.getUserEntityByUsername(username);
        } catch (Exception e) {
            return null;
        }
    }

    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @GetMapping
    public String overview(HttpServletRequest request, Model model,
            @RequestParam(required = false) String keyword) {
        model.addAttribute("user", getLoginUser(request));
        model.addAttribute("stats", customerAnalysisService.getOverview());

        var all = userService.getAllCustomers();
        var filtered = all.stream()
                .filter(c -> keyword == null || keyword.isBlank()
                        || c.getName().contains(keyword)
                        || c.getUsername().contains(keyword))
                .toList();
        model.addAttribute("customers", filtered);
        model.addAttribute("keyword", keyword);
        return "admin/customer-analysis";
    }

    // ── GET /admin/customers/{username} ─────────────────────────────────────
    // 會員信息詳情：整合會員資料、寵物資料、預約紀錄、消費紀錄於同一頁
    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @GetMapping("/{username}")
    public String customerDetail(@PathVariable String username, HttpServletRequest request, Model model) {
        User loginUser = getLoginUser(request);
        model.addAttribute("user", loginUser);

        User customer = userService.getUserEntityByUsername(username);
        model.addAttribute("customer", customer);
        model.addAttribute("wallet", walletService.getWallet(username));
        model.addAttribute("appointments", appointmentService.getMyAppointments(username));

        // 需求（2026-09-24）：消費紀錄彙整邏輯搬到 MemberConsumptionService 共用（員工手機版也要用），內容不變
        java.util.List<com.petgrooming.pet_system.dto.ConsumptionRecordResponse> records =
                memberConsumptionService.buildPaidRecords(username, loginUser != null ? loginUser.getUsername() : null);
        model.addAttribute("transactions", records);

        int totalSpent = records.stream().mapToInt(com.petgrooming.pet_system.dto.ConsumptionRecordResponse::getAmount)
                .sum();
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("orderCount", records.size());
        model.addAttribute("lastPaymentTime",
                records.stream()
                        .map(com.petgrooming.pet_system.dto.ConsumptionRecordResponse::getTime)
                        .filter(java.util.Objects::nonNull)
                        .max(java.time.LocalDateTime::compareTo)
                        .orElse(null));

        var pets = petService.getMyPets(username);
        model.addAttribute("pets", pets);
        model.addAttribute("coatTypes", CoatType.values());
        // 需求（追加）：貓咪品種下拉選單資料來源 + 毛髮分類選項，供編輯基本資料表單使用
        model.addAttribute("catBreeds", petService.listCatBreedOptions());
        model.addAttribute("catCoatCategories", com.petgrooming.pet_system.enums.CatCoatCategory.values());
        var groomingNotesByPetId = new java.util.HashMap<Long, java.util.List<com.petgrooming.pet_system.model.PetGroomingNote>>();
        // 需求 8-3：消費項目明細整合顯示在美容歷史卡片旁——依寵物名稱把上面已經整理好的
        // 消費紀錄（records）分組。沿用需求 9 既有的「用寵物名稱文字比對」慣例
        // （Appointment / WalkInOrder 都只存 petName 快照，沒有直接關聯 Pet 實體）。
        var consumptionByPetId = new java.util.HashMap<Long, java.util.List<com.petgrooming.pet_system.dto.ConsumptionRecordResponse>>();
        for (var p : pets) {
            groomingNotesByPetId.put(p.getId(),
                    petGroomingNoteRepository.findByPetIdOrderByServiceDateDescCreatedAtDesc(p.getId()));
            consumptionByPetId.put(p.getId(),
                    records.stream()
                            .filter(r -> p.getName().equals(r.getPetName()))
                            .toList());
        }
        model.addAttribute("groomingNotesByPetId", groomingNotesByPetId);
        model.addAttribute("consumptionByPetId", consumptionByPetId);

        return "admin/customer-detail";
    }
}
