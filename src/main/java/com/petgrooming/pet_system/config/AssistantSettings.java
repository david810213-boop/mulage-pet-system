package com.petgrooming.pet_system.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 慕馬 AI 小助手的設定（application.yml 的 assistant 區塊）。
 */
@Component
public class AssistantSettings {

    private final boolean enabled;
    private final Set<Long> testers;
    private final String apiKey;
    private final String model;
    private final double monthlyBudgetUsd;
    private final int dailyQuestionLimit;
    private final int maxOutputTokens;
    private final int historyMessages;
    private final int maxInputChars;
    private final double priceInput;
    private final double priceOutput;
    private final double priceCacheRead;
    private final double priceCacheWrite;

    public AssistantSettings(
            @Value("${assistant.enabled:false}") boolean enabled,
            @Value("${assistant.testers:}") String testers,
            @Value("${assistant.api-key:}") String apiKey,
            @Value("${assistant.model:claude-haiku-5-5}") String model,
            @Value("${assistant.monthly-budget-usd:19}") double monthlyBudgetUsd,
            @Value("${assistant.daily-question-limit:20}") int dailyQuestionLimit,
            @Value("${assistant.max-output-tokens:1024}") int maxOutputTokens,
            @Value("${assistant.history-messages:6}") int historyMessages,
            @Value("${assistant.max-input-chars:200}") int maxInputChars,
            @Value("${assistant.price.input:0.10}") double priceInput,
            @Value("${assistant.price.output:0.50}") double priceOutput,
            @Value("${assistant.price.cache-read:0.01}") double priceCacheRead,
            @Value("${assistant.price.cache-write:0.125}") double priceCacheWrite) {
        this.enabled = enabled;
        this.testers = parseIds(testers);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model.trim();
        this.monthlyBudgetUsd = monthlyBudgetUsd;
        this.dailyQuestionLimit = dailyQuestionLimit;
        this.maxOutputTokens = maxOutputTokens;
        this.historyMessages = historyMessages;
        this.maxInputChars = maxInputChars;
        this.priceInput = priceInput;
        this.priceOutput = priceOutput;
        this.priceCacheRead = priceCacheRead;
        this.priceCacheWrite = priceCacheWrite;
    }

    private static Set<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 這位會員看得到慕馬嗎：功能開啟，且沒有設定測試名單或在名單內。 */
    public boolean availableTo(Long userId) {
        return enabled && (testers.isEmpty() || testers.contains(userId));
    }

    /** 依實際用量換算美金花費。 */
    public double costUsd(int input, int output, int cacheRead, int cacheWrite) {
        return (input * priceInput + output * priceOutput + cacheRead * priceCacheRead
                + cacheWrite * priceCacheWrite) / 1_000_000d;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public double getMonthlyBudgetUsd() {
        return monthlyBudgetUsd;
    }

    public int getDailyQuestionLimit() {
        return dailyQuestionLimit;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public int getHistoryMessages() {
        return historyMessages;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }
}
