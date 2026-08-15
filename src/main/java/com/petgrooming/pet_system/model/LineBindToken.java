package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 店員/店家綁定 LINE 帳號用的一次性驗證碼。
 *
 * 流程：店員在網頁後台（已用帳密登入）產生一組驗證碼，用手機 LINE 開啟綁定用的 LIFF 頁面，
 * 輸入這組碼，後端驗證碼正確 + 還沒過期 + 還沒用過，就把這支手機的 LINE userId 綁到
 * 對應的店員帳號上（User.lineUserId），之後低庫存等通知才發得到這支手機。
 *
 * 用資料庫記錄（而不是純 JWT）是為了能簡單支援「用過一次就失效」跟過期時間查詢。
 */
@Entity
@Table(name = "line_bind_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineBindToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code; // 6 位數驗證碼，店員在手機上輸入用，比一長串 UUID 好打

    @Column(name = "target_username", nullable = false, length = 100)
    private String targetUsername; // 要綁定到哪個店員帳號

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean used = false;
}
