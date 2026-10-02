package com.petgrooming.pet_system.utils;

/**
 * 需求（追加，2026-10-02）：定型化契約版本。
 *
 * 契約全文只放在 static/liff/contract-body.html 一個地方（LIFF 預約頁、代客預約頁、
 * 公開契約頁、我的預約「查看契約」都載入同一份）。每次修改條文，要同步更新這裡的
 * VERSION，並把舊版全文另存成 contract-body-舊版本號.html，這樣每筆預約都能追溯
 * 「當時簽的是哪一版」，日後有爭議時拿得出當時的條文。
 */
public final class ContractInfo {
    public static final String VERSION = "2026-10-02";

    private ContractInfo() {
    }
}
