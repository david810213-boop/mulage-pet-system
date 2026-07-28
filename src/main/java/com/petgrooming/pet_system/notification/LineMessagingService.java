package com.petgrooming.pet_system.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * LINE 官方帳號推播訊息（Messaging API push message）。
 *
 * 注意：這跟顧客登入用的 LINE Login 是「不同的 channel」。
 * 要讓這個服務真正動起來，店家需要：
 *   1. 到 LINE Developers Console 建立（或使用既有的）Messaging API channel
 *      —— 通常跟 LIFF 綁在同一個 Provider 底下的官方帳號
 *   2. 在該 channel 頁面「Messaging API」分頁，簽發一組長效 Channel Access Token
 *   3. 用環境變數帶進系統：LINE_MESSAGING_CHANNEL_ACCESS_TOKEN=xxxxx
 *
 * 在正式申請好 token 之前，這個服務會自動跳過發送、只記 log，不會讓系統噴錯。
 */
@Slf4j
@Service
public class LineMessagingService {

    @Value("${line.messaging.channel-access-token:}")
    private String channelAccessToken;

    private final RestClient restClient = RestClient.create();

    /**
     * 推播一則純文字訊息給指定的 LINE 使用者。
     * @param lineUserId 對方的 LINE userId（User.lineUserId，登入時取得）
     * @param text       訊息內容
     */
    public void pushText(String lineUserId, String text) {
        if (lineUserId == null || lineUserId.isBlank()) {
            log.info("[LINE推播] 略過發送：此會員沒有綁定 LINE（可能是帳密註冊）");
            return;
        }
        if (channelAccessToken == null || channelAccessToken.isBlank()) {
            log.warn("[LINE推播] 尚未設定 LINE_MESSAGING_CHANNEL_ACCESS_TOKEN，訊息未送出。內容：{}", text);
            return;
        }

        try {
            restClient.post()
                    .uri("https://api.line.me/v2/bot/message/push")
                    .header("Authorization", "Bearer " + channelAccessToken)
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "to", lineUserId,
                            "messages", List.of(Map.of("type", "text", "text", text))
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[LINE推播] 已發送給 {}", lineUserId);
        } catch (Exception e) {
            // 推播失敗（token 失效、對方封鎖官方帳號等）不應該讓核心業務動作（例如確認預約）失敗，
            // 只記警告，讓呼叫端的交易照常完成。
            log.warn("[LINE推播] 發送失敗，不影響主要操作。原因：{}", e.getMessage());
        }
    }
}
