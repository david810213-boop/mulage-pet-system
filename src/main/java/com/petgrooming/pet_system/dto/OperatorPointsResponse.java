package com.petgrooming.pet_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 需求 6：經手人積分結算報表的一列
 */
@Data
@AllArgsConstructor
public class OperatorPointsResponse {
    private String operator;   // 經手人
    private double totalPoints; // 累計積分
    private int totalAmount;    // 累計金額
    private long itemCount;     // 項目數
}
