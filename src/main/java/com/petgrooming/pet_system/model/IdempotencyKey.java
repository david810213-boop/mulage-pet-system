package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 已處理過的請求識別碼。同一個 key 只能寫入一次（唯一索引），用來擋下連點或網路重送造成的重複儲值。
 */
@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_keys_key", columnNames = "idempotency_key"),
        indexes = @Index(name = "idx_idempotency_keys_created", columnList = "created_at"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
