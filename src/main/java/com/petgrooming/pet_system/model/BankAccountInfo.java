package com.petgrooming.pet_system.model;

import com.petgrooming.pet_system.enums.BankAccountPurpose;
import jakarta.persistence.*;
import lombok.*;

// 需求 10：店家匯款帳號資訊，顧客/店員選「匯款」付款方式時自動帶出顯示。
// 需求（追加）：改成依用途各存一筆——「結帳收款」與「儲值金收款（大額專用）」是不同帳戶，
// 不再是單例設定，改用 purpose 欄位區分，每種用途各自最多一筆。
@Entity
@Table(name = "bank_account_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20, nullable = false, columnDefinition = "varchar(20) default 'CHECKOUT'")
    private BankAccountPurpose purpose; // CHECKOUT / TOPUP

    @Column(name = "bank_name", length = 100)
    private String bankName;        // 銀行名稱

    @Column(name = "account_number", length = 50)
    private String accountNumber;   // 帳號

    @Column(name = "account_holder", length = 100)
    private String accountHolder;   // 戶名

    // ── 需求 21：匯款/線上儲值 QR Code（Cloudinary 雲端圖床）────────────
    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    @Column(name = "qr_code_public_id", length = 200)
    private String qrCodePublicId;
}
