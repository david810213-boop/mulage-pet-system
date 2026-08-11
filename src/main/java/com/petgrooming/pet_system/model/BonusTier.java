package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 需求 3：積分門檻對應獎勵金，改成可在後台編輯，不用再改程式碼重新部署
@Entity
@Table(name = "bonus_tiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BonusTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_points", nullable = false)
    private int minPoints;

    @Column(name = "max_points", nullable = false)
    private int maxPoints;

    @Column(name = "bonus_amount", nullable = false)
    private int bonusAmount;
}
