package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.PetResponse.LockedItemView;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PetException;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.PetLockedItem;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.PetLockedItemRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 狗狗的固定組合（客製化鎖定）：一項精緻洗套餐＋任意加購，價格固定在鎖定當時。
 * 預約與開單時整組自動帶入、照固定價計算；顧客線上不能移除，但可以另外加購。
 * Pet.lockedGroomingItemId 仍指向組合裡的精緻洗，菜單篩選沿用原本的鎖定邏輯。
 * 見 docs/adr/0012-staff-part-time-and-custom-lock.md
 */
@Service
@RequiredArgsConstructor
public class PetComboLockService {

    private final PetRepository petRepository;
    private final PetLockedItemRepository petLockedItemRepository;
    private final GroomingItemRepository groomingItemRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentItemRepository appointmentItemRepository;

    /** 組合裡的一項：服務項目與固定價。 */
    public record ComboLine(Long groomingItemId, int price) {
    }

    // ── 鎖定／解鎖 ─────────────────────────────────────────────

    /** 只鎖定一項精緻洗（舊的單項鎖定按鈕），價格固定為目前價格。 */
    @Transactional
    public PetResponse lockSingle(Long petId, Long groomingItemId) {
        GroomingItem item = groomingItemRepository.findById(groomingItemId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到服務項目 #" + groomingItemId));
        return lockCombo(petId, List.of(new ComboLine(item.getId(), (int) Math.round(item.getPrice()))));
    }

    /** 把這張現場單的服務項目（照這張單的價格）鎖定成固定組合。 */
    @Transactional
    public PetResponse lockFromWalkInOrder(Long petId, Long orderId) {
        WalkInOrder order = walkInOrderRepository.findById(orderId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到現場單 #" + orderId));
        List<ComboLine> lines = linesFrom(order.getItems().stream()
                .filter(i -> i.getGroomingItemId() != null && i.getRetailProductId() == null)
                .map(i -> new ComboLine(i.getGroomingItemId(), i.getPrice()))
                .toList());
        return lockCombo(petId, lines);
    }

    /** 把這筆預約的服務項目鎖定成固定組合：已到店開單照開單價格，還沒開單照預約時選的項目。 */
    @Transactional
    public PetResponse lockFromAppointment(Long petId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到預約 #" + appointmentId));
        List<AppointmentItem> checkinItems = appointmentItemRepository.findByAppointmentId(appointmentId);
        List<ComboLine> raw;
        if (!checkinItems.isEmpty()) {
            raw = checkinItems.stream()
                    .filter(i -> i.getGroomingItemId() != null && i.getRetailProductId() == null)
                    .map(i -> new ComboLine(i.getGroomingItemId(), i.getPrice()))
                    .toList();
        } else {
            Pet pet = getPet(petId);
            Map<Long, Integer> locked = lockedPrices(pet);
            raw = appointment.getSelectedItems() == null ? List.of() : appointment.getSelectedItems().stream()
                    .map(i -> new ComboLine(i.getId(), priceFor(locked, i)))
                    .toList();
        }
        return lockCombo(petId, linesFrom(raw));
    }

    /** 套餐展開的副組成是 $0 的明細列，同一個服務項目取最高價那列（主項目）。 */
    private static List<ComboLine> linesFrom(List<ComboLine> raw) {
        Map<Long, Integer> byItem = new LinkedHashMap<>();
        raw.forEach(l -> byItem.merge(l.groomingItemId(), l.price(), Math::max));
        return byItem.entrySet().stream().map(e -> new ComboLine(e.getKey(), e.getValue())).toList();
    }

    @Transactional
    public PetResponse lockCombo(Long petId, List<ComboLine> lines) {
        Pet pet = getPet(petId);
        if (lines.isEmpty()) {
            throw new PetException("沒有可以鎖定的服務項目");
        }
        Map<Long, GroomingItem> items = groomingItemRepository.findAllById(
                        lines.stream().map(ComboLine::groomingItemId).distinct().toList())
                .stream().collect(Collectors.toMap(GroomingItem::getId, Function.identity()));
        List<GroomingItem> packages = new ArrayList<>();
        for (ComboLine line : lines) {
            GroomingItem item = items.get(line.groomingItemId());
            if (item == null || item.isDeleted()) {
                throw new PetException("服務項目 #" + line.groomingItemId() + " 已下架，不能鎖定");
            }
            if (line.price() < 0) {
                throw new PetException("固定價不能是負數");
            }
            if (isTierPackage(item)) {
                packages.add(item);
            }
        }
        if (packages.size() != 1) {
            throw new PetException(packages.isEmpty()
                    ? "固定組合要包含一項精緻洗套餐"
                    : "固定組合只能有一項精緻洗套餐");
        }
        petLockedItemRepository.deleteByPetId(petId);
        petLockedItemRepository.saveAll(lines.stream()
                .map(l -> PetLockedItem.builder().petId(petId).groomingItemId(l.groomingItemId())
                        .lockedPrice(l.price()).build())
                .toList());
        pet.setLockedGroomingItemId(packages.get(0).getId());
        petRepository.save(pet);
        return toResponse(pet);
    }

    @Transactional
    public PetResponse unlock(Long petId) {
        Pet pet = getPet(petId);
        petLockedItemRepository.deleteByPetId(petId);
        pet.setLockedGroomingItemId(null);
        petRepository.save(pet);
        return PetResponse.from(pet);
    }

    // ── 查詢 ─────────────────────────────────────────────

    /** 寵物回應附上固定組合。 */
    @Transactional(readOnly = true)
    public PetResponse toResponse(Pet pet) {
        return toResponses(List.of(pet)).get(0);
    }

    /** 一次查好多隻寵物的固定組合，避免逐隻查詢。 */
    @Transactional(readOnly = true)
    public List<PetResponse> toResponses(List<Pet> pets) {
        Map<Long, List<LockedItemView>> combos = combosFor(pets);
        return pets.stream().map(pet -> {
            List<LockedItemView> combo = combos.getOrDefault(pet.getId(), List.of());
            PetResponse res = PetResponse.from(pet, null);
            res.applyLockedCombo(combo);
            return res;
        }).toList();
    }

    /** 這隻狗目前有效的固定組合項目（精緻洗已下架時整組不算數，回到依體重篩選）。 */
    @Transactional(readOnly = true)
    public List<GroomingItem> activeComboItems(Pet pet) {
        if (pet == null || pet.getLockedGroomingItemId() == null) {
            return List.of();
        }
        List<PetLockedItem> rows = petLockedItemRepository.findByPetIdOrderByIdAsc(pet.getId());
        Map<Long, GroomingItem> items = itemsById(rows.stream().map(PetLockedItem::getGroomingItemId).toList());
        GroomingItem main = items.get(pet.getLockedGroomingItemId());
        if (main == null || main.isDeleted()) {
            return List.of();
        }
        return rows.stream().map(r -> items.get(r.getGroomingItemId()))
                .filter(Objects::nonNull).filter(i -> !i.isDeleted()).toList();
    }

    /** 這隻狗固定組合的固定價（服務項目 id → 價格）；沒有鎖定回空 Map。 */
    @Transactional(readOnly = true)
    public Map<Long, Integer> lockedPrices(Pet pet) {
        if (pet == null || pet.getLockedGroomingItemId() == null) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> prices = new HashMap<>();
        petLockedItemRepository.findByPetIdOrderByIdAsc(pet.getId())
                .forEach(r -> prices.put(r.getGroomingItemId(), r.getLockedPrice()));
        return prices;
    }

    /** 會員名下這個名字的寵物（預約、現場單只存寵物名字快照）。 */
    @Transactional(readOnly = true)
    public Optional<Pet> findPet(User owner, String petName) {
        if (owner == null || petName == null) {
            return Optional.empty();
        }
        return petRepository.findByOwnerUsernameAndName(owner.getUsername(), petName);
    }

    /** 固定組合的固定價；不在組合裡就是目前價格。 */
    public static int priceFor(Map<Long, Integer> lockedPrices, GroomingItem item) {
        Integer locked = lockedPrices.get(item.getId());
        return locked != null ? locked : (int) Math.round(item.getPrice());
    }

    /** 固定價跟目前價格不同時，品項名稱標註，方便對帳。 */
    public static String labelIfLocked(Map<Long, Integer> lockedPrices, GroomingItem item, String name) {
        Integer locked = lockedPrices.get(item.getId());
        return locked != null && locked != (int) Math.round(item.getPrice()) ? name + "（固定組合價）" : name;
    }

    /** 精緻洗這類依體型篩選、可以當固定組合主項目的套餐（不是依體型的加購）。 */
    public static boolean isTierPackage(GroomingItem item) {
        return item.getDogWeightTier() != null && !item.isTierAddon();
    }

    private Map<Long, List<LockedItemView>> combosFor(List<Pet> pets) {
        List<Long> lockedPetIds = pets.stream().filter(p -> p.getLockedGroomingItemId() != null)
                .map(Pet::getId).toList();
        if (lockedPetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PetLockedItem> rows = petLockedItemRepository.findByPetIdInOrderByIdAsc(lockedPetIds);
        Map<Long, GroomingItem> items = itemsById(rows.stream().map(PetLockedItem::getGroomingItemId).toList());
        Map<Long, List<LockedItemView>> result = new HashMap<>();
        for (PetLockedItem row : rows) {
            GroomingItem item = items.get(row.getGroomingItemId());
            if (item == null || item.isDeleted()) {
                continue;
            }
            result.computeIfAbsent(row.getPetId(), k -> new ArrayList<>())
                    .add(new LockedItemView(item.getId(), item.getItemCode(), item.getName(), row.getLockedPrice()));
        }
        // 精緻洗已下架的組合不算數
        Map<Long, Long> mainByPet = pets.stream().filter(p -> p.getLockedGroomingItemId() != null)
                .collect(Collectors.toMap(Pet::getId, Pet::getLockedGroomingItemId, (a, b) -> a));
        result.entrySet().removeIf(e -> e.getValue().stream()
                .noneMatch(v -> v.groomingItemId().equals(mainByPet.get(e.getKey()))));
        return result;
    }

    private Map<Long, GroomingItem> itemsById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return groomingItemRepository.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(GroomingItem::getId, Function.identity()));
    }

    private Pet getPet(Long petId) {
        return petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));
    }
}
