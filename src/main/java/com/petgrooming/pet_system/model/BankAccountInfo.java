package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

// 需求 10：店家匯款帳號資訊，顧客/店員選「匯款」付款方式時自動帶出顯示。
// 只會有一列資料（單例設定），後台可編輯。
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

    @Column(name = "bank_name", length = 100)
    private String bankName;        // 銀行名稱

    @Column(name = "account_number", length = 50)
    private String accountNumber;   // 帳號

    @Column(name = "account_holder", length = 100)
    private String accountHolder;   // 戶名
}
