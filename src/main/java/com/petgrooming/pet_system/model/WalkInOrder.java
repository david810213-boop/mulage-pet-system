package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 需求 5 / 6：現場開單（到店開單）。
 *
 * 使用情境：不同寵物有需要調理的項目時，店家現場針對這隻寵物開一張單，
 * 加入一到多個項目（含不可線上預約的調理項目）。這張單本身就是一筆「交易紀錄」，
 * 可日後拉出來補填每個項目的經手人以計算積分。
 *
 * 與既有 Transaction 的關係：
 *   既有 Transaction 綁定「一筆預約」(appointment_id NOT NULL)，用於線上預約結帳。
 *   現場開單不一定有預約、且一張單多個項目各自有經手人，資料結構不同，
 *   因此獨立成 WalkInOrder，不動既有結帳流程，避免破壞相容性。
 */
@Entity
@Table(name = "walk_in_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalkInOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 關聯會員（選填：現場客可能非會員）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    @ToString.Exclude
    private User member;

    // 寵物名稱（快照，現場填寫，可不對應系統內寵物）
    @Column(name = "pet_name")
    private String petName;

    // 訂單總額（各項目 price 加總）
    @Column(name = "total_amount", nullable = false)
    @Builder.Default
    private int totalAmount = 0;

    // 開單人（店家 / 員工姓名，快照）
    @Column(name = "created_by", length = 100)
    private String createdBy;

    // 訂單備註
    @Column(length = 300)
    private String note;

    // ── 需求：現場單串接結帳 ─────────────────────────────────────────────
    // 開單當下未付款；paid=true 才代表已完成結帳（跟 Appointment.paid 同樣的語意）。
    // 用 columnDefinition 給資料庫層級預設值，避免既有表加 NOT NULL 欄位時 ALTER 失敗
    // （這是先前 GroomingItem.bookable 踩過的坑，這裡直接照做避開）。
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean paid = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private com.petgrooming.pet_system.enums.PaymentMethod paymentMethod;

    @Column(name = "payment_time")
    private LocalDateTime paymentTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── 結束服務（比照預約單邏輯）────────────────────────────────────────
    @Column(name = "service_ended_done", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean serviceEndedDone = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_ended_staff_id")
    @ToString.Exclude
    private User serviceEndedStaff;

    @Column(name = "service_ended_at")
    private LocalDateTime serviceEndedAt;

    // ── 核對（比照預約單邏輯，須先結束服務才能核對，核對後才能結帳）───────
    @Column(name = "final_check_done", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean finalCheckDone = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_check_staff_id")
    @ToString.Exclude
    private User finalCheckStaff;

    @Column(name = "final_check_note", columnDefinition = "TEXT")
    private String finalCheckNote;

    @Column(name = "final_check_signature_image", columnDefinition = "LONGTEXT")
    private String finalCheckSignatureImage;

    @Column(name = "final_check_at")
    private LocalDateTime finalCheckAt;

    // 開單人（店家/員工帳號，用於開單當下記 CHECKIN 積分；createdBy 只有姓名快照，這裡另存帳號方便查）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_staff_id")
    @ToString.Exclude
    private User createdByStaff;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<WalkInOrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // 便利方法：加入項目並維持雙向關聯
    public void addItem(WalkInOrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }
}
