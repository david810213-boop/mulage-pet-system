package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "appointments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // 預約唯一識別碼

    // 預約歸屬的使用者
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Column(nullable = false)
    private String petName;             // 寵物名稱

    @Column(nullable = false)
    private String petType;             // DOG / CAT / 其他

    @Column(nullable = false)
    private LocalDate date;             // 預約日期

    @Column(nullable = false)
    private LocalTime startTime;        // 開始時間

    @Column(nullable = false)
    private LocalTime endTime;          // 結束時間

    // FetchType 改為 EAGER：確保撈預約單時，一併把美容項目撈出來顯示在前端
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "appointment_grooming_items", 
        joinColumns = @JoinColumn(name = "appointment_id"),          // 對應 appointments 表的 id
        inverseJoinColumns = @JoinColumn(name = "grooming_item_id")  // 對應 grooming_items 表的 id
    )
    private List<GroomingItem> selectedItems; // 選擇的動態美容項目實體清單

    @Column(nullable = false)
    private int totalAmount;            // 總金額（建立時自動計算）

    @Column(nullable = false)
    @Builder.Default
    private boolean paid = false;       // 是否已付款

    // 負責美容的員工（STAFF），結帳後自動計算績效用
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    @ToString.Exclude
    private User staff;

    // ── 需求 3：店家確認最後時間 ─────────────────────────────────────────
    // 顧客送出預約後為 PENDING_CONFIRM，店家確認並敲定實際時間後填入 confirmedTime 並轉 CONFIRMED
    @Column(name = "confirmed_time")
    private LocalDateTime confirmedTime;

    // ── 需求 7：預約備注（雙可見性）───────────────────────────────────────
    // 店家內部備注：例如「耳朵加強去油」「造型需求」。絕不回傳給顧客端 API。
    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    // 會員可見備注：店家想傳達給會員的訊息。會出現在 /api/appointments/my。
    @Column(name = "member_note", columnDefinition = "TEXT")
    private String memberNote;

    // ── 取消預約相關 ─────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    // 記錄由誰取消（顯示用文字，例如「會員自行取消」「員工：王小美」）
    @Column(name = "cancelled_by")
    private String cancelledBy;

    public boolean isCancelled() {
        return status == AppointmentStatus.CANCELLED;
    }
}