package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.CoatType;
import com.petgrooming.pet_system.enums.PetSizeCategory;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService; // 需求 17：寵物照片上傳
    private final com.petgrooming.pet_system.repository.PetGroomingNoteRepository petGroomingNoteRepository; // 需求 18

    // ── 1. 新增寵物 ───────────────────────────────────────────────────────
    // 改用 X-Username 識別飼主，與 AppointmentService.book() 相同做法
    public PetResponse addPet(String username, PetRequest req) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者：" + username));

        // 自動依 petType + weight 判斷體型分類
        PetSizeCategory sizeCategory = PetSizeCategory.determine(req.getPetType(), req.getWeight());

        Pet pet = Pet.builder()
                .name(req.getName())
                .petType(req.getPetType())
                .breed(req.getBreed())
                .weight(req.getWeight())
                .age(req.getAge())
                .sizeCategory(sizeCategory)
                // 需求 2：毛長不由顧客決定，新增時固定為 UNDEFINED，之後由店家後台設定
                .coatType(CoatType.UNDEFINED)
                .hasSeparationAnxiety(req.getHasSeparationAnxiety() != null && req.getHasSeparationAnxiety())
                .ownerPhone(req.getOwnerPhone())
                .notes(req.getNotes())
                .owner(user)
                // 需求 19：定型化契約要求蒐集的資料（皆選填）
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

    // ── 2. 查詢自己的所有寵物 ────────────────────────────────────────────
    // 改用 username 查詢，與 AppointmentService.getMyAppointments() 相同做法
    public List<PetResponse> getMyPets(String username) {

        // 確認 user 存在，避免靜默回傳空清單讓呼叫端誤以為「此人只是沒有寵物」
        userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者：" + username));

        return petRepository.findByOwnerUsername(username)
                .stream()
                .map(PetResponse::from)
                .toList();
    }

    // ── 3. 店家定義寵物毛長（需求 2）────────────────────────────────────────
    // 僅供後台（STAFF / ADMIN）呼叫，由店家實際檢視毛況後設定短毛 / 中長毛 / 長毛。
    @Transactional
    public PetResponse setCoatType(Long petId, CoatType coatType) {
        if (coatType == null) {
            throw new IllegalArgumentException("毛長不能為空");
        }
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("找不到寵物 #" + petId));
        pet.setCoatType(coatType);
        return PetResponse.from(petRepository.save(pet));
    }

    // ── 4. 上傳/更換寵物照片（需求 17：LIFF 顧客端 + 店家後台皆可用）───────
    // 權限（會員只能傳自己的寵物、店家可傳任何寵物）由呼叫端（Controller）檢查，
    // 這裡只負責「換照片」這件事本身：先上傳新圖，成功後才刪舊圖並更新資料庫，
    // 順序不能反過來——否則上傳失敗時會先把舊照片刪掉，變成沒有照片可以用。
    @Transactional
    public PetResponse updatePhoto(Long petId, org.springframework.web.multipart.MultipartFile file) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("找不到寵物 #" + petId));

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
                .orElseThrow(() -> new IllegalArgumentException("找不到寵物 #" + petId));
    }

    // ── 5. 上傳美容狀況歷史照片（需求 18：美容歷史相簿）─────────────────
    // 僅供店家後台使用（美容狀況備注本身就是店員在核對步驟填寫的，照片同樣由店員事後補上）。
    @Transactional
    public void uploadGroomingNotePhoto(Long noteId, org.springframework.web.multipart.MultipartFile file) {
        var note = petGroomingNoteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("找不到美容狀況紀錄 #" + noteId));

        CloudinaryService.UploadResult result = cloudinaryService.upload(file, "grooming-notes");

        String oldPublicId = note.getPhotoPublicId();
        note.setPhotoUrl(result.url());
        note.setPhotoPublicId(result.publicId());
        petGroomingNoteRepository.save(note);

        cloudinaryService.deleteQuietly(oldPublicId);
    }
}
