package com.petgrooming.pet_system.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * 固定公休星期設定（單例設定，全店只有一筆）。
 * 跟需求 16 的 ClosedDate（單一天公休）是兩套獨立機制：
 *   - ClosedDate：某一個特定日期公休（例如國定假日、店休一天）
 *   - WeeklyClosureSetting：每週固定哪幾天公休（例如每週四、五）
 * 兩者同時生效、互相疊加判斷，只要符合任一條件，當天就是公休日。
 */
@Entity
@Table(name = "weekly_closure_setting")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyClosureSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closed_monday", nullable = false)
    @Builder.Default
    private boolean closedMonday = false;

    @Column(name = "closed_tuesday", nullable = false)
    @Builder.Default
    private boolean closedTuesday = false;

    @Column(name = "closed_wednesday", nullable = false)
    @Builder.Default
    private boolean closedWednesday = false;

    @Column(name = "closed_thursday", nullable = false)
    @Builder.Default
    private boolean closedThursday = false;

    @Column(name = "closed_friday", nullable = false)
    @Builder.Default
    private boolean closedFriday = false;

    @Column(name = "closed_saturday", nullable = false)
    @Builder.Default
    private boolean closedSaturday = false;

    @Column(name = "closed_sunday", nullable = false)
    @Builder.Default
    private boolean closedSunday = false;

    public boolean isClosedOn(java.time.DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> closedMonday;
            case TUESDAY -> closedTuesday;
            case WEDNESDAY -> closedWednesday;
            case THURSDAY -> closedThursday;
            case FRIDAY -> closedFriday;
            case SATURDAY -> closedSaturday;
            case SUNDAY -> closedSunday;
        };
    }
}
