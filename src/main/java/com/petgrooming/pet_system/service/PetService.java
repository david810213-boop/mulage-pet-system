package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.PetSearchResult;
import com.petgrooming.pet_system.enums.CatCoatCategory;
import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.PetSizeCategory;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PetException;
import com.petgrooming.pet_system.model.CatBreedCoatMapping;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.PetGroomingNote;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.CatBreedCoatMappingRepository;
import com.petgrooming.pet_system.repository.GroomingItemRepository;
import com.petgrooming.pet_system.repository.PetGroomingNoteRepository;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService; // 寵物照片上傳
    private final PetGroomingNoteRepository petGroomingNoteRepository;
    private final PetConsumptionHistoryService petConsumptionHistoryService; // 僅限既有客戶項目判斷
    private final CatBreedCoatMappingRepository catBreedCoatMappingRepository; // 菜單簡化
    private final GroomingItemRepository groomingItemRepository; // 狗狗鎖定套餐查詢
    private final GroomingNotePhotoService groomingNotePhotoService; // 美容紀錄照片

    // ── 1. 新增寵物 ───────────────────────────────────────────────────────
    // 改用 X-Username 識別飼主，與 AppointmentService.book() 相同做法
    public PetResponse addPet(String username, PetRequest req) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到該使用者：" + username));

        // 自動依 petType + weight 判斷體型分類
        PetSizeCategory sizeCategory = PetSizeCategory.determine(req.getPetType(), req.getWeight());

        // 菜單簡化——貓咪依品種自動判斷毛髮分類（單層毛/雙層毛/長毛），
        // LIFF 預約頁靠這個欄位篩選菜單。狗/其他物種、或品種不在對照表裡的特殊貓種，
        // 一律是 null（LIFF 端遇到 null 顯示全部貓咪套餐項目，不擋顧客預約）。
        CatCoatCategory catCoatCategory = resolveCatCoatCategory(req.getPetType(), req.getBreed());

        Pet pet = Pet.builder()
                .name(req.getName())
                .petType(req.getPetType())
                .breed(req.getBreed())
                .weight(req.getWeight())
                .age(req.getAge())
                .sizeCategory(sizeCategory)
                // 毛長不由顧客決定，新增時固定為 UNDEFINED，之後由店家後台設定
                .coatType(CoatType.UNDEFINED)
                .catCoatCategory(catCoatCategory)
                .hasSeparationAnxiety(req.getHasSeparationAnxiety() != null && req.getHasSeparationAnxiety())
                .ownerPhone(req.getOwnerPhone())
                .notes(req.getNotes())
                .owner(user)
                // 定型化契約要求蒐集的資料（皆選填）
                .gender(req.getGender())
                .isNeutered(req.getIsNeutered() != null && req.getIsNeutered())
                .hasChip(req.getHasChip() != null && req.getHasChip())
                .chipNumber(req.getChipNumber())
                .personalityTags(req.getPersonalityTags())
                .healthHistory(req.getHealthHistory())
                .healthHistoryOther(req.getHealthHistoryOther())
                .hasDesignatedVet(req.getHasDesignatedVet() != null && req.getHasDesignatedVet())
                .designatedVetName(req.getDesignatedVetName())
                .designatedVetAddress(req.getDesignatedVetAddress())
                .designatedVetPhone(req.getDesignatedVetPhone())
                .build();

        Pet saved = petRepository.save(pet);
        return PetResponse.from(saved);
    }

    // LIFF 新增毛孩頁面的品種下拉選單資料來源
    public List<CatBreedCoatMapping> listCatBreedOptions() {
        return catBreedCoatMappingRepository.findAllByOrderBySortOrderAscBreedNameAsc();
    }

    // 貓咪依品種查對照表算出毛髮分類，新增/編輯/批次匯入共用同一套邏輯，
    // 避免多處各自實作導致行為不一致。
    public CatCoatCategory resolveCatCoatCategory(
            PetType petType, String breed) {
        if (petType != PetType.CAT || breed == null) {
            return null;
        }
        return catBreedCoatMappingRepository.findByBreedName(breed.trim())
                .map(CatBreedCoatMapping::getCoatCategory)
                .orElse(null);
    }

    // ── 1之1. 編輯寵物資料（新增）─────────────────────────────────────────
    // 兩種使用情境共用：
    //   1. 顧客自己在 LIFF「我的寵物」編輯（呼叫端負責檢查這隻寵物是不是自己的）
    //   2. 店家後台代改（會員信息頁面的寵物分頁）
    // 物種（petType）刻意不開放修改——換物種牽動體型分類、適用項目判斷等一連串
    // 邏輯，貿然允許中途改物種容易產生不一致的歷史資料，寫錯的話請改用刪除重建
    // （目前系統還沒有刪除寵物的功能，這點先記錄，之後如果店家真的有需求再處理）。
    // 品種有改的話，比照新增時的邏輯重新查一次毛髮分類（貓咪才有意義，狗維持 null）。
    @Transactional
    public PetResponse updatePet(Long petId, PetRequest req) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));

        PetSizeCategory sizeCategory = PetSizeCategory.determine(pet.getPetType(), req.getWeight());
        CatCoatCategory catCoatCategory =
                resolveCatCoatCategory(pet.getPetType(), req.getBreed());

        pet.setName(req.getName());
        pet.setBreed(req.getBreed());
        pet.setWeight(req.getWeight());
        pet.setAge(req.getAge());
        pet.setSizeCategory(sizeCategory);
        pet.setCatCoatCategory(catCoatCategory);
        pet.setHasSeparationAnxiety(req.getHasSeparationAnxiety() != null && req.getHasSeparationAnxiety());
        pet.setOwnerPhone(req.getOwnerPhone());
        pet.setNotes(req.getNotes());
        if (req.getGender() != null) pet.setGender(req.getGender());
        if (req.getIsNeutered() != null) pet.setIsNeutered(req.getIsNeutered());
        if (req.getHasChip() != null) pet.setHasChip(req.getHasChip());
        if (req.getChipNumber() != null) pet.setChipNumber(req.getChipNumber());
        if (req.getPersonalityTags() != null) pet.setPersonalityTags(req.getPersonalityTags());
        if (req.getHealthHistory() != null) pet.setHealthHistory(req.getHealthHistory());
        if (req.getHealthHistoryOther() != null) pet.setHealthHistoryOther(req.getHealthHistoryOther());
        if (req.getHasDesignatedVet() != null) pet.setHasDesignatedVet(req.getHasDesignatedVet());
        if (req.getDesignatedVetName() != null) pet.setDesignatedVetName(req.getDesignatedVetName());
        if (req.getDesignatedVetAddress() != null) pet.setDesignatedVetAddress(req.getDesignatedVetAddress());
        if (req.getDesignatedVetPhone() != null) pet.setDesignatedVetPhone(req.getDesignatedVetPhone());

        return PetResponse.from(petRepository.save(pet));
    }

    // LIFF 顧客端編輯自己的寵物前，先確認這隻寵物真的是這個 username 的，
    // 避免會員竄改 API 請求裡的 petId 改到別人的寵物資料。
    public void assertOwnership(Long petId, String username) {
        Pet pet = getPetEntity(petId);
        if (pet.getOwner() == null || !pet.getOwner().getUsername().equals(username)) {
            throw new PetException("這隻寵物不屬於這個帳號，無法編輯");
        }
    }

    // ── 刪除寵物：軟刪除 ─────────────────────────────
    // 客戶端（LIFF）跟後台都會呼叫。跟 GroomingItem 下架同一套邏輯——只標記 isDeleted=true，
    // 過往預約/消費紀錄存的是寵物名字快照，不受影響，所以任何情況都能刪。
    @Transactional
    public void deletePet(Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));
        pet.setDeleted(true);
        petRepository.save(pet);
    }

    // ── 2. 查詢自己的所有寵物 ────────────────────────────────────────────
    // 改用 username 查詢，與 AppointmentQueryService.getMyAppointments() 相同做法
    public List<PetResponse> getMyPets(String username) {

        // 確認 user 存在，避免靜默回傳空清單讓呼叫端誤以為「此人只是沒有寵物」
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到該使用者：" + username));

        return petRepository.findByOwnerUsername(username)
                .stream()
                .filter(pet -> !pet.isDeleted()) // 軟刪除的寵物不出現在清單裡
                .map(pet -> {
                    // 已鎖定固定套餐的狗，順便查出項目名稱/價格一起帶回去，
                    // 前端才不用為了顯示鎖定資訊再多打一次 API。
                    GroomingItem lockedItem = pet.getLockedGroomingItemId() != null
                            ? groomingItemRepository.findById(pet.getLockedGroomingItemId()).orElse(null)
                            : null;
                    PetResponse res = PetResponse.from(pet, lockedItem);
                    // 帶入「是不是既有客戶」，供預約表單過濾「僅限既有客戶」項目用
                    res.setIsExistingCustomer(
                            petConsumptionHistoryService.hasPriorPaidService(owner.getId(), pet.getName(), null));
                    return res;
                })
                .toList();
    }

    // ── 3. 店家定義寵物毛長────────────────────────────────────────
    // 僅供後台（STAFF / ADMIN）呼叫，由店家實際檢視毛況後設定短毛 / 中長毛 / 長毛。
    @Transactional
    public PetResponse setCoatType(Long petId, CoatType coatType) {
        if (coatType == null) {
            throw new PetException("毛長不能為空");
        }
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));
        pet.setCoatType(coatType);
        return PetResponse.from(petRepository.save(pet));
    }

    // ── 寵物信息管理頁面 ──────────────────────────
    // 後台集中管理所有寵物（跨會員）與固定套餐鎖定狀態，不用先找到會員、點進去才看得到。
    public List<PetResponse> listAllPets() {
        return petRepository.findByIsDeletedFalseOrderByNameAsc().stream()
                .map(pet -> {
                    var lockedItem = pet.getLockedGroomingItemId() != null
                            ? groomingItemRepository.findById(pet.getLockedGroomingItemId()).orElse(null)
                            : null;
                    return PetResponse.from(pet, lockedItem);
                })
                .toList();
    }

    // ── 3之2. 狗狗定價流程簡化：鎖定/解鎖固定套餐（新增）───────────────────
    // 用途：成犬結帳核對時，店員選出真正對應的套餐項目後，呼叫這個方法把
    // 這個項目「鎖」在寵物資料上，之後不用再重複選單。
    @Transactional
    public PetResponse lockGroomingItem(Long petId, Long groomingItemId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));
        var item = groomingItemRepository.findById(groomingItemId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到服務項目 #" + groomingItemId));
        pet.setLockedGroomingItemId(groomingItemId);
        petRepository.save(pet);
        return PetResponse.from(pet, item);
    }

    // 用途：狗狗生病消瘦、換季毛況差很多、或當初選錯了，店員手動解鎖，
    // 恢復成「依體重自動篩選」的狀態。
    @Transactional
    public PetResponse unlockGroomingItem(Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));
        pet.setLockedGroomingItemId(null);
        petRepository.save(pet);
        return PetResponse.from(pet);
    }

    // ── 4. 更新寵物體重（新增）──────────────────────────────────────────
    // 用途：結帳完成後提醒店員更新的體重，這個欄位是「依體重自動篩選」邏輯的
    // 依據，幼犬每次結帳都應該更新，成犬鎖定固定套餐之後就不需要再更新了。
    @Transactional
    public PetResponse updateWeight(Long petId, double weight) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));
        pet.setWeight(weight);
        // 體重變了，體型分類（PetSizeCategory，影響貓咪 90 天回洗優惠等其他既有
        // 邏輯）也要跟著重新算一次，維持資料一致。
        pet.setSizeCategory(PetSizeCategory.determine(pet.getPetType(), weight));
        petRepository.save(pet);
        var lockedItem = pet.getLockedGroomingItemId() != null
                ? groomingItemRepository.findById(pet.getLockedGroomingItemId()).orElse(null)
                : null;
        return PetResponse.from(pet, lockedItem);
    }


    // 用途：自動判斷抓不到品種（品種不在對照表裡，變成 SPECIAL）時，店家實際
    // 看過這隻貓之後，可以在後台手動指定正確分類，不用透過改品種名稱間接觸發。
    // 只給貓咪用——狗狗沒有這個分類概念，呼叫端（Controller）應先確認物種是貓
    // 再呼叫這個方法，這裡不重複檢查（避免二次查資料庫），只單純防呆 category 不能空。
    @Transactional
    public PetResponse setCatCoatCategory(Long petId, CatCoatCategory category) {
        if (category == null) {
            throw new PetException("毛髮分類不能為空");
        }
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));
        pet.setCatCoatCategory(category);
        return PetResponse.from(petRepository.save(pet));
    }

    // ── 4. 上傳/更換寵物照片（LIFF 顧客端 + 店家後台皆可用）───────
    // 權限（會員只能傳自己的寵物、店家可傳任何寵物）由呼叫端（Controller）檢查，
    // 這裡只負責「換照片」這件事本身：先上傳新圖，成功後才刪舊圖並更新資料庫，
    // 順序不能反過來——否則上傳失敗時會先把舊照片刪掉，變成沒有照片可以用。
    @Transactional
    public PetResponse updatePhoto(Long petId, MultipartFile file) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));

        CloudinaryService.UploadResult result = cloudinaryService.upload(file, "pets");

        String oldPublicId = pet.getPhotoPublicId();
        pet.setPhotoUrl(result.url());
        pet.setPhotoPublicId(result.publicId());
        Pet saved = petRepository.save(pet);

        cloudinaryService.deleteQuietly(oldPublicId);

        return PetResponse.from(saved);
    }

    // 供 Controller 檢查「這隻寵物是不是這個 username 的」，避免會員上傳到別人的寵物
    public Pet getPetEntity(Long petId) {
        return petRepository.findById(petId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到寵物 #" + petId));
    }

    // ── 5. 上傳美容狀況歷史照片（美容歷史相簿）─────────────────
    // 僅供店家後台使用（美容狀況備注本身就是店員在核對步驟填寫的，照片同樣由店員事後補上）。
    @Transactional
    public void uploadGroomingNotePhoto(Long noteId, MultipartFile file) {
        var note = petGroomingNoteRepository.findById(noteId)
                .orElseThrow(() -> new PetException(ErrorCode.NOT_FOUND, "找不到美容狀況紀錄 #" + noteId));

        CloudinaryService.UploadResult result = cloudinaryService.upload(file, "grooming-notes");

        String oldPublicId = note.getPhotoPublicId();
        note.setPhotoUrl(result.url());
        note.setPhotoPublicId(result.publicId());
        petGroomingNoteRepository.save(note);

        cloudinaryService.deleteQuietly(oldPublicId);
    }

    // ── 6. 顧客查看自己毛孩的美容紀錄（LIFF「我的毛孩」）──────
    // 備註內容與照片全部開放給顧客看（2026-09-29 店家決定）。呼叫端要先做擁有者檢查。
    public List<Map<String, Object>> getGroomingHistory(Long petId) {
        var notes = petGroomingNoteRepository.findByPetIdOrderByServiceDateDescCreatedAtDesc(petId);
        var photosByNote = groomingNotePhotoService.allPhotoUrlsByNoteId(notes);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var n : notes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("serviceDate", n.getServiceDate() != null ? n.getServiceDate().toString() : null);
            m.put("note", n.getNote());
            m.put("photos", photosByNote.getOrDefault(n.getId(), List.of()));
            result.add(m);
        }
        return result;
    }

    public Optional<Pet> findPet(Long petId) {
        return petRepository.findById(petId);
    }

    public List<Pet> findAllByType(PetType petType) {
        return petRepository.findByPetType(petType);
    }

    public List<Pet> searchByName(String keyword) {
        return petRepository.findByNameContainingIgnoreCase(keyword);
    }

    /** 依寵物名稱搜尋（店員用），同名寵物以家長姓名與電話末四碼區分，最多 20 筆。 */
    @Transactional(readOnly = true)
    public List<PetSearchResult> searchForStaff(String keyword) {
        return searchByName(keyword.trim()).stream()
                .filter(p -> p.getOwner() != null)
                .limit(20)
                .map(PetService::toSearchResult)
                .toList();
    }

    private static PetSearchResult toSearchResult(Pet p) {
        String phone = p.getOwnerPhone() != null && p.getOwnerPhone().length() >= 4
                ? p.getOwnerPhone().substring(p.getOwnerPhone().length() - 4)
                : "未填";
        return PetSearchResult.builder()
                .petId(p.getId())
                .petName(p.getName())
                .petType(p.getPetType() != null ? p.getPetType().name() : null)
                .breed(p.getBreed())
                .ownerUsername(p.getOwner().getUsername())
                .ownerName(p.getOwner().getName())
                .ownerPhone(p.getOwnerPhone())
                .photoUrl(p.getPhotoUrl())
                .displayLabel(p.getName() + "（家長：" + p.getOwner().getName() + "・電話末四碼 " + phone + "）")
                .build();
    }

    /** 寵物的美容紀錄，新的在前。 */
    public List<PetGroomingNote> getGroomingNotes(Long petId) {
        return petGroomingNoteRepository.findByPetIdOrderByServiceDateDescCreatedAtDesc(petId);
    }
}
