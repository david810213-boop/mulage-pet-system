package com.petgrooming.pet_system.service.assistant;

import com.petgrooming.pet_system.config.AssistantSettings;
import com.petgrooming.pet_system.dto.AssistantLogView;
import com.petgrooming.pet_system.dto.AssistantUsageView;
import com.petgrooming.pet_system.model.AssistantMessage;
import com.petgrooming.pet_system.repository.AssistantMessageRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 後台抽查慕馬的對話與本月花費。 */
@Service
@RequiredArgsConstructor
public class AssistantReviewService {

    private static final int REVIEW_DAYS = 14;
    private static final int REVIEW_LIMIT = 300;

    private final AssistantSettings settings;
    private final AssistantMessageRepository repository;

    @Transactional(readOnly = true)
    public List<AssistantLogView> recentLogs() {
        return repository.findForReview(LocalDate.now().minusDays(REVIEW_DAYS).atStartOfDay(),
                        PageRequest.of(0, REVIEW_LIMIT)).stream()
                .map(m -> new AssistantLogView(m.getCreatedAt(), m.getUser().getName(),
                        AssistantMessage.ROLE_USER.equals(m.getRole()), m.getContent(), m.getPage(),
                        m.getCostUsd(), m.isFallback()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssistantUsageView usage() {
        LocalDate today = LocalDate.now();
        return new AssistantUsageView(settings.isEnabled(),
                repository.sumCostSince(today.withDayOfMonth(1).atStartOfDay()),
                settings.getMonthlyBudgetUsd(),
                repository.countQuestionsSince(today.atStartOfDay()),
                repository.countQuestionsSince(today.withDayOfMonth(1).atStartOfDay()));
    }
}
