package com.petgrooming.pet_system.dto;

import java.time.LocalDateTime;

/** 後台慕馬對話紀錄的一列。 */
public record AssistantLogView(LocalDateTime createdAt, String memberName, boolean fromCustomer, String content,
                               String page, double costUsd, boolean fallback) {
}
