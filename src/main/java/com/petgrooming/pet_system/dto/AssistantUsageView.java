package com.petgrooming.pet_system.dto;

/** 後台慕馬本月用量：花費、預算、今天的提問數，以及換算台幣用的匯率。 */
public record AssistantUsageView(boolean enabled, double monthCostUsd, double monthlyBudgetUsd,
                                 long questionsToday, long questionsThisMonth, double usdToTwd) {
}
