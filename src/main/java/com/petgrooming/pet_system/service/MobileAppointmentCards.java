package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.MobileAppointmentCard;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.GroomingItem;
import com.petgrooming.pet_system.service.MobileViewHelper;
import com.petgrooming.pet_system.service.WalkInOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 員工手機版預約畫面的顯示資料：預約卡片、流程階段（標籤與流程條位置）、日期與星期標籤、
 * 今日頁的「現在」紅線位置與進行中現場單。只做畫面顯示用的轉換，不含業務規則判斷。
 */
@Component
@RequiredArgsConstructor
public class MobileAppointmentCards {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    /** 今日頁最多列出的進行中現場單。 */
    private static final int OPEN_WALK_IN_LIMIT = 8;

    private final MobileViewHelper view;
    private final WalkInOrderService walkInOrderService;

    /** 「現在」紅線要插在第一筆開始時間晚於現在的卡片前面；全部都過了就放最後。 */
    public int nowIndex(List<MobileAppointmentCard> todayCards, LocalTime now) {
        for (int i = 0; i < todayCards.size(); i++) {
            if (LocalTime.parse(todayCards.get(i).getTime(), TIME_FMT).isAfter(now)) {
                return i;
            }
        }
        return todayCards.size();
    }

    /** 進行中（未結帳）的現場單，今日頁一併列出，方便接著做下一步。 */
    public List<Map<String, Object>> openWalkInCards() {
        return walkInOrderService.listAll().stream()
                .filter(o -> !o.isPaid())
                .limit(OPEN_WALK_IN_LIMIT)
                .map(o -> {
                    String title = o.getPetName() != null && !o.getPetName().isBlank() ? o.getPetName()
                            : o.getMemberName() != null ? o.getMemberName() : "非會員";
                    String stage = o.isPendingWireTransfer() ? "待對帳"
                            : !o.isRequiresServiceFlow() || o.isFinalCheckDone() ? "待結帳"
                            : !o.isServiceEndedDone() ? "服務中" : "待核對";
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("title", title);
                    m.put("initial", view.initial(title, "客"));
                    m.put("sub", (o.getMemberName() != null ? o.getMemberName() : "非會員") + "，現場單 #" + o.getId());
                    m.put("stageLabel", stage);
                    return m;
                })
                .toList();
    }

    /** 依日期分組（保留排序），今天、明天另外標註。 */
    public Map<String, List<MobileAppointmentCard>> groupByDate(List<AppointmentAdminResponse> picked, LocalDate today) {
        Map<String, List<MobileAppointmentCard>> groups = new LinkedHashMap<>();
        for (AppointmentAdminResponse a : picked) {
            String key = a.getDate().isEqual(today) ? "今天 " + dateLabel(a.getDate())
                    : a.getDate().isEqual(today.plusDays(1)) ? "明天 " + dateLabel(a.getDate())
                    : dateLabel(a.getDate());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(toCard(a, today));
        }
        return groups;
    }

    /** 日期列：今天起 7 天，每天的預約數。 */
    public List<Map<String, Object>> dayStrip(Map<LocalDate, Long> dayCounts, LocalDate today, LocalDate selected) {
        List<Map<String, Object>> days = new ArrayList<>();
        dayCounts.forEach((d, count) -> {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("iso", d.toString());
            day.put("week", d.equals(today) ? "今天" : weekdayLabel(d.getDayOfWeek()).substring(1));
            day.put("day", d.getDayOfMonth());
            day.put("count", count);
            day.put("on", d.equals(selected));
            days.add(day);
        });
        return days;
    }

    // 流程階段，判斷順序跟網頁版預約列表的按鈕顯示條件一致
    public String stageKey(AppointmentAdminResponse a) {
        if (a.isCancelled()) return "cancelled";
        if (a.isPaid()) return "done";
        if (a.isPendingWireTransfer()) return "wire";
        if (a.getStatus() == AppointmentStatus.PENDING_CONFIRM) return "confirm";
        if (a.getStatus() == AppointmentStatus.CONFIRMED) {
            return a.isCheckinOrderConfirmed() ? "start" : "checkin";
        }
        if (a.getStatus() == AppointmentStatus.IN_PROGRESS) {
            if (!a.isServiceEndedDone()) return "serving";
            if (!a.isFinalCheckDone()) return "check";
        }
        return "pay";
    }

    public String stageLabel(String stage) {
        return switch (stage) {
            case "cancelled" -> "已取消";
            case "done" -> "已結帳";
            case "wire" -> "待對帳";
            case "confirm" -> "待確認";
            case "checkin" -> "待開單";
            case "start" -> "待開始";
            case "serving" -> "服務中";
            case "check" -> "待核對";
            default -> "待結帳";
        };
    }

    // 詳情頁流程條：0 確認、1 開單、2 服務、3 核對、4 結帳、5 全部完成
    public int stepIndex(String stage) {
        return switch (stage) {
            case "confirm" -> 0;
            case "checkin" -> 1;
            case "start", "serving" -> 2;
            case "check" -> 3;
            case "pay", "wire" -> 4;
            case "done" -> 5;
            default -> -1;
        };
    }

    public String dateLabel(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth() + " " + weekdayLabel(d.getDayOfWeek());
    }

    public MobileAppointmentCard toCard(AppointmentAdminResponse a, LocalDate today) {
        String species = speciesKey(a.getPetType());
        String items;
        if (a.getDisplayItemNames() != null && !a.getDisplayItemNames().isEmpty()) {
            items = String.join("、", a.getDisplayItemNames());
        } else if (a.getSelectedItems() != null) {
            items = a.getSelectedItems().stream().map(GroomingItem::getName).collect(Collectors.joining("、"));
        } else {
            items = "";
        }
        String petName = a.getPetName() == null ? "" : a.getPetName();
        String stage = stageKey(a);
        return MobileAppointmentCard.builder()
                .id(a.getId())
                .dateLabel(dateLabel(a.getDate()))
                .time(a.getStartTime().format(TIME_FMT))
                .petName(petName)
                .petInitial(petName.isEmpty() ? "?" : petName.substring(0, petName.offsetByCodePoints(0, 1)))
                .species(species)
                .speciesLabel("dog".equals(species) ? "狗狗" : "cat".equals(species) ? "貓咪" : "其他")
                .items(items)
                .ownerName(a.getOwnerName())
                .state(stateKey(a))
                .statusLabel(a.getStatusLabel())
                .note(a.getInternalNote())
                .totalAmount(a.getTotalAmount())
                .quotePending(a.isFirstVisitAssessment() && !a.isCheckinOrderConfirmed())
                .pending(a.getStatus() == AppointmentStatus.PENDING_CONFIRM)
                .stage(stage)
                .stageLabel(stageLabel(stage))
                .build();
    }

    public String speciesKey(String petType) {
        return view.speciesKey(petType);
    }

    public String stateKey(AppointmentAdminResponse a) {
        if (a.getStatus() == AppointmentStatus.COMPLETED || a.isPaid()) {
            return "done";
        }
        if (a.getStatus() == AppointmentStatus.IN_PROGRESS) {
            return "live";
        }
        if (a.getStatus() == AppointmentStatus.PENDING_CONFIRM) {
            return "pending";
        }
        return "normal";
    }

    public String greeting(LocalTime now) {
        if (now.isBefore(LocalTime.of(11, 0))) {
            return "早安";
        }
        if (now.isBefore(LocalTime.of(17, 0))) {
            return "午安";
        }
        return "晚安";
    }

    public String weekdayLabel(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "週一";
            case TUESDAY -> "週二";
            case WEDNESDAY -> "週三";
            case THURSDAY -> "週四";
            case FRIDAY -> "週五";
            case SATURDAY -> "週六";
            case SUNDAY -> "週日";
        };
    }
}
