package com.petgrooming.pet_system.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.petgrooming.pet_system.config.AssistantSettings;
import com.petgrooming.pet_system.dto.AssistantReplyResponse.Action;
import org.junit.jupiter.api.Test;

/** 按鈕標記只接受白名單，網址由系統產生；測試名單與花費換算。 */
class AssistantActionsTest {

    @Test
    void onlyWhitelistedButtonsBecomeLinks() {
        AssistantActions.Parsed p = AssistantActions.parse(
                "請到匯款資訊回報後五碼 [[按鈕:deposit:2026-11-24]][[按鈕:http]][[按鈕:wallet]][[按鈕:pets]]");

        assertThat(p.text()).isEqualTo("請到匯款資訊回報後五碼");
        assertThat(p.actions()).extracting(Action::url)
                .containsExactly("/liff/my-appointments.html?deposit=2026-11-24", "/liff/wallet.html");
    }

    @Test
    void wordsAfterTheFirstButtonAreDropped() {
        AssistantActions.Parsed p = AssistantActions.parse("詳細可以到毛孩檔案查看。\n\n[[按鈕:pets]] 毛孩檔案");
        assertThat(p.text()).isEqualTo("詳細可以到毛孩檔案查看。");
        assertThat(p.actions()).extracting(Action::label).containsExactly("毛孩檔案");
    }

    @Test
    void datesUseTheSameFormatAsAnswers() {
        int year = java.time.LocalDate.now().getYear();
        assertThat(AssistantTools.day(java.time.LocalDate.of(year, 8, 14))).endsWith("8/14（" +
                weekdayOf(java.time.LocalDate.of(year, 8, 14)) + "）");
        assertThat(AssistantTools.day(java.time.LocalDate.of(year + 1, 1, 5))).startsWith((year + 1) + "/1/5（");
    }

    private static String weekdayOf(java.time.LocalDate d) {
        return "一二三四五六日".substring(d.getDayOfWeek().getValue() - 1, d.getDayOfWeek().getValue());
    }

    @Test
    void depositWithBadDateFallsBackToMyAppointments() {
        AssistantActions.Parsed p = AssistantActions.parse("[[按鈕:deposit:2026-13-40]]");
        assertThat(p.actions()).extracting(Action::url).containsExactly("/liff/my-appointments.html");
    }

    @Test
    void testersLimitWhoSeesTheAssistant() {
        AssistantSettings s = settings(true, "5, 8");
        assertThat(s.availableTo(5L)).isTrue();
        assertThat(s.availableTo(6L)).isFalse();
        assertThat(settings(true, "").availableTo(6L)).isTrue();
        assertThat(settings(false, "").availableTo(6L)).isFalse();
    }

    @Test
    void costUsesPerMillionPrices() {
        // 1 萬輸入、500 輸出、3 萬快取讀取：0.001 + 0.00025 + 0.0003
        assertThat(settings(true, "").costUsd(10_000, 500, 30_000, 0)).isCloseTo(0.00155, org.assertj.core.data.Offset.offset(1e-9));
    }

    private static AssistantSettings settings(boolean enabled, String testers) {
        return new AssistantSettings(enabled, testers, "", "claude-haiku-5-5", 19, 20, 400, 6, 200,
                0.10, 0.50, 0.01, 0.125);
    }
}
