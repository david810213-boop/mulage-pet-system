package com.petgrooming.pet_system.service.assistant;

import java.util.List;
import java.util.Map;

/**
 * 呼叫語言模型的介面。正式環境由 {@link AnthropicLlmClient} 呼叫 Claude API，測試換成假的實作。
 * 訊息與工具沿用 Claude Messages API 的 JSON 結構（以 Map 表示）。
 */
public interface LlmClient {

    /**
     * @param cachedSystem 每次都相同的系統提示（角色、規則、知識文件），會被快取
     * @param dynamicSystem 每次不同的系統提示（今天日期、顧客姓名、所在頁面）
     * @param tools 工具定義
     * @param messages 對話內容
     * @param allowTools false 時仍帶工具定義（對話裡有工具呼叫紀錄時 API 要求要帶），但不准再呼叫，強制模型用文字回答
     */
    LlmResult send(String cachedSystem, String dynamicSystem, List<Map<String, Object>> tools,
                   List<Map<String, Object>> messages, boolean allowTools);

    /** 一次呼叫的結果：回應內容區塊、停止原因、用量。 */
    record LlmResult(List<Map<String, Object>> content, String stopReason, Usage usage) {
        public LlmResult {
            content = List.copyOf(content);
        }
    }

    record Usage(int input, int output, int cacheRead, int cacheWrite) {
        public static final Usage ZERO = new Usage(0, 0, 0, 0);

        public Usage plus(Usage o) {
            return new Usage(input + o.input, output + o.output, cacheRead + o.cacheRead, cacheWrite + o.cacheWrite);
        }
    }
}
