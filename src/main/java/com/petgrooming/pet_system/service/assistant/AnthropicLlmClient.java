package com.petgrooming.pet_system.service.assistant;

import com.petgrooming.pet_system.config.AssistantSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 以 Claude Messages API 呼叫模型。只用 Spring 內建的 RestClient，不另外加 SDK 依賴。
 */
@Slf4j
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final String API_VERSION = "2023-06-01";

    private final AssistantSettings settings;
    private final RestClient restClient;

    public AnthropicLlmClient(AssistantSettings settings) {
        this.settings = settings;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .requestFactory(factory)
                .build();
    }

    @Override
    public LlmResult send(String cachedSystem, String dynamicSystem, List<Map<String, Object>> tools,
                          List<Map<String, Object>> messages) {
        if (settings.getApiKey().isEmpty()) {
            throw new AssistantUnavailableException("未設定 ANTHROPIC_API_KEY");
        }
        Map<String, Object> cachedBlock = new LinkedHashMap<>();
        cachedBlock.put("type", "text");
        cachedBlock.put("text", cachedSystem);
        // 工具定義與這一段系統提示每次都一樣，標記快取，之後的提問只付快取讀取的價格
        cachedBlock.put("cache_control", Map.of("type", "ephemeral"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.getModel());
        body.put("max_tokens", settings.getMaxOutputTokens());
        body.put("system", List.of(cachedBlock, Map.of("type", "text", "text", dynamicSystem)));
        body.put("tools", tools);
        body.put("messages", messages);

        Map<?, ?> res;
        try {
            res = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", settings.getApiKey())
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new AssistantUnavailableException("呼叫 Claude API 失敗：" + e.getMessage(), e);
        }
        if (res == null) {
            throw new AssistantUnavailableException("Claude API 沒有回應內容");
        }
        return parse(res);
    }

    @SuppressWarnings("unchecked")
    static LlmResult parse(Map<?, ?> res) {
        List<Map<String, Object>> content = new ArrayList<>();
        if (res.get("content") instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> m) {
                    content.add((Map<String, Object>) m);
                }
            }
        }
        Usage usage = Usage.ZERO;
        if (res.get("usage") instanceof Map<?, ?> u) {
            usage = new Usage(num(u.get("input_tokens")), num(u.get("output_tokens")),
                    num(u.get("cache_read_input_tokens")), num(u.get("cache_creation_input_tokens")));
        }
        Object stop = res.get("stop_reason");
        return new LlmResult(content, stop == null ? "" : stop.toString(), usage);
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
