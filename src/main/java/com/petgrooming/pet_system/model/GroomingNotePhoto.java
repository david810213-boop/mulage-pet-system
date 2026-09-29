package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 美容狀況紀錄的照片（需求，2026-09-29）：核對時可以拍照／上傳，一筆紀錄最多 5 張。
 *
 * 跟 PetGroomingNote 原本的 photoUrl（事後在會員信息頁補的單張照片）分開存，
 * 兩者並存：顯示時先列這裡的照片，再接舊的那一張。
 */
@Entity
@Table(name = "grooming_note_photos", indexes = @Index(name = "idx_gnp_note", columnList = "note_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroomingNotePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    @ToString.Exclude
    private PetGroomingNote note;

    @Column(name = "photo_url", length = 500, nullable = false)
    private String photoUrl;

    @Column(name = "photo_public_id", length = 200)
    private String photoPublicId;

    @Column(name = "sort_order", nullable = false, columnDefinition = "int default 0")
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
