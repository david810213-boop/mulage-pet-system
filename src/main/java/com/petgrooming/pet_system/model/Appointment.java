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

    // ── 定型化契約簽署紀錄 ───────────────────────────────────────────────
    // 顧客預約前必須詳閱契約，並在簽名板上親筆簽名，才能送出預約
    @Column(name = "contract_signature_image", columnDefinition = "LONGTEXT")
    private String contractSignatureImage;   // 手寫簽名圖片（base64 PNG dataURL）

    @Column(name = "contract_agreed_at")
    private LocalDateTime contractAgreedAt;

    // ── 結束服務（完成美容施作，通知家長來接）───────────────────────────────
    // 服務項目全部做完、可以請家長來店接寵物時，店員點「結束服務」：
    // 記錄完成積分給操作者、發送 LINE 通知家長，狀態仍維持「進行中」不變。
    // 核對（final-check）強制要求已先完成這一步，才能進行。
    // 注意（ddl-auto 陷阱）：這是新增到「既有 appointments 資料表」的 NOT NULL 欄位，
    // 若只寫 nullable=false 沒給資料庫層級預設值，既有列會因無值可填導致 ALTER TABLE 失敗
    // （沿用 GroomingItem.bookable 欄位同樣的處理方式）。
    @Column(name = "service_ended_done", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean serviceEndedDone = false;

    // 負責結束服務的員工（COMPLETE 積分記給這位）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_ended_staff_id")
    @ToString.Exclude
    private User serviceEndedStaff;

    @Column(name = "service_ended_at")
    private LocalDateTime serviceEndedAt;

    // ── 進行中核對（接待送出）相關紀錄 ─────────────────────────────────────
    // 店員從「進行中」的預約選擇核對：記錄本次美容狀況備註 + 家長現場簽名確認，
    // 核對完成才能進入結帳；同時把該店員記為「接待送出」積分。
    @Column(name = "final_check_done", nullable = false)
    @Builder.Default
    private boolean finalCheckDone = false;

    // 負責核對送出的員工
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_check_staff_id")
    @ToString.Exclude
    private User finalCheckStaff;

    // 本次美容狀況備註（同時會另存一筆到 PetGroomingNote 累積毛孩歷史）
    @Column(name = "final_check_note", columnDefinition = "TEXT")
    private String finalCheckNote;

    // 家長核對後的現場簽名（base64 PNG dataURL）
    @Column(name = "final_check_signature_image", columnDefinition = "LONGTEXT")
    private String finalCheckSignatureImage;

    @Column(name = "final_check_at")
    private LocalDateTime finalCheckAt;

    // ── 現場開單（依預約編號開立訂單）─────────────────────────────────────
    // 家長到店後，店員依現場情況確認/調整服務項目，確認後才能「開始服務」。
    @Column(name = "checkin_order_confirmed", nullable = false)
    @Builder.Default
    private boolean checkinOrderConfirmed = false;

    public boolean isCancelled() {
        return status == AppointmentStatus.CANCELLED;
    }
}