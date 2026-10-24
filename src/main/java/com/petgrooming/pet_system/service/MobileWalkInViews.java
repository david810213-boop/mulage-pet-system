package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.WalkInOrderResponse;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * 員工手機版現場單畫面的顯示資料：列表與明細頁用的精簡資料列、流程階段、開單時搜尋客人、建議套餐。
 */
@Component
@RequiredArgsConstructor
public class MobileWalkInViews {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final MobileViewHelper view;
    private final UserService userService;
    private final PetService petService;

    // 流程階段，判斷條件跟網頁版現場單列表的按鈕顯示一致
    public String stageKey(WalkInOrderResponse o) {
        if (o.isPaid()) return "done";
        if (o.isPendingWireTransfer()) return "wire";
        if (!o.isRequiresServiceFlow()) return "pay";
        if (!o.isServiceEndedDone()) return "serving";
        if (!o.isFinalCheckDone()) return "check";
        return "pay";
    }

    public String stageLabel(String stage) {
        return switch (stage) {
            case "done" -> "已結帳";
            case "wire" -> "待對帳";
            case "serving" -> "服務中";
            case "check" -> "待核對";
            default -> "待結帳";
        };
    }

    // 列表、明細頁用的精簡資料（只放畫面需要的欄位）
    public Map<String, Object> toRow(WalkInOrderResponse o) {
        String title = o.getPetName() != null && !o.getPetName().isBlank() ? o.getPetName()
                : o.getMemberName() != null ? o.getMemberName() : "非會員";
        String stage = stageKey(o);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("title", title);
        m.put("initial", view.initial(title, "客"));
        m.put("sub", (o.getMemberName() != null ? o.getMemberName() : "非會員")
                + "，" + o.getItems().stream().map(WalkInOrderResponse.ItemLine::getItemName)
                        .collect(Collectors.joining("、")));
        m.put("time", o.getCreatedAt() != null ? o.getCreatedAt().format(TIME_FMT) : "");
        m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().format(DATE_TIME_FMT) : "");
        m.put("amount", o.getChargedAmount() != null ? o.getChargedAmount() : o.getTotalAmount());
        m.put("stage", stage);
        m.put("stageLabel", stageLabel(stage));
        m.put("pendingOperator", o.getItems().stream().anyMatch(i -> !i.isRetailItem() && !i.isOperatorFilled()));
        return m;
    }

    // 搜尋客人：姓名／帳號、電話、毛孩名字，結果依會員分組並帶出名下毛孩
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchCustomers(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String kw = q.trim();
        Map<String, User> found = new LinkedHashMap<>();
        userService.searchCustomers(kw).forEach(u -> found.putIfAbsent(u.getUsername(), u));

        String digits = kw.replaceAll("[^0-9]", "");
        if (digits.length() >= 3) {
            userService.getAllCustomers().stream()
                    .filter(u -> u.getPhone() != null && u.getPhone().replaceAll("[^0-9]", "").contains(digits))
                    .limit(20)
                    .forEach(u -> found.putIfAbsent(u.getUsername(), u));
        }
        for (Pet p : petService.searchByName(kw)) {
            if (p.getOwner() != null && !p.isDeleted()) {
                found.putIfAbsent(p.getOwner().getUsername(), p.getOwner());
            }
            if (found.size() >= 20) {
                break;
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (User u : found.values()) {
            if (results.size() >= 15) {
                break;
            }
            List<Map<String, Object>> pets = new ArrayList<>();
            try {
                for (PetResponse p : petService.getMyPets(u.getUsername())) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("name", p.getName());
                    pm.put("species", view.speciesKey(p.getPetType() != null ? p.getPetType().name() : null));
                    pm.put("initial", view.initial(p.getName(), "?"));
                    pm.put("meta", p.getBreed() != null ? p.getBreed() : "");
                    pets.add(pm);
                }
            } catch (IllegalArgumentException ignore) {
                // 查不到寵物就只顯示會員本人
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("username", u.getUsername());
            r.put("name", u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName());
            r.put("initial", view.initial(u.getName(), "客"));
            String phone = u.getPhone();
            r.put("phoneTail", phone != null && phone.length() >= 4 ? "末四碼 " + phone.substring(phone.length() - 4) : "");
            r.put("pets", pets);
            results.add(r);
        }
        return results;
    }

    // 建議套餐：只處理狗。已鎖定就是鎖定那項；否則從篩選後的菜單裡抓第一個
    // 有體重級距的項目（跟網頁版 suggestItemCodeForPet() 同一套規則）
    public String suggestItemCode(PetResponse pet, List<GroomingItemResponse> all, List<GroomingItemResponse> menu) {
        if (pet == null || pet.getPetType() == null || !"DOG".equalsIgnoreCase(pet.getPetType().name())) {
            return null;
        }
        if (pet.getLockedGroomingItemId() != null) {
            return all.stream().filter(i -> i.getId().equals(pet.getLockedGroomingItemId()))
                    .map(GroomingItemResponse::getItemCode).findFirst().orElse(null);
        }
        if (pet.getWeight() == null) {
            return null;
        }
        return menu.stream().filter(i -> i.getDogWeightTier() != null)
                .map(GroomingItemResponse::getItemCode).findFirst().orElse(null);
    }
}
