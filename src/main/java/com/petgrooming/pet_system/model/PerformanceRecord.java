package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 員工績效紀錄（每筆代表某員工在某預約中完成某個大項的積分）
 * 一筆預約可產生多筆績效紀錄（每個大項一筆，可拆分給多人）
 */
@Entity
@Table(name = "performance_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 負責的員工
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    @ToString.Exclude
    private User staff;

    // 對應的預約（若此筆績效來自現場開單，appointmentId 為 null，改看 walkInOrderId）
    @Column(name = "appointment_id")
    private Long appointmentId;

    // 對應的現場單（若此筆績效來自預約結帳，此欄位為 null）
    @Column(name = "walk_in_order_id")
    private Long walkInOrderId;

    // 績效大項分類
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private PerformanceCategory category;

    // 實際獲得的積分（可以是 0.5 等小數，代表拆分）
    @Column(nullable = false)
    private Double points;

    // 服務日期（方便按日/月統計）
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    // 備註（例如：「與小王各分 0.5」）
    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
