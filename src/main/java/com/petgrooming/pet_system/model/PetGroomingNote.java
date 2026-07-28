package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 毛孩美容狀況紀錄（每次到店核對後留下的備註歷史）
 * 用途：店家後續查詢某隻寵物過去的毛況/皮膚狀況/特殊注意事項，
 * 以便針對該毛孩進行客製化美容。與 Pet.notes（單一注意事項欄位）不同，
 * 這裡是「累積歷史」，一隻寵物可以有很多筆，依服務日期排序查看。
 */
@Entity
@Table(name = "pet_grooming_notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetGroomingNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 這筆備註屬於哪隻寵物
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    @ToString.Exclude
    private Pet pet;

    // 對應的預約（供追溯是哪一次服務留下的備註）
    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    // 留下這筆備註的員工
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    @ToString.Exclude
    private User staff;

    // 本次美容狀況備註（毛況、皮膚狀況、行為表現、建議下次留意事項等）
    @Column(columnDefinition = "TEXT", nullable = false)
    private String note;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
