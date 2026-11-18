package com.petgrooming.pet_system.service.assistant;

import com.petgrooming.pet_system.config.AssistantSettings;
import com.petgrooming.pet_system.dto.AssistantMessageRequest;
import com.petgrooming.pet_system.dto.AssistantQuotaResponse;
import com.petgrooming.pet_system.dto.AssistantReplyResponse;
import com.petgrooming.pet_system.dto.AssistantTrialView;
import com.petgrooming.pet_system.exception.AssistantException;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.model.AssistantMessage;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AssistantMessageRepository;
import com.petgrooming.pet_system.service.assistant.LlmClient.LlmResult;
import com.petgrooming.pet_system.service.assistant.LlmClient.Usage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 慕馬 AI 小助手：檢查題數與預算 → 帶最近幾句對話與知識文件呼叫模型 → 模型需要時呼叫唯讀工具 → 存對話紀錄。
 * 呼叫模型可能要好幾秒，刻意不包在資料庫交易裡；工具各自走既有 service 的交易。設計見 docs/adr/0010。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    static final int MAX_TOOL_ROUNDS = 4;
    /** 本月花費到預算的這個比例就停止呼叫模型，留一點緩衝給已經送出的請求。 */
    static final double BUDGET_GUARD = 0.95;
    private static final int HISTORY_HOURS = 2;

    static final String LIMIT_TODAY = "今天的提問次數用完了，明天再來找慕馬，或直接聯絡店家喔！";
    static final String LIMIT_BUDGET = "慕馬這個月要休息一下了，有問題請直接聯絡店家喔！";
    static final String UNAVAILABLE = "慕馬現在有點忙，請稍後再試，或直接聯絡店家喔！";
    static final String EMPTY_ANSWER = "這個問題慕馬不太確定，幫你聯絡店家確認比較準喔！";

    private final AssistantSettings settings;
    private final LlmClient llmClient;
    private final AssistantTools tools;
    private final AssistantPrompt prompt;
    private final AssistantMessageRepository repository;

    public AssistantQuotaResponse quota(User user) {
        if (user == null || !settings.availableTo(user.getId())) {
            return new AssistantQuotaResponse(false, 0, settings.getDailyQuestionLimit());
        }
        return new AssistantQuotaResponse(true, remainingToday(user), settings.getDailyQuestionLimit());
    }

    public AssistantReplyResponse ask(User user, AssistantMessageRequest req) {
        if (user == null || !settings.availableTo(user.getId())) {
            throw new AssistantException(ErrorCode.FORBIDDEN, "慕馬目前還沒開放");
        }
        String message = req.message().trim();
        if (message.isEmpty()) {
            throw new AssistantException("請輸入問題");
        }
        if (message.length() > settings.getMaxInputChars()) {
            throw new AssistantException("問題請在 " + settings.getMaxInputChars() + " 字以內");
        }

        int remaining = remainingToday(user);
        if (remaining <= 0) {
            return fallback(user, message, req.page(), LIMIT_TODAY, "limit", 0);
        }
        double spent = repository.sumCostSince(LocalDate.now().withDayOfMonth(1).atStartOfDay());
        if (spent >= settings.getMonthlyBudgetUsd() * BUDGET_GUARD) {
            log.warn("[慕馬] 本月花費 ${} 已達預算上限，暫停回答", String.format("%.4f", spent));
            return fallback(user, message, req.page(), LIMIT_BUDGET, "limit", remaining);
        }

        List<Map<String, Object>> messages = history(user);
        messages.add(textMessage("user", message));

        Turn turn;
        try {
            turn = converse(user, messages, req.page());
        } catch (AssistantUnavailableException e) {
            log.warn("[慕馬] 呼叫失敗：{}", e.getMessage());
            return fallback(user, message, req.page(), UNAVAILABLE, "error", remaining);
        }
        save(user, AssistantMessage.ROLE_USER, message, req.page(), Usage.ZERO, 0, false);
        save(user, AssistantMessage.ROLE_ASSISTANT, turn.answer(), req.page(), turn.usage(), turn.cost(), false);
        return new AssistantReplyResponse(turn.text(), turn.actions(), turn.mood(), remaining - 1);
    }

    /**
     * 後台試問：店長以指定會員的身分問一題，用來在開放前用測試資料檢查回答。
     * 不看開關與測試名單（金鑰有設定就能用），不扣該會員的每日題數，只受每月預算限制；不帶前面的對話。
     */
    public AssistantTrialView trial(User member, String question) {
        String message = question == null ? "" : question.trim();
        if (message.isEmpty() || message.length() > settings.getMaxInputChars()) {
            throw new AssistantException("問題請在 1～" + settings.getMaxInputChars() + " 字之間");
        }
        double spent = repository.sumCostSince(LocalDate.now().withDayOfMonth(1).atStartOfDay());
        if (spent >= settings.getMonthlyBudgetUsd() * BUDGET_GUARD) {
            throw new AssistantException("本月花費已達預算上限，慕馬暫停回答");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(textMessage("user", message));
        Turn turn;
        try {
            turn = converse(member, messages, AssistantMessage.PAGE_TRIAL);
        } catch (AssistantUnavailableException e) {
            throw new AssistantException("呼叫失敗：" + e.getMessage());
        }
        save(member, AssistantMessage.ROLE_USER, message, AssistantMessage.PAGE_TRIAL, Usage.ZERO, 0, false);
        save(member, AssistantMessage.ROLE_ASSISTANT, turn.answer(), AssistantMessage.PAGE_TRIAL, turn.usage(),
                turn.cost(), false);
        return new AssistantTrialView(member.getName(), message, turn.text(), turn.actions(), turn.mood(),
                turn.toolsUsed(), turn.cost(), turn.usage().input() + turn.usage().cacheRead() + turn.usage().cacheWrite(),
                turn.usage().output());
    }

    /** 一次問答的結果：原始回答（含按鈕標記）、拆好的文字與按鈕、用量與用到的工具。 */
    private record Turn(String answer, String text, List<AssistantReplyResponse.Action> actions, String mood,
                        Usage usage, double cost, List<String> toolsUsed) {
    }

    /**
     * 呼叫模型；模型要查資料時執行工具再送回去，最多 MAX_TOOL_ROUNDS 輪。
     * 工具輪數用完或模型沒有給文字時，再呼叫一次並禁止使用工具，讓模型用已查到的資料回答，
     * 避免顧客只看到「不太確定」。
     */
    private Turn converse(User user, List<Map<String, Object>> messages, String page) {
        List<String> toolsUsed = new ArrayList<>();
        LlmResult result = call(user, messages, page, true);
        Usage usage = result.usage();
        for (int round = 1; round < MAX_TOOL_ROUNDS && isToolUse(result); round++) {
            addToolResults(messages, result, user, toolsUsed);
            result = call(user, messages, page, true);
            usage = usage.plus(result.usage());
        }
        String answer = text(result);
        // 輪數用完時就算有文字，也只是「我查一下」這類開場白，一樣要重問
        if (answer.isEmpty() || isToolUse(result)) {
            log.warn("[慕馬] 沒有完整回答，改為禁止工具再問一次：stop_reason={}，工具={}，內容={}",
                    result.stopReason(), toolsUsed, blockTypes(result));
            if (isToolUse(result)) {
                addToolResults(messages, result, user, toolsUsed);
            } else if (!result.content().isEmpty()) {
                messages.add(Map.of("role", "assistant", "content", result.content()));
                messages.add(textMessage("user", "請根據上面查到的資料，直接用文字回答顧客的問題。"));
            }
            LlmResult retry = call(user, messages, page, false);
            usage = usage.plus(retry.usage());
            answer = text(retry);
            if (answer.isEmpty()) {
                log.warn("[慕馬] 重問仍沒有文字回答：stop_reason={}，內容={}", retry.stopReason(), blockTypes(retry));
            }
        }
        AssistantActions.Parsed parsed = AssistantActions.parse(answer);
        String text = parsed.text();
        List<AssistantReplyResponse.Action> actions = parsed.actions();
        if (text.isEmpty()) {
            text = EMPTY_ANSWER;
            actions = List.of(AssistantActions.contact());
        }
        double cost = settings.costUsd(usage.input(), usage.output(), usage.cacheRead(), usage.cacheWrite());
        String mood = actions.stream().anyMatch(a -> AssistantActions.CONTACT.equals(a.type())) ? "unsure" : "answer";
        // 存進對話紀錄的內容不能是空的：下一題會帶著這段對話送出，API 不接受空白的回答
        String saved = answer.isEmpty() ? text : answer;
        return new Turn(saved, text, actions, mood, usage, cost, toolsUsed);
    }

    private int remainingToday(User user) {
        long used = repository.countAnsweredSince(user.getId(), LocalDate.now().atStartOfDay());
        return (int) Math.max(0, settings.getDailyQuestionLimit() - used);
    }

    /** 最近兩小時內的對話（舊的在前），一定從顧客的提問開始。 */
    private List<Map<String, Object>> history(User user) {
        List<AssistantMessage> recent = new ArrayList<>(repository.findRecent(user.getId(),
                LocalDateTime.now().minusHours(HISTORY_HOURS), PageRequest.of(0, settings.getHistoryMessages())));
        Collections.reverse(recent);
        while (!recent.isEmpty() && !AssistantMessage.ROLE_USER.equals(recent.get(0).getRole())) {
            recent.remove(0);
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AssistantMessage m : recent) {
            messages.add(textMessage(AssistantMessage.ROLE_USER.equals(m.getRole()) ? "user" : "assistant",
                    m.getContent()));
        }
        // 最後一句若是顧客（上次沒得到回答），併掉避免連續兩則 user
        if (!messages.isEmpty() && "user".equals(messages.get(messages.size() - 1).get("role"))) {
            messages.remove(messages.size() - 1);
        }
        return messages;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runTools(List<Map<String, Object>> content, User user,
                                               List<String> toolsUsed) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> block : content) {
            if (!"tool_use".equals(block.get("type"))) {
                continue;
            }
            Object input = block.get("input");
            Map<String, Object> args = input instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            String name = String.valueOf(block.get("name"));
            toolsUsed.add(name);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "tool_result");
            result.put("tool_use_id", block.get("id"));
            result.put("content", tools.execute(name, args, user));
            results.add(result);
        }
        return results;
    }

    private LlmResult call(User user, List<Map<String, Object>> messages, String page, boolean allowTools) {
        return llmClient.send(prompt.cachedSystem(),
                prompt.dynamicSystem(LocalDateTime.now(), user.getName(), page),
                tools.definitions(), messages, allowTools);
    }

    /** 模型要求呼叫工具（含被 max_tokens 截斷但已經寫出工具呼叫的情況）。 */
    private static boolean isToolUse(LlmResult result) {
        return "tool_use".equals(result.stopReason())
                || result.content().stream().anyMatch(b -> "tool_use".equals(b.get("type")));
    }

    private void addToolResults(List<Map<String, Object>> messages, LlmResult result, User user,
                                List<String> toolsUsed) {
        messages.add(Map.of("role", "assistant", "content", result.content()));
        messages.add(Map.of("role", "user", "content", runTools(result.content(), user, toolsUsed)));
    }

    private static List<Object> blockTypes(LlmResult result) {
        return result.content().stream().map(b -> b.get("type")).toList();
    }

    private static String text(LlmResult result) {
        if (result == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> block : result.content()) {
            if ("text".equals(block.get("type")) && block.get("text") != null) {
                sb.append(block.get("text"));
            }
        }
        return sb.toString().trim();
    }

    private static Map<String, Object> textMessage(String role, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", text);
        return m;
    }

    private AssistantReplyResponse fallback(User user, String message, String page, String reply, String mood,
                                            int remaining) {
        save(user, AssistantMessage.ROLE_USER, message, page, Usage.ZERO, 0, true);
        save(user, AssistantMessage.ROLE_ASSISTANT, reply, page, Usage.ZERO, 0, true);
        return new AssistantReplyResponse(reply, List.of(AssistantActions.contact()), mood, remaining);
    }

    private void save(User user, String role, String content, String page, Usage usage, double cost,
                      boolean fallback) {
        repository.save(AssistantMessage.builder()
                .user(user)
                .role(role)
                .content(content)
                .page(page == null ? null : page.length() > 40 ? page.substring(0, 40) : page)
                .inputTokens(usage.input())
                .outputTokens(usage.output())
                .cacheReadTokens(usage.cacheRead())
                .cacheWriteTokens(usage.cacheWrite())
                .costUsd(cost)
                .fallback(fallback)
                .build());
    }
}
