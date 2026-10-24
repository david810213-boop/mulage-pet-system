package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 預約列表頁的篩選與統計（網頁版預約列表、員工手機版預約頁）。
 */
@Service
@RequiredArgsConstructor
public class AppointmentListService {

    private static final Comparator<AppointmentAdminResponse> BY_DATE_TIME =
            Comparator.comparing(AppointmentAdminResponse::getDate)
                    .thenComparing(AppointmentAdminResponse::getStartTime);
    /** 手機版各分頁最多列出的筆數，避免歷史資料多時一次渲染太多卡片。 */
    private static final int MOBILE_KEYWORD_LIMIT = 100;
    private static final int MOBILE_LIST_LIMIT = 150;
    private static final int MOBILE_CANCELLED_LIMIT = 50;
    private static final int MOBILE_PAST_DAYS = 60;
    private final AppointmentQueryService appointmentQueryService;
    private final DashboardService dashboardService;

    /** 網頁版預約列表的篩選條件；空白代表不限。付款狀態 PAID＝已付款，其他值＝未付款。 */
    public record Filter(LocalDate dateFrom, LocalDate dateTo, String status, String paidStatus, String keyword) {

        boolean matches(LocalDate date, AppointmentStatus appointmentStatus, boolean paid,
                        String code, Long id, String ownerName, String petName) {
            return (dateFrom == null || !date.isBefore(dateFrom))
                    && (dateTo == null || !date.isAfter(dateTo))
                    && (isBlank(status) || appointmentStatus.name().equals(status))
                    && (isBlank(paidStatus) || paidStatus.equals("PAID") == paid)
                    && (isBlank(keyword) || matchesKeyword(keyword.trim().toLowerCase(), code, id, ownerName, petName));
        }
    }

    /**
     * 網頁版預約列表頂部的統計卡片：一律以「今天」為基準計算，不受篩選條件影響。
     * 今日營收與首頁相同，是當日已付款的實收金額（預約＋現場單），見 {@link DashboardService#todayRevenue}。
     */
    public record StaffSummary(long todayCount, long completedCount, int todayRevenue, long pendingCount) {
    }

    public record StaffList(List<AppointmentAdminResponse> appointments, StaffSummary summary) {
    }

    /** 店家後台看完整資訊（含內部備註、確認狀態）。 */
    public StaffList forStaff(Filter filter, LocalDate today) {
        List<AppointmentAdminResponse> all = appointmentQueryService.getAllForAdmin();
        StaffSummary summary = new StaffSummary(
                all.stream().filter(a -> !a.isCancelled() && a.getDate().isEqual(today)).count(),
                all.stream().filter(a -> !a.isCancelled() && a.getDate().isEqual(today)
                        && a.getStatus() == AppointmentStatus.COMPLETED).count(),
                dashboardService.todayRevenue(today),
                all.stream().filter(a -> !a.isCancelled() && a.getStatus() == AppointmentStatus.PENDING_CONFIRM).count());
        List<AppointmentAdminResponse> filtered = all.stream()
                .filter(a -> filter.matches(a.getDate(), a.getStatus(), a.isPaid(),
                        a.getAppointmentCode(), a.getId(), a.getOwnerName(), a.getPetName()))
                .toList();
        return new StaffList(filtered, summary);
    }

    public List<AppointmentResponse> forCustomer(String username, Filter filter) {
        return appointmentQueryService.getMyAppointments(username).stream()
                .filter(a -> filter.matches(a.getDate(), a.getStatus(), a.isPaid(),
                        a.getAppointmentCode(), a.getId(), a.getOwnerName(), a.getPetName()))
                .toList();
    }

    /**
     * 員工手機版預約頁。
     *
     * @param picked     要列出的預約（已排序）
     * @param dayCounts  日期列：今天起 7 天，每天的預約數（不含已取消）
     */
    public record MobileList(List<AppointmentAdminResponse> picked, Map<LocalDate, Long> dayCounts,
                             long pendingCount, long servingCount) {
    }

    /**
     * 挑選順序：關鍵字（搜尋全部預約，新到舊最多 100 筆）優先，其次是指定日期（含已取消），
     * 最後才看分頁 f：upcoming 近期（預設）/ pending 待確認 / serving 服務中 / past 過去 60 天 / cancelled 已取消。
     */
    public MobileList forMobile(String f, LocalDate date, String keyword, LocalDate today) {
        List<AppointmentAdminResponse> all = appointmentQueryService.getAllForAdmin();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();

        List<AppointmentAdminResponse> picked;
        if (!kw.isEmpty()) {
            picked = all.stream()
                    .filter(a -> matchesKeyword(kw, a.getAppointmentCode(), a.getId(), a.getOwnerName(), a.getPetName()))
                    .sorted(BY_DATE_TIME.reversed()).limit(MOBILE_KEYWORD_LIMIT).toList();
        } else if (date != null) {
            picked = all.stream().filter(a -> a.getDate().isEqual(date)).sorted(BY_DATE_TIME).toList();
        } else {
            picked = switch (f) {
                case "pending" -> all.stream()
                        .filter(a -> isUpcomingPending(a, today))
                        .sorted(BY_DATE_TIME).toList();
                case "serving" -> all.stream()
                        .filter(AppointmentListService::isServing)
                        .sorted(BY_DATE_TIME).toList();
                case "past" -> all.stream()
                        .filter(a -> !a.isCancelled() && a.getDate().isBefore(today)
                                && !a.getDate().isBefore(today.minusDays(MOBILE_PAST_DAYS)))
                        .sorted(BY_DATE_TIME.reversed()).limit(MOBILE_LIST_LIMIT).toList();
                case "cancelled" -> all.stream()
                        .filter(AppointmentAdminResponse::isCancelled)
                        .sorted(BY_DATE_TIME.reversed()).limit(MOBILE_CANCELLED_LIMIT).toList();
                default -> all.stream()
                        .filter(a -> !a.isCancelled() && !a.getDate().isBefore(today))
                        .sorted(BY_DATE_TIME).limit(MOBILE_LIST_LIMIT).toList();
            };
        }

        Map<LocalDate, Long> dayCounts = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.plusDays(i);
            dayCounts.put(d, all.stream().filter(a -> !a.isCancelled() && a.getDate().isEqual(d)).count());
        }
        return new MobileList(picked, dayCounts,
                all.stream().filter(a -> isUpcomingPending(a, today)).count(),
                all.stream().filter(AppointmentListService::isServing).count());
    }

    private static boolean isUpcomingPending(AppointmentAdminResponse a, LocalDate today) {
        return !a.isCancelled() && a.getStatus() == AppointmentStatus.PENDING_CONFIRM && !a.getDate().isBefore(today);
    }

    private static boolean isServing(AppointmentAdminResponse a) {
        return !a.isCancelled() && a.getStatus() == AppointmentStatus.IN_PROGRESS;
    }

    /** 關鍵字（已轉小寫）比對預約編號、預約 ID（完全相同）、飼主名字、毛孩名字。 */
    private static boolean matchesKeyword(String kw, String code, Long id, String ownerName, String petName) {
        return (code != null && code.toLowerCase().contains(kw))
                || String.valueOf(id).equals(kw)
                || (ownerName != null && ownerName.toLowerCase().contains(kw))
                || (petName != null && petName.toLowerCase().contains(kw));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
