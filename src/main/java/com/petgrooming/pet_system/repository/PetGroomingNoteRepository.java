package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.PetGroomingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PetGroomingNoteRepository extends JpaRepository<PetGroomingNote, Long> {

    // 查詢某隻寵物的所有美容備註歷史，最新的排前面，供店家查詢客製化美容參考
    List<PetGroomingNote> findByPetIdOrderByServiceDateDescCreatedAtDesc(Long petId);

    List<PetGroomingNote> findByAppointmentId(Long appointmentId);
}
