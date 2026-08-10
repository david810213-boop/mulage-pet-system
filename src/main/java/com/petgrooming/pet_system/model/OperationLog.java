package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 操作紀錄（僅供 ADMIN 查看）：記錄誰在什麼時候對什麼資料做了什麼操作
// 保留 60 天，由排程自動清除超過期限的舊紀錄
@Entity
@Table(name = "operation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;      // 操作人帳號

    @Column(name = "actor_name", length = 100)
    private String actorName;          // 操作人姓名（快照，避免之後帳號改名查不到當時是誰）

    @Column(name = "actor_role", length = 20)
    private String actorRole;          // 操作人角色（ADMIN / STAFF / CUSTOMER）

    @Column(name = "category", length = 30)
    private String category;           // 分類：APPOINTMENT / WALLET / WALKIN / PERFORMANCE / CUSTOMER / AUTH

    @Column(name = "action", length = 50)
    private String action;             // 動作：CONFIRM / CANCEL / CHECKOUT / SPLIT_POINTS ... 等

    @Column(name = "target_desc", length = 255)
    private String targetDesc;         // 操作對象描述，例如 "預約 #AP012"、"會員 user@pet.com"

    @Column(name = "detail", length = 500)
    private String detail;             // 補充說明，例如金額、備註內容摘要

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
