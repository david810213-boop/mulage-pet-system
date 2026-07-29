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

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) return null;
        try { return userService.getUserEntityByUsername(username); }
        catch (Exception e) { return null; }
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
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
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{username}")
    public String customerDetail(@PathVariable String username, HttpServletRequest request, Model model) {
        model.addAttribute("user", getLoginUser(request));

        User customer = userService.getUserEntityByUsername(username);
        model.addAttribute("customer", customer);
        model.addAttribute("wallet", walletService.getWallet(username));
        model.addAttribute("appointments", appointmentService.getMyAppointments(username));

        java.util.List<com.petgrooming.pet_system.dto.ConsumptionRecordResponse> records = new java.util.ArrayList<>();

        // 來源 1：預約結帳（Transaction）
        for (var t : transactionRepository.findByUserUsername(username)) {
            if (!t.isPaid()) continue;
            records.add(com.petgrooming.pet_system.dto.ConsumptionRecordResponse.builder()
                    .sourceLabel("預約結帳")
                    .code(t.getAppointment() != null ? String.format("AP%03d", t.getAppointment().getId()) : "—")
                    .petName(t.getAppointment() != null ? t.getAppointment().getPetName() : "—")
                    .time(t.getPaymentTime())
                    .handledBy(t.getHandledBy())
                    .paymentMethodLabel(t.getPaymentMethod() != null ? t.getPaymentMethod().getDisplayName() : "—")
                    .amount(t.getFinalAmount())
                    .paid(true)
                    .build());
        }

        // 來源 2：現場開單（WalkInOrder）—— 之前沒有併入會員信息頁，這次補上
        for (var w : walkInOrderRepository.findByMemberUsernameOrderByCreatedAtDesc(username)) {
            if (!w.isPaid()) continue;
            records.add(com.petgrooming.pet_system.dto.ConsumptionRecordResponse.builder()
                    .sourceLabel("現場開單")
                    .code("現場單#" + w.getId())
                    .petName(w.getPetName())
                    .time(w.getPaymentTime())
                    .handledBy(w.getCreatedBy())
                    .paymentMethodLabel(w.getPaymentMethod() != null ? w.getPaymentMethod().getDisplayName() : "—")
                    .amount(w.getTotalAmount())
                    .paid(true)
                    .build());
        }

        records.sort((a, b) -> {
            if (a.getTime() == null && b.getTime() == null) return 0;
            if (a.getTime() == null) return 1;
            if (b.getTime() == null) return -1;
            return b.getTime().compareTo(a.getTime());
        });
        model.addAttribute("transactions", records);

        int totalSpent = records.stream().mapToInt(com.petgrooming.pet_system.dto.ConsumptionRecordResponse::getAmount).sum();
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
        var groomingNotesByPetId = new java.util.HashMap<Long, java.util.List<com.petgrooming.pet_system.model.PetGroomingNote>>();
        for (var p : pets) {
            groomingNotesByPetId.put(p.getId(),
                    petGroomingNoteRepository.findByPetIdOrderByServiceDateDescCreatedAtDesc(p.getId()));
        }
        model.addAttribute("groomingNotesByPetId", groomingNotesByPetId);

        return "admin/customer-detail";
    }
}
