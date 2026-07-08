package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.PetRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.PetSizeCategory;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final UserRepository userRepository;

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
                .coatType(req.getCoatType())
                .hasSeparationAnxiety(req.getHasSeparationAnxiety() != null && req.getHasSeparationAnxiety())
                .ownerPhone(req.getOwnerPhone())
                .notes(req.getNotes())
                .owner(user)
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
}
