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

    // ── 中文顯示用（衍生欄位，不存資料庫）──────────────────────────────
    // 操作紀錄要能讓人一眼看懂做了什麼，身分/分類/動作全部轉中文顯示，
    // 對照不到的字（例如以後新增分類忘記補翻譯）就照原樣顯示，不會噴錯或空白。
    private static final java.util.Map<String, String> ROLE_LABELS = java.util.Map.of(
            "ADMIN", "管理員",
            "STAFF", "員工",
            "CUSTOMER", "會員"
    );

    private static final java.util.Map<String, String> CATEGORY_LABELS = java.util.Map.ofEntries(
            java.util.Map.entry("APPOINTMENT", "預約"),
            java.util.Map.entry("WALLET", "會員錢包"),
            java.util.Map.entry("WALKIN", "現場開單"),
            java.util.Map.entry("RETAIL", "零售商品"),
            java.util.Map.entry("SUPPLY", "店用洗劑"),
            java.util.Map.entry("PERFORMANCE", "績效"),
            java.util.Map.entry("CUSTOMER", "會員資料"),
            java.util.Map.entry("AUTH", "帳號登入")
    );

    private static final java.util.Map<String, String> ACTION_LABELS = java.util.Map.ofEntries(
            // 預約
            java.util.Map.entry("BOOK", "建立預約"),
            java.util.Map.entry("CANCEL", "取消預約"),
            java.util.Map.entry("CONFIRM", "確認預約"),
            java.util.Map.entry("CHECKIN_ORDER", "現場開單（依預約）"),
            java.util.Map.entry("START", "開始服務"),
            java.util.Map.entry("END_SERVICE", "結束服務"),
            java.util.Map.entry("FILL_OPERATOR", "填寫經手人"),
            java.util.Map.entry("FINAL_CHECK", "核對結果"),
            java.util.Map.entry("CHECKOUT", "結帳"),
            java.util.Map.entry("REFUND", "退款"),
            java.util.Map.entry("CONFIRM_WIRE_TRANSFER", "確認匯款收款"),
            java.util.Map.entry("SET_CLOSED_DATE", "設定公休日"),
            java.util.Map.entry("UNSET_CLOSED_DATE", "取消公休日"),
            java.util.Map.entry("UPDATE_WEEKLY_CLOSURE", "更新固定公休星期"),
            java.util.Map.entry("SET_SLOT_CAPACITY", "調整時段名額"),
            java.util.Map.entry("SET_DEFAULT_SLOT_CAPACITY", "調整預設時段容量範本"),
            java.util.Map.entry("UPDATE_BANK_ACCOUNT", "更新匯款帳號"),
            java.util.Map.entry("UPDATE_BANK_ACCOUNT_QR", "更新收款 QR Code"),
            java.util.Map.entry("UPDATE_COMPANY_SIGNATURE", "更新乙方簽名檔"),
            // 現場開單
            java.util.Map.entry("CREATE", "建立現場單"),
            // 零售商品
            java.util.Map.entry("CREATE_RETAIL_PRODUCT", "新增商品"),
            java.util.Map.entry("UPDATE_RETAIL_PRODUCT", "更新商品"),
            java.util.Map.entry("ADJUST_RETAIL_STOCK", "調整庫存"),
            java.util.Map.entry("DELETE_RETAIL_PRODUCT", "下架商品"),
            // 店用洗劑
            java.util.Map.entry("CREATE_SUPPLY", "新增洗劑品項"),
            java.util.Map.entry("UPDATE_SUPPLY", "更新洗劑品項"),
            java.util.Map.entry("RESTOCK_SUPPLY", "洗劑進貨"),
            java.util.Map.entry("USE_SUPPLY", "洗劑領用"),
            java.util.Map.entry("DELETE_SUPPLY", "下架洗劑品項"),
            // 績效
            java.util.Map.entry("SETTLE", "結算績效"),
            java.util.Map.entry("CANCEL_SETTLE", "取消結算"),
            java.util.Map.entry("SPLIT_POINTS", "積分拆分"),
            java.util.Map.entry("CREATE_BONUS_TIER", "新增獎勵金級距"),
            java.util.Map.entry("UPDATE_BONUS_TIER", "更新獎勵金級距"),
            java.util.Map.entry("DELETE_BONUS_TIER", "刪除獎勵金級距"),
            // 會員資料
            java.util.Map.entry("ADD_PET", "新增毛孩"),
            java.util.Map.entry("SET_COAT_TYPE", "設定毛長"),
            java.util.Map.entry("SET_NOTE", "設定備註"),
            java.util.Map.entry("UPDATE_PROFILE", "更新個人資料"),
            java.util.Map.entry("UPLOAD_PET_PHOTO", "上傳毛孩照片"),
            java.util.Map.entry("UPLOAD_GROOMING_NOTE_PHOTO", "上傳美容紀錄照片"),
            // 會員錢包
            java.util.Map.entry("DEPOSIT", "儲值扣款"),
            java.util.Map.entry("TOPUP_REQUEST", "申請儲值"),
            java.util.Map.entry("TOPUP_CONFIRM", "確認儲值"),
            java.util.Map.entry("TOPUP_REJECT", "駁回儲值"),
            // 帳號登入
            java.util.Map.entry("LOGIN", "登入"),
            java.util.Map.entry("LOGOUT", "登出"),
            java.util.Map.entry("CHANGE_PASSWORD", "修改密碼"),
            java.util.Map.entry("CREATE_STAFF", "新增員工帳號"),
            java.util.Map.entry("SET_SWITCH_PIN", "設定切換 PIN 碼"),
            java.util.Map.entry("BIND_LINE", "綁定 LINE 帳號"),
            java.util.Map.entry("SWITCH_USER", "切換使用者")
    );

    public String getActorRoleLabel() {
        return actorRole != null ? ROLE_LABELS.getOrDefault(actorRole, actorRole) : "—";
    }

    public String getCategoryLabel() {
        return category != null ? CATEGORY_LABELS.getOrDefault(category, category) : "—";
    }

    public String getActionLabel() {
        return action != null ? ACTION_LABELS.getOrDefault(action, action) : "—";
    }
}
