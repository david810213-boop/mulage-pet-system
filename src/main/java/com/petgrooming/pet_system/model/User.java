package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.CustomerSource;
import com.petgrooming.pet_system.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String username;            // 登入帳號（email）

    @Column(nullable = false)
    private String password;

    // 新需求：共用平板快速切換使用者用的 4 位數 PIN 碼（加密儲存，同密碼一樣用 BCrypt）。
    // 只有員工/管理員需要設定，尚未設定過的話這裡是 null，切換清單上不會出現他們，
    // 提示要先去設定 PIN 才能被切換。
    @Column(name = "switch_pin")
    private String switchPin;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;              // ADMIN / STAFF / CUSTOMER

    @Column(length = 100)
    private String email;               // 電子郵件（選填）

    @Column(length = 20)
    private String phone;               // 電話號碼（選填）

    @Column(name = "line_user_id", unique = true, length = 64)
    private String lineUserId;          // LINE 登入對應的 userId（顧客專用，選填）

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;    // 帳號是否啟用（預留停用功能）

    // ── 會員基本資料（供店家記錄分析用，選填）───────────────────────────
    @Column
    private Integer age;                // 年齡

    @Column(length = 50)
    private String occupation;          // 職業

    @Column(name = "residence_area", length = 50)
    private String residenceArea;       // 居住區域（例如：板橋區）

    // ── 需求 19：定型化契約要求蒐集的家長資料（皆選填，只需填一次）───────
    @Column(name = "mailing_address", length = 200)
    private String mailingAddress;              // 通訊地址（完整地址，與 residenceArea 用途不同，那個只是行銷分析用的粗略區域）

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;        // 緊急聯絡人姓名

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;        // 緊急聯絡人電話

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CustomerSource source;      // 得知本店的來源管道

    @Column(name = "profile_completed_at")
    private LocalDateTime profileCompletedAt; // 首次完成資料填寫時間

    // ── 需求 8：店家後台備注會員特殊資訊 ──────────────────────────────────
    // 例：對某剪法敏感、需特別安撫、慣用付款方式等。
    // 僅後台可見，絕不回傳給任何顧客端 API（UserResponse 不含此欄位）。
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── 寵物關聯 ─────────────────────────────────────────
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL)
    @ToString.Exclude
    @Builder.Default
    private List<Pet> pets = new ArrayList<>();

    // ── 便利方法 ──────────────────
    public boolean isAdmin()         { return role == UserRole.ADMIN; }
    public boolean isStaff()         { return role == UserRole.STAFF; }
    public boolean isCustomer()      { return role == UserRole.CUSTOMER; }
    public boolean isStaffOrAdmin()  { return isStaff() || isAdmin(); }

}
