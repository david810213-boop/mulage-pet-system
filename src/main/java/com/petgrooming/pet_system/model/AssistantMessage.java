package com.petgrooming.pet_system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 慕馬對話紀錄：一則顧客提問（USER）或一則慕馬回答（ASSISTANT）。
 * 每日提問次數、每月花費都從這張表計算；店家也用它抽查回答內容。
 */
@Entity
@Table(name = "assistant_messages", indexes = {
        @Index(name = "idx_assistant_messages_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_assistant_messages_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 顧客提問時所在的 LIFF 頁面（index、booking…），回答時參考
    @Column(length = 40)
    private String page;

    @Builder.Default
    @Column(name = "input_tokens", nullable = false)
    private int inputTokens = 0;

    @Builder.Default
    @Column(name = "output_tokens", nullable = false)
    private int outputTokens = 0;

    @Builder.Default
    @Column(name = "cache_read_tokens", nullable = false)
    private int cacheReadTokens = 0;

    @Builder.Default
    @Column(name = "cache_write_tokens", nullable = false)
    private int cacheWriteTokens = 0;

    @Builder.Default
    @Column(name = "cost_usd", nullable = false)
    private double costUsd = 0;

    // 呼叫 AI 失敗、預算或次數用完時的回覆，不計入每日題數
    @Builder.Default
    @Column(nullable = false)
    private boolean fallback = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
