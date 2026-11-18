package com.petgrooming.pet_system.dto;

/** 慕馬是否開放給這位會員，以及今天還能問幾題。 */
public record AssistantQuotaResponse(boolean enabled, int remaining, int dailyLimit) {
}
