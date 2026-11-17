package com.petgrooming.pet_system.dto;

import java.util.List;

/**
 * 慕馬的回答。
 *
 * @param mood      answer＝一般回答；unsure＝不確定、建議聯絡店家；limit＝今天題數或本月預算用完；error＝暫時無法回答
 * @param remaining 今天還能問幾題
 */
public record AssistantReplyResponse(String text, List<Action> actions, String mood, int remaining) {

    public AssistantReplyResponse {
        actions = List.copyOf(actions);
    }

    /** 回答下方的按鈕；url 只會是系統內頁面或官方 LINE。 */
    public record Action(String label, String url, String type) {
    }
}
