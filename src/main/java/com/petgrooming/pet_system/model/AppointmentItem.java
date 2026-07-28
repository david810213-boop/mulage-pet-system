package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.PerformanceCategory;
import jakarta.persistence.*;
import lombok.*;

/**
 * 預約現場開單項目（依預約編號開立訂單）。
 *
 * 使用情境：家長帶寵物到店，店員點開該筆預約（例如 AP001），
 * 依照現場實際情況新增/調整要做的服務項目，並可個別指定經手人（跟現場開單
 * WalkInOrderItem 同一套設計：經手人綁定實際員工帳號，可以先不填，
 * 之後再從「待補經手人」清單補上，補上的瞬間即建立績效紀錄）。
 *
 * 與 Appointment.selectedItems（顧客線上預約時勾選的項目）的差異：
 * selectedItems 是預約當下的「意向項目」，這裡的 AppointmentItem 才是
 * 「店員依現場情況實際確認要做的項目」，兩者可能不同（可能加購、減少）。
 * 一旦這張現場開單建立，結帳時的績效計算會改用這裡的項目 + 經手人，
 * 不再只用 selectedItems 整包算給單一 appointment.staff。
 */
@Entity
@Table(name = "appointment_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    @ToString.Exclude
    private Appointment appointment;

    // 對應的美容項目 id
    @Column(name = "grooming_item_id")
    private Long groomingItemId;

    // 項目名稱（快照，避免項目日後改名或下架影響歷史單）
    @Column(name = "item_name", nullable = false)
    private String itemName;

    // 單價（快照）
    @Column(nullable = false)
    private int price;

    // 積分（快照，補填經手人後用於積分結算）
    @Column(nullable = false)
    @Builder.Default
    private double points = 0.0;

    // 績效分類（快照，供積分報表分類）
    @Enumerated(EnumType.STRING)
    @Column(name = "performance_category", length = 30)
    @Builder.Default
    private PerformanceCategory performanceCategory = PerformanceCategory.OTHER;

    // 經手人：綁定實際員工帳號。null = 未填寫。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_staff_id")
    @ToString.Exclude
    private User operatorStaff;

    // 是否已經把這筆的積分寫進 PerformanceRecord 了（避免同一筆項目被重複計入積分）
    @Column(name = "points_awarded", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean pointsAwarded = false;

    public boolean isOperatorFilled() {
        return operatorStaff != null;
    }
}
