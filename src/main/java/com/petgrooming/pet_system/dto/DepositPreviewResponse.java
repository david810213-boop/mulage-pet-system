package com.petgrooming.pet_system.dto;

/**
 * 送出預約前試算：如果這一筆送出，當天需要再付多少訂金。
 *
 * @param amount 送出後需要再付的訂金（0＝不需要）
 * @param exempt 儲值金會員不用付訂金
 * @param reason 給顧客看的說明（例如「同一天預約 2 位」）
 */
public record DepositPreviewResponse(int amount, boolean exempt, String reason) {
}
