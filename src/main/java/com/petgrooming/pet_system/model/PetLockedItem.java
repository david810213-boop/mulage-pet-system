package com.petgrooming.pet_system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 狗狗的固定組合（客製化鎖定）中的一個項目，價格是鎖定當時的固定價，之後店家調價不影響這隻狗。
 * 見 docs/adr/0012-staff-part-time-and-custom-lock.md
 */
@Entity
@Table(name = "pet_locked_items", indexes = @Index(name = "idx_pet_locked_items_pet", columnList = "pet_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetLockedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    @Column(name = "grooming_item_id", nullable = false)
    private Long groomingItemId;

    @Column(name = "locked_price", nullable = false)
    private int lockedPrice;
}
