package com.petgrooming.pet_system.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 「我的績效」頁面用：員工查詢自己的當日／當月累積積分，
 * 以及距離下一個獎勵金級距還差多少分。
 */
@Data
@Builder
public class StaffProgressResponse {
    private String staffName;
    private double todayPoints;          // 今日累積積分（不分類別，全部加總）
    private double monthMainPoints;      // 當月主要積分（不含接待、不含OTHER）
    private double monthReceptionPoints; // 當月接待積分（CHECKIN + CHECKOUT）
    private int currentBonus;            // 依目前主要積分對照到的獎勵金（未達門檻則為 0）
    private Integer nextThreshold;       // 下一個級距的門檻分數；已達最高級距則為 null
    private Double pointsToNextThreshold; // 距離下一個級距還差幾分；已達最高級距則為 null
}
