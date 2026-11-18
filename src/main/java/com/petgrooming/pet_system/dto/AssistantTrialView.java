package com.petgrooming.pet_system.dto;

import java.util.List;

/** 後台試問慕馬的結果：回答、按鈕、用到的工具與這一題的花費。 */
public record AssistantTrialView(String memberName, String question, String text,
                                 List<AssistantReplyResponse.Action> actions, String mood, List<String> toolsUsed,
                                 double costUsd, int inputTokens, int outputTokens) {
    public AssistantTrialView {
        actions = actions == null ? List.of() : List.copyOf(actions);
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }
}
