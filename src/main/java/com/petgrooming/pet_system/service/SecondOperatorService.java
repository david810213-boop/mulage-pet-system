package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.SecondOperatorCandidateView;
import com.petgrooming.pet_system.enums.PerformanceCategory;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PerformanceException;
import com.petgrooming.pet_system.model.AppointmentItem;
import com.petgrooming.pet_system.model.PerformanceRecord;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WalkInOrderItem;
import com.petgrooming.pet_system.repository.AppointmentItemRepository;
import com.petgrooming.pet_system.repository.PerformanceRecordRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalkInOrderItemRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事後補第二經手人：開單或補經手人時只選了一位，之後再補上第二位，兩人積分各半。
 *
 * 積分已經入帳的項目，把第一位那筆績效紀錄對半拆給第二位（與「拆給同事」同一套算法與防呆，
 * 拆過的不能再拆）；還沒入帳的（預約尚未結帳），只記下第二位，入帳時自動以雙人經手計算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecondOperatorService {

    /** 只列最近兩週的項目，太久以前的積分多半已月結，不在手機上改。 */
    public static final int CANDIDATE_DAYS = 14;

    private final AppointmentItemRepository appointmentItemRepository;
    private final WalkInOrderItemRepository walkInOrderItemRepository;
    private final PerformanceRecordRepository recordRepository;
    private final PerformanceSplitService performanceSplitService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SecondOperatorCandidateView> candidates(LocalDate today) {
        LocalDate from = today.minusDays(CANDIDATE_DAYS);
        List<SecondOperatorCandidateView> result = new ArrayList<>();
        for (AppointmentItem i : appointmentItemRepository.findSecondOperatorCandidates(from, PerformanceCategory.OTHER)) {
            result.add(new SecondOperatorCandidateView("A", i.getId(), i.getAppointment().getId(),
                    String.format("AP%03d", i.getAppointment().getId()), i.getAppointment().getPetName(),
                    i.getAppointment().getDate(), i.getPerformanceCategory().getLabel(), i.getItemName(),
                    i.getPoints(), i.getOperatorStaff().getId(), i.getOperatorStaff().getName()));
        }
        for (WalkInOrderItem i : walkInOrderItemRepository.findSecondOperatorCandidates(from.atStartOfDay(),
                PerformanceCategory.OTHER)) {
            result.add(new SecondOperatorCandidateView("W", i.getId(), i.getOrder().getId(),
                    "現場單#" + i.getOrder().getId(), i.getOrder().getPetName(),
                    i.getOrder().getCreatedAt().toLocalDate(), i.getPerformanceCategory().getLabel(), i.getItemName(),
                    i.getPoints(), i.getOperatorStaff().getId(), i.getOperatorStaff().getName()));
        }
        result.sort(Comparator.comparing(SecondOperatorCandidateView::date).reversed()
                .thenComparing(SecondOperatorCandidateView::orderId).thenComparing(SecondOperatorCandidateView::itemId));
        return result;
    }

    /** 預約項目補第二經手人，回傳第二位的姓名。 */
    @Transactional
    public String addToAppointmentItem(Long itemId, Long staffId) {
        AppointmentItem item = appointmentItemRepository.lockById(itemId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到項目 #" + itemId));
        User second = checkSecond(item.getOperatorStaff(), item.getOperatorStaff2(), item.getPoints(),
                item.getPerformanceCategory(), staffId);
        if (item.isPointsAwarded()) {
            Long apptId = item.getAppointment().getId();
            splitAwardedRecord(recordRepository.findByAppointmentId(apptId), item.getOperatorStaff(),
                    item.getPerformanceCategory(), item.getPoints(), "預約 #" + apptId + " - " + item.getItemName(),
                    second);
        }
        item.setOperatorStaff2(second);
        appointmentItemRepository.save(item);
        return second.getName();
    }

    /** 現場單項目補第二經手人，回傳第二位的姓名。 */
    @Transactional
    public String addToWalkInItem(Long itemId, Long staffId) {
        WalkInOrderItem item = walkInOrderItemRepository.lockById(itemId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到項目 #" + itemId));
        User second = checkSecond(item.getOperatorStaff(), item.getOperatorStaff2(), item.getPoints(),
                item.getPerformanceCategory(), staffId);
        if (item.isPointsAwarded()) {
            Long orderId = item.getOrder().getId();
            splitAwardedRecord(recordRepository.findByWalkInOrderId(orderId), item.getOperatorStaff(),
                    item.getPerformanceCategory(), item.getPoints(), "現場單 #" + orderId + " - " + item.getItemName(),
                    second);
        }
        item.setOperatorStaff2(second);
        walkInOrderItemRepository.save(item);
        return second.getName();
    }

    private User checkSecond(User first, User existingSecond, double points, PerformanceCategory category,
                             Long staffId) {
        if (first == null) {
            throw new PerformanceException("這個項目還沒有經手人，請在上方「待補經手人」一次選兩位");
        }
        if (existingSecond != null) {
            throw new PerformanceException("這個項目已經有第二經手人：" + existingSecond.getName());
        }
        if (points <= 0 || category == PerformanceCategory.OTHER) {
            throw new PerformanceException("這個項目沒有積分，不需要第二經手人");
        }
        if (staffId == null) {
            throw new PerformanceException("請選第二經手人");
        }
        if (staffId.equals(first.getId())) {
            throw new PerformanceException("兩位經手人不能是同一個人");
        }
        User second = userRepository.findById(staffId)
                .orElseThrow(() -> new PerformanceException(ErrorCode.NOT_FOUND, "找不到員工 #" + staffId));
        if (!second.isStaffOrAdmin()) {
            throw new PerformanceException("經手人必須是店內員工");
        }
        return second;
    }

    // 績效紀錄沒有存項目 id，以「同一張單、同一位、同分類、同積分、同備註」找出這個項目入帳時寫的那一筆。
    // 同一張單有兩個一模一樣的項目時，兩筆紀錄內容相同，拆哪一筆結果都一樣。
    private void splitAwardedRecord(List<PerformanceRecord> records, User first, PerformanceCategory category,
                                    double points, String note, User second) {
        PerformanceRecord source = records.stream()
                .filter(r -> r.getStaff().getId().equals(first.getId()))
                .filter(r -> r.getCategory() == category)
                .filter(r -> r.getSplitFromRecordId() == null)
                .filter(r -> r.getPoints() != null && Double.compare(r.getPoints(), points) == 0)
                .filter(r -> note.equals(r.getNote()))
                .filter(r -> !recordRepository.existsBySplitFromRecordId(r.getId()))
                .findFirst()
                .orElseThrow(() -> new PerformanceException(
                        "這個項目的積分已經拆給同事或調整過，不能再補第二位；需要調整請到「我的績效」查看"));
        performanceSplitService.splitRecord(source.getId(), second.getId(),
                "雙人經手（事後補第二位），與 " + first.getName() + " 各半");
        log.info("事後補第二經手人：{}，{} 與 {} 各半", note, first.getName(), second.getName());
    }
}
