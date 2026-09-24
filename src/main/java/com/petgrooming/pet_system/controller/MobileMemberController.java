package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.ConsumptionRecordResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.PetGroomingNote;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PetGroomingNoteRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.MemberConsumptionService;
import com.petgrooming.pet_system.service.MobileViewHelper;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 員工手機版：毛孩、會員（需求，2026-09-24 第五批）。
 *
 * 以查詢為主的頁面：毛孩檔案（美容紀錄、消費紀錄、即將到來的預約）、會員資料
 * （儲值金、名下毛孩、消費紀錄、店家備注）。現場最常改的三件事可以直接在手機上改：
 * 體重、毛長（呼叫既有的 /api/admin/pets API）、店家備注（/api/admin/members/{username}/note）；
 * 其餘完整編輯、手動綁定匯入資料等較少用的操作，連到網頁版（網址帶 desktop=1，不會被導回手機版）。
 */
@Controller
@RequestMapping("/m")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class MobileMemberController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final UserService userService;
    private final UserRepository userRepository;
    private final PetService petService;
    private final PetGroomingNoteRepository petGroomingNoteRepository;
    private final AppointmentService appointmentService;
    private final MemberConsumptionService memberConsumptionService;
    private final WalletService walletService;
    private final MobileViewHelper view;

    private User getLoginUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("tokenUsername");
        if (username == null) {
            return null;
        }
        try {
            return userService.getUserEntityByUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── 毛孩列表 ────────────────────────────────────────────────────────
    @GetMapping("/pets")
    public String pets(HttpServletRequest request, Model model,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "all") String f) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        String kw = q == null ? "" : q.trim().toLowerCase();
        List<PetResponse> all = petService.listAllPets();
        List<Map<String, Object>> rows = all.stream()
                .filter(p -> kw.isEmpty()
                        || (p.getName() != null && p.getName().toLowerCase().contains(kw))
                        || (p.getOwnerName() != null && p.getOwnerName().toLowerCase().contains(kw))
                        || (p.getBreed() != null && p.getBreed().toLowerCase().contains(kw)))
                .filter(p -> switch (f) {
                    case "dog" -> p.getPetType() != null && "DOG".equals(p.getPetType().name());
                    case "cat" -> p.getPetType() != null && "CAT".equals(p.getPetType().name());
                    case "locked" -> p.getLockedGroomingItemId() != null;
                    default -> true;
                })
                .limit(120)
                .map(this::petRow)
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("f", f);
        model.addAttribute("rows", rows);
        model.addAttribute("total", all.size());
        model.addAttribute("activeTab", "pets");
        return "m/pets";
    }

    // ── 毛孩檔案 ────────────────────────────────────────────────────────
    @GetMapping("/pets/{id}")
    public String pet(@PathVariable Long id, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        Pet entity;
        try {
            entity = petService.getPetEntity(id);
        } catch (IllegalArgumentException e) {
            return "redirect:/m/pets";
        }
        if (entity.getOwner() == null) {
            return "redirect:/m/pets";
        }
        String owner = entity.getOwner().getUsername();
        // 用 getMyPets() 取，才會帶出鎖定套餐的名稱／價格，也會自動排除已刪除的寵物
        PetResponse pet = petService.getMyPets(owner).stream()
                .filter(p -> p.getId().equals(id))
                .findFirst().orElse(null);
        if (pet == null) {
            return "redirect:/m/pets";
        }

        List<Map<String, Object>> notes = petGroomingNoteRepository.findByPetIdOrderByServiceDateDescCreatedAtDesc(id)
                .stream().limit(10).map(this::noteRow).toList();
        List<Map<String, Object>> records = memberConsumptionService.buildPaidRecords(owner, user.getUsername())
                .stream().filter(r -> pet.getName().equals(r.getPetName()))
                .limit(10).map(this::recordRow).toList();
        List<Map<String, Object>> upcoming = upcomingAppointments(owner, pet.getName());

        model.addAttribute("user", user);
        model.addAttribute("pet", pet);
        model.addAttribute("species", view.speciesKey(pet.getPetType() != null ? pet.getPetType().name() : null));
        model.addAttribute("initial", view.initial(pet.getName(), "?"));
        model.addAttribute("ownerUsername", owner);
        model.addAttribute("ownerName", entity.getOwner().getName());
        model.addAttribute("personality", splitTags(pet.getPersonalityTags()));
        model.addAttribute("health", splitTags(pet.getHealthHistory()));
        model.addAttribute("notes", notes);
        model.addAttribute("records", records);
        model.addAttribute("upcoming", upcoming);
        model.addAttribute("activeTab", "pets");
        return "m/pet";
    }

    // ── 會員列表 ────────────────────────────────────────────────────────
    @GetMapping("/members")
    public String members(HttpServletRequest request, Model model,
            @RequestParam(required = false) String q) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        String kw = q == null ? "" : q.trim().toLowerCase();
        String digits = kw.replaceAll("[^0-9]", "");
        List<User> all = userService.getAllCustomers();
        List<Map<String, Object>> rows = all.stream()
                .filter(u -> kw.isEmpty()
                        || (u.getName() != null && u.getName().toLowerCase().contains(kw))
                        || (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw))
                        || (digits.length() >= 3 && u.getPhone() != null
                                && u.getPhone().replaceAll("[^0-9]", "").contains(digits)))
                .sorted(Comparator.comparing(u -> u.getName() == null ? "" : u.getName()))
                .limit(120)
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", u.getUsername());
                    m.put("name", displayName(u));
                    m.put("initial", view.initial(u.getName(), "客"));
                    m.put("phone", u.getPhone() != null ? u.getPhone() : "");
                    // 匯入後還沒被認領的暫時帳號，標出來提醒店家
                    m.put("imported", u.getUsername() != null && u.getUsername().startsWith("imported_"));
                    return m;
                })
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("rows", rows);
        model.addAttribute("total", all.size());
        model.addAttribute("activeTab", "more");
        return "m/members";
    }

    // ── 會員資料 ────────────────────────────────────────────────────────
    @GetMapping("/members/{username}")
    public String member(@PathVariable String username, HttpServletRequest request, Model model) {
        User user = getLoginUser(request);
        if (user == null) {
            return "redirect:/auth/login";
        }
        User customer = userRepository.findByUsername(username).orElse(null);
        if (customer == null || !customer.isCustomer()) {
            return "redirect:/m/members";
        }
        var wallet = walletService.getWallet(username);
        List<ConsumptionRecordResponse> records = memberConsumptionService.buildPaidRecords(username, user.getUsername());
        List<Map<String, Object>> pets = petService.getMyPets(username).stream().map(this::petRow).toList();

        model.addAttribute("user", user);
        model.addAttribute("c", customer);
        model.addAttribute("name", displayName(customer));
        model.addAttribute("initial", view.initial(customer.getName(), "客"));
        model.addAttribute("imported", username.startsWith("imported_"));
        model.addAttribute("sourceLabel", customer.getSource() != null ? customer.getSource().getLabel() : null);
        model.addAttribute("walletBalance", wallet.getBalance() == null ? 0 : wallet.getBalance());
        model.addAttribute("walletTier", wallet.isCardActive() ? wallet.getCardTierLabel() : null);
        model.addAttribute("walletDiscount", wallet.isCardActive() && wallet.getDiscount() < 1.0
                ? view.formatDiscount(wallet.getDiscount()) : null);
        model.addAttribute("totalSpent", records.stream().mapToInt(ConsumptionRecordResponse::getAmount).sum());
        model.addAttribute("orderCount", records.size());
        model.addAttribute("records", records.stream().limit(10).map(this::recordRow).toList());
        model.addAttribute("pets", pets);
        model.addAttribute("upcoming", upcomingAppointments(username, null));
        model.addAttribute("adminNote", customer.getAdminNote() == null ? "" : customer.getAdminNote());
        model.addAttribute("activeTab", "more");
        return "m/member";
    }

    // ────────────────────────────────────────────────────────────────────

    private Map<String, Object> petRow(PetResponse p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("initial", view.initial(p.getName(), "?"));
        m.put("species", view.speciesKey(p.getPetType() != null ? p.getPetType().name() : null));
        m.put("photoUrl", p.getPhotoUrl());
        List<String> meta = new ArrayList<>();
        if (p.getBreed() != null && !p.getBreed().isBlank()) {
            meta.add(p.getBreed());
        }
        if (p.getWeight() != null) {
            meta.add(formatWeight(p.getWeight()));
        }
        if (p.getCoatType() != null && !"UNDEFINED".equals(p.getCoatType().name())) {
            meta.add(p.getCoatTypeLabel());
        }
        if (p.getCatCoatCategoryLabel() != null) {
            meta.add(p.getCatCoatCategoryLabel());
        }
        m.put("meta", String.join("，", meta));
        m.put("owner", p.getOwnerName() != null ? p.getOwnerName() : "");
        m.put("locked", p.getLockedGroomingItemId() != null);
        return m;
    }

    private Map<String, Object> noteRow(PetGroomingNote n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", n.getServiceDate() != null ? n.getServiceDate().format(DATE_FMT) : "");
        m.put("note", n.getNote());
        m.put("staff", n.getStaff() != null ? n.getStaff().getName() : null);
        m.put("photoUrl", n.getPhotoUrl());
        return m;
    }

    private Map<String, Object> recordRow(ConsumptionRecordResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", r.getTime() != null ? r.getTime().format(DATE_FMT) : "");
        m.put("source", r.getSourceLabel());
        m.put("petName", r.getPetName());
        m.put("amount", r.getAmount());
        m.put("method", r.getPaymentMethodLabel());
        m.put("items", r.getItems() == null ? "" : r.getItems().stream()
                .map(i -> i.getStaffName() != null ? i.getName() + "（" + i.getStaffName() + "）" : i.getName())
                .collect(Collectors.joining("、")));
        return m;
    }

    // 今天以後、還沒取消的預約；petName 為 null 時不篩毛孩
    private List<Map<String, Object>> upcomingAppointments(String username, String petName) {
        LocalDate today = LocalDate.now();
        List<AppointmentResponse> list = appointmentService.getMyAppointments(username);
        return list.stream()
                .filter(a -> !a.isCancelled() && a.getDate() != null && !a.getDate().isBefore(today))
                .filter(a -> petName == null || petName.equals(a.getPetName()))
                .sorted(Comparator.comparing(AppointmentResponse::getDate)
                        .thenComparing(a -> a.getStartTime() == null ? java.time.LocalTime.MIN : a.getStartTime()))
                .limit(5)
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("when", a.getDate().format(SHORT_DATE_FMT)
                            + (a.getStartTime() != null ? " " + a.getStartTime().format(TIME_FMT) : ""));
                    m.put("petName", a.getPetName());
                    m.put("status", a.getStatusLabel());
                    return m;
                })
                .toList();
    }

    private List<String> splitTags(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String t : s.split(",")) {
            if (!t.isBlank()) {
                out.add(t.trim());
            }
        }
        return out;
    }

    private String formatWeight(double w) {
        return (w % 1 == 0 ? String.valueOf((long) w) : String.valueOf(w)) + " kg";
    }

    private String displayName(User u) {
        return u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName();
    }
}
