package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

// 需求 22：乙方（店家）固定電子簽名檔，顯示在用戶端定型化契約最下方。
// 只會有一列資料（單例設定），存 Base64 圖片資料，後台可上傳/更換。
// 只有一張圖片，直接存資料庫欄位即可，不需要額外的雲端圖床（跟需求 17/21
// 那種「每個會員/每次美容都要存一張照片」的情境不同，不用套用同一套方案）。
@Entity
@Table(name = "company_signature")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Base64 Data URL 格式（例如 "data:image/png;base64,iVBORw0KG..."），
    // 前端可以直接當作 <img src="..."> 使用，不用另外組路徑。
    @Lob
    @Column(name = "signature_image", columnDefinition = "LONGTEXT")
    private String signatureImage;
}
