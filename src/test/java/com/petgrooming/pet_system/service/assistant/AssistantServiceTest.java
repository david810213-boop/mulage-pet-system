package com.petgrooming.pet_system.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.petgrooming.pet_system.dto.AssistantMessageRequest;
import com.petgrooming.pet_system.dto.AssistantReplyResponse;
import com.petgrooming.pet_system.dto.AssistantTrialView;
import com.petgrooming.pet_system.exception.AssistantException;
import com.petgrooming.pet_system.model.AssistantMessage;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.repository.AssistantMessageRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * 慕馬：回答與按鈕、工具只查登入者自己的資料、每日題數、每月預算、呼叫失敗的備用回覆。
 * 模型換成假的實作，不會真的呼叫 Claude API。
 */
@TestPropertySource(properties = {"assistant.enabled=true", "assistant.daily-question-limit=3"})
class AssistantServiceTest extends IntegrationTestBase {

    @Autowired private AssistantService assistantService;
    @Autowired private AssistantMessageRepository messageRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TestData testData;

    @AfterEach
    void reset() {
        FakeLlm.SCRIPT.clear();
        FakeLlm.CALLS.clear();
        FakeLlm.ALLOW_TOOLS.clear();
    }

    @Test
    void answerWithButtonsCountsTowardDailyQuota() {
        User user = testData.customer();
        FakeLlm.reply(c -> FakeLlm.text("Daiwei 你好～可以直接到預約頁喔！[[按鈕:booking]][[按鈕:evil]]"));

        AssistantReplyResponse reply = ask(user, "怎麼預約？");

        assertThat(reply.text()).isEqualTo("Daiwei 你好～可以直接到預約頁喔！");
        assertThat(reply.actions()).extracting(AssistantReplyResponse.Action::url).containsExactly("/liff/booking.html");
        assertThat(reply.mood()).isEqualTo("answer");
        assertThat(reply.remaining()).isEqualTo(2);
        assertThat(assistantService.quota(user).remaining()).isEqualTo(2);
    }

    @Test
    void toolsOnlySeeTheLoggedInCustomer() {
        User me = testData.customer();
        User other = testData.customer();
        walletRepository.save(Wallet.builder().user(me).balance(5200).build());
        walletRepository.save(Wallet.builder().user(other).balance(98_765).build());
        FakeLlm.reply(c -> FakeLlm.toolUse("toolu_1", "get_my_wallet", Map.of()));
        FakeLlm.reply(c -> FakeLlm.text("你的儲值金還有 $5,200 喔！"));

        ask(me, "我還有多少儲值金？");

        String toolResult = FakeLlm.lastToolResult();
        assertThat(toolResult).contains("5200").doesNotContain("98765");
    }

    @Test
    void dailyLimitStopsCallingTheModel() {
        User user = testData.customer();
        for (int i = 0; i < 3; i++) {
            FakeLlm.reply(c -> FakeLlm.text("好的"));
            ask(user, "問題 " + i);
        }
        int calls = FakeLlm.CALLS.size();

        AssistantReplyResponse reply = ask(user, "第四題");

        assertThat(reply.mood()).isEqualTo("limit");
        assertThat(reply.remaining()).isZero();
        assertThat(FakeLlm.CALLS).hasSize(calls);
    }

    @Test
    void monthlyBudgetStopsCallingTheModel() {
        User user = testData.customer();
        AssistantMessage expensive = messageRepository.save(AssistantMessage.builder()
                .user(testData.customer()).role(AssistantMessage.ROLE_ASSISTANT).content("x").costUsd(1000).build());
        try {
            AssistantReplyResponse reply = ask(user, "還有空位嗎？");

            assertThat(reply.mood()).isEqualTo("limit");
            assertThat(reply.actions()).extracting(AssistantReplyResponse.Action::type).containsExactly("contact");
            assertThat(FakeLlm.CALLS).isEmpty();
        } finally {
            messageRepository.delete(expensive);
        }
    }

    @Test
    void failureGivesFriendlyReplyWithoutUsingQuota() {
        User user = testData.customer();
        FakeLlm.reply(c -> {
            throw new AssistantUnavailableException("timeout");
        });

        AssistantReplyResponse reply = ask(user, "在嗎？");

        assertThat(reply.mood()).isEqualTo("error");
        assertThat(reply.text()).isEqualTo(AssistantService.UNAVAILABLE);
        assertThat(assistantService.quota(user).remaining()).isEqualTo(3);
    }

    @Test
    void toolRoundsUsedUpStillGivesTextAnswer() {
        User user = testData.customer();
        for (int i = 0; i < AssistantService.MAX_TOOL_ROUNDS; i++) {
            String id = "toolu_" + i;
            FakeLlm.reply(c -> FakeLlm.toolUse(id, "get_closed_dates", Map.of()));
        }
        FakeLlm.reply(c -> FakeLlm.text("週四、週五公休喔！"));

        AssistantReplyResponse reply = ask(user, "哪天公休？");

        assertThat(reply.text()).isEqualTo("週四、週五公休喔！");
        assertThat(reply.mood()).isEqualTo("answer");
        assertThat(FakeLlm.ALLOW_TOOLS).hasSize(AssistantService.MAX_TOOL_ROUNDS + 1).last().isEqualTo(false);
    }

    @Test
    void emptyAnswerIsNotSavedBlankForTheNextQuestion() {
        User user = testData.customer();
        FakeLlm.reply(c -> new LlmClient.LlmResult(List.of(), "max_tokens", new LlmClient.Usage(1000, 400, 0, 0)));
        FakeLlm.reply(c -> new LlmClient.LlmResult(List.of(), "end_turn", new LlmClient.Usage(1000, 0, 0, 0)));

        AssistantReplyResponse first = ask(user, "營業時間？");
        FakeLlm.reply(c -> FakeLlm.text("11:00～19:30 喔"));
        ask(user, "那營業時間呢？");

        assertThat(first.text()).isEqualTo(AssistantService.EMPTY_ANSWER);
        List<Map<String, Object>> sent = FakeLlm.CALLS.get(FakeLlm.CALLS.size() - 1);
        assertThat(sent).extracting(m -> m.get("content")).doesNotContain("");
    }

    @Test
    void historyIsSentOnTheNextQuestion() {
        User user = testData.customer();
        FakeLlm.reply(c -> FakeLlm.text("吉米 11/24 要付 $1,000 訂金喔"));
        ask(user, "吉米的訂金多少？");
        FakeLlm.reply(c -> FakeLlm.text("72 小時前取消全額退"));

        ask(user, "那取消呢？");

        List<Map<String, Object>> sent = FakeLlm.CALLS.get(FakeLlm.CALLS.size() - 1);
        assertThat(sent).extracting(m -> m.get("role")).containsExactly("user", "assistant", "user");
        assertThat(sent.get(0).get("content")).isEqualTo("吉米的訂金多少？");
    }

    @Test
    void staffTrialUsesMemberDataWithoutUsingQuotaOrHistory() {
        User member = testData.customer();
        walletRepository.save(Wallet.builder().user(member).balance(3100).build());
        FakeLlm.reply(c -> FakeLlm.toolUse("toolu_t", "get_my_wallet", Map.of()));
        FakeLlm.reply(c -> FakeLlm.text("還有 $3,100 喔！[[按鈕:wallet]]"));

        AssistantTrialView trial = assistantService.trial(member, "我還有多少儲值金？");

        assertThat(trial.text()).isEqualTo("還有 $3,100 喔！");
        assertThat(trial.toolsUsed()).containsExactly("get_my_wallet");
        assertThat(FakeLlm.lastToolResult()).contains("3100");
        assertThat(assistantService.quota(member).remaining()).isEqualTo(3);

        FakeLlm.reply(c -> FakeLlm.text("好的"));
        ask(member, "謝謝");
        assertThat(FakeLlm.CALLS.get(FakeLlm.CALLS.size() - 1)).hasSize(1);
    }

    @Test
    void tooLongQuestionIsRejected() {
        User user = testData.customer();
        assertThatThrownBy(() -> ask(user, "問".repeat(201))).isInstanceOf(AssistantException.class);
    }

    private AssistantReplyResponse ask(User user, String message) {
        return assistantService.ask(user, new AssistantMessageRequest(message, "index"));
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlmClient() {
            return new FakeLlm();
        }
    }

    /** 依序回放事先排好的回應，並記下每次送出的對話。 */
    static class FakeLlm implements LlmClient {
        static final Deque<Function<List<Map<String, Object>>, LlmResult>> SCRIPT = new ArrayDeque<>();
        static final List<List<Map<String, Object>>> CALLS = new ArrayList<>();
        static final List<Boolean> ALLOW_TOOLS = new ArrayList<>();

        static void reply(Function<List<Map<String, Object>>, LlmResult> step) {
            SCRIPT.add(step);
        }

        static LlmResult text(String text) {
            return new LlmResult(List.of(Map.of("type", "text", "text", text)), "end_turn", new Usage(1000, 50, 3000, 0));
        }

        static LlmResult toolUse(String id, String name, Map<String, Object> input) {
            return new LlmResult(List.of(Map.of("type", "tool_use", "id", id, "name", name, "input", input)),
                    "tool_use", new Usage(1000, 30, 3000, 0));
        }

        @SuppressWarnings("unchecked")
        static String lastToolResult() {
            List<Map<String, Object>> last = CALLS.get(CALLS.size() - 1);
            Map<String, Object> msg = last.get(last.size() - 1);
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) msg.get("content");
            return String.valueOf(blocks.get(0).get("content"));
        }

        @Override
        public LlmResult send(String cachedSystem, String dynamicSystem, List<Map<String, Object>> tools,
                              List<Map<String, Object>> messages, boolean allowTools) {
            CALLS.add(List.copyOf(messages));
            ALLOW_TOOLS.add(allowTools);
            Function<List<Map<String, Object>>, LlmResult> step = SCRIPT.poll();
            if (step == null) {
                return text("（沒有排定回應）");
            }
            return step.apply(messages);
        }
    }
}
