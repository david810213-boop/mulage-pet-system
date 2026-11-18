package com.petgrooming.pet_system.service.assistant;

/** 呼叫 AI 失敗（網路、逾時、API 錯誤）。慕馬改回覆「請稍後再試或聯絡店家」，不會讓顧客看到錯誤頁。 */
public class AssistantUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AssistantUnavailableException(String message) {
        super(message);
    }
}
