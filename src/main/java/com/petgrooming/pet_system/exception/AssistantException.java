package com.petgrooming.pet_system.exception;

/** 慕馬 AI 小助手相關的業務錯誤（未開放、問題太長等）。 */
public class AssistantException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public AssistantException(String message) {
        super(message);
    }

    public AssistantException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
