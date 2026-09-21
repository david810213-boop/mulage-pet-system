package com.petgrooming.pet_system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 需求（追加，2026-09-17）：Sentry 監控驗證用的暫時端點。
 *
 * ⚠️ 這是臨時測試用的檔案，確認 Sentry 有沒有真的收到未預期例外之後，
 * 建議直接刪掉這個檔案，不要留在正式站上（沒有登入驗證、任何人都能觸發，
 * 雖然只是丟一個測試例外，沒有實際危害，但沒有理由留一個不需要的公開端點）。
 *
 * 用法：部署後直接瀏覽器打開 /test/sentry-check（不用登入），
 * 畫面會顯示 500 錯誤，這是預期行為——這個例外故意沒有被任何地方接住，
 * 用來確認它真的會被送到 Sentry。送出後去 Sentry 的 Issues 頁面確認
 * 有沒有跳出一筆標題含「Sentry 監控驗證測試」的新錯誤紀錄。
 */
@RestController
public class SentryTestController {

    @GetMapping("/test/sentry-check")
    public String triggerTestError() {
        throw new RuntimeException("Sentry 監控驗證測試——如果你在 Sentry 後台看到這筆，代表監控正常運作");
    }
}
