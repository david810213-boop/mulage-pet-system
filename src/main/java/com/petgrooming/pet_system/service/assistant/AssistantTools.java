package com.petgrooming.pet_system.service.assistant;

import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.DepositStatusResponse;
import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.model.ClosedDate;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.WeeklyClosureSetting;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.BookingDepositService;
import com.petgrooming.pet_system.service.ClosedDateService;
import com.petgrooming.pet_system.service.GroomingMenuFilter;
import com.petgrooming.pet_system.service.PetConsumptionHistoryService;
import com.petgrooming.pet_system.service.PetService;
import com.petgrooming.pet_system.service.WalletService;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 慕馬可以使用的查詢工具。全部唯讀，個人資料一律以登入的會員查詢，不接受模型指定其他人。
 * 回傳精簡的純文字（不是完整 JSON），減少送回模型的 token。
 */
@Component
@RequiredArgsConstructor
public class AssistantTools {

    private static final DateTimeFormatter MD_HM = DateTimeFormatter.ofPattern("M/d HH:mm");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_SLOT_DAYS = 7;
    private static final int MAX_MENU_ITEMS = 15;

    private final AppointmentQueryService appointmentQueryService;
    private final BookingDepositService bookingDepositService;
    private final WalletService walletService;
    private final PetService petService;
    private final ClosedDateService closedDateService;
    private final GroomingService groomingService;
    private final GroomingMenuFilter groomingMenuFilter;
    private final PetConsumptionHistoryService petConsumptionHistoryService;

    /** 給模型的工具定義（Claude Messages API 格式）。 */
    public List<Map<String, Object>> definitions() {
        return List.of(
                tool("get_my_appointments", "查詢這位顧客即將到來的預約（含狀態、項目、金額、訂金）與最近完成的服務", Map.of(), List.of()),
                tool("get_deposit_status", "查詢這位顧客某一天的訂金：應付、已付、尚需匯款、付款期限、是否核帳中",
                        Map.of("date", prop("string", "預約日期，格式 YYYY-MM-DD")), List.of("date")),
                tool("get_my_wallet", "查詢這位顧客的儲值金餘額與會員卡等級、到期日", Map.of(), List.of()),
                tool("get_my_pets", "查詢這位顧客登記的毛孩（名字、物種、品種、體重、年齡）", Map.of(), List.of()),
                tool("find_available_slots", "查詢某段日期每天還有名額的時段，最多 7 天；含公休日",
                        Map.of("start_date", prop("string", "起始日期，格式 YYYY-MM-DD"),
                                "days", prop("integer", "查幾天，1～7"),
                                "pet_type", prop("string", "DOG 或 CAT，選填")),
                        List.of("start_date")),
                tool("get_service_menu", "查詢這位顧客某隻毛孩線上可預約的服務項目與原價",
                        Map.of("pet_name", prop("string", "毛孩名字")), List.of("pet_name")),
                tool("get_closed_dates", "查詢固定公休日與接下來的臨時公休日", Map.of(), List.of()));
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> properties,
                                            List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return Map.of("name", name, "description", description, "input_schema", schema);
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    /** 執行工具，回傳給模型的文字。查不到或參數錯誤也回傳文字說明，不丟例外給模型。 */
    public String execute(String name, Map<String, Object> input, User user) {
        try {
            return switch (name) {
                case "get_my_appointments" -> appointments(user);
                case "get_deposit_status" -> deposit(user, date(input.get("date")));
                case "get_my_wallet" -> wallet(user);
                case "get_my_pets" -> pets(user);
                case "find_available_slots" -> slots(date(input.get("start_date")), intValue(input.get("days")),
                        str(input.get("pet_type")));
                case "get_service_menu" -> menu(user, str(input.get("pet_name")));
                case "get_closed_dates" -> closedDates();
                default -> "沒有這個工具";
            };
        } catch (DateTimeParseException e) {
            return "日期格式錯誤，請用 YYYY-MM-DD";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "查詢失敗：" + e.getMessage();
        }
    }

    private String appointments(User user) {
        LocalDate today = LocalDate.now();
        List<AppointmentResponse> all = appointmentQueryService.getMyAppointments(user.getUsername());
        List<AppointmentResponse> upcoming = all.stream()
                .filter(a -> !a.isCancelled() && !a.getDate().isBefore(today) && !a.isPaid())
                .sorted(Comparator.comparing(AppointmentResponse::getDate)
                        .thenComparing(AppointmentResponse::getStartTime))
                .limit(5)
                .toList();
        List<AppointmentResponse> recent = all.stream()
                .filter(AppointmentResponse::isPaid)
                .sorted(Comparator.comparing(AppointmentResponse::getDate).reversed())
                .limit(3)
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append(upcoming.isEmpty() ? "即將到來：沒有\n" : "即將到來：\n");
        for (AppointmentResponse a : upcoming) {
            sb.append("- ").append(a.getAppointmentCode()).append("｜").append(a.getPetName()).append("｜")
                    .append(day(a.getDate())).append(' ').append(a.getStartTime().format(HM))
                    .append("（按鈕日期 ").append(a.getDate()).append("）｜")
                    .append(a.getStatusLabel()).append("｜項目：")
                    .append(a.isFirstVisitAssessment() ? "初次・現場評估" : String.join("、", names(a)))
                    .append("｜金額：").append(a.isFirstVisitAssessment() ? "現場報價" : "$" + a.getTotalAmount());
            if (a.getDepositShare() > 0) {
                sb.append("｜訂金份額 $").append(a.getDepositShare()).append(" 已付 $").append(a.getDepositPaid());
                if (a.getDepositDueAt() != null && a.getDepositPaid() < a.getDepositShare()) {
                    sb.append(" 期限 ").append(a.getDepositDueAt().format(MD_HM));
                }
            }
            sb.append('\n');
        }
        sb.append(recent.isEmpty() ? "最近完成：沒有" : "最近完成：");
        for (AppointmentResponse a : recent) {
            sb.append("\n- ").append(a.getPetName()).append("｜").append(day(a.getDate())).append("｜")
                    .append(String.join("、", names(a)));
        }
        return sb.toString();
    }

    private static List<String> names(AppointmentResponse a) {
        return a.getDisplayItemNames() == null ? List.of() : a.getDisplayItemNames();
    }

    private String deposit(User user, LocalDate date) {
        DepositStatusResponse d = bookingDepositService.status(user.getUsername(), date);
        if (d.required() == 0) {
            return day(date) + "：不需要訂金";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(day(date)).append("：應付 $").append(d.required()).append("，已付 $").append(d.paid())
                .append("，尚需匯款 $").append(d.outstanding());
        if (d.dueAt() != null && d.outstanding() > 0) {
            sb.append("，付款期限 ").append(d.dueAt().format(MD_HM));
        }
        if (d.paymentPending()) {
            sb.append("，已回報匯款 $").append(d.pendingAmount()).append("、店家核帳中");
        }
        if (d.lastRejectReason() != null) {
            sb.append("，上次回報被退回：").append(d.lastRejectReason());
        }
        return sb.toString();
    }

    private String wallet(User user) {
        WalletResponse w = walletService.getWallet(user.getUsername());
        StringBuilder sb = new StringBuilder("儲值金餘額 $").append(w.getBalance() == null ? 0 : w.getBalance());
        if (w.getCardTierLabel() != null && !"無".equals(w.getCardTierLabel())) {
            sb.append("，會員卡 ").append(w.getCardTierLabel()).append(w.isCardActive() ? "（有效）" : "（已到期）");
            if (w.getCardExpiresAt() != null) {
                sb.append("，到期日 ").append(day(w.getCardExpiresAt()));
            }
        } else {
            sb.append("，尚未開卡");
        }
        return sb.toString();
    }

    private String pets(User user) {
        List<PetResponse> pets = petService.getMyPets(user.getUsername());
        if (pets.isEmpty()) {
            return "還沒有登記毛孩";
        }
        return pets.stream()
                .map(p -> "- " + p.getName() + "｜" + p.getPetTypeLabel() + "｜" + nvl(p.getBreed())
                        + "｜體重 " + (p.getWeight() == null ? "未填" : p.getWeight() + " kg")
                        + "｜" + (p.getAge() == null ? "年齡未填" : p.getAge() + " 歲"))
                .collect(Collectors.joining("\n"));
    }

    private String slots(LocalDate start, Integer days, String petType) {
        int n = days == null ? 3 : Math.max(1, Math.min(MAX_SLOT_DAYS, days));
        PetType type = parsePetType(petType);
        LocalDate earliest = AppointmentService.earliestOnlineBookingDate();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate d = start.plusDays(i);
            String label = day(d);
            if (d.isBefore(LocalDate.now())) {
                lines.add(label + "：已過");
                continue;
            }
            if (closedDateService.isClosed(d)) {
                lines.add(label + "：公休");
                continue;
            }
            List<TimeSlotResponse> slots = appointmentQueryService.getAvailableSlots(d, type);
            String times = slots.stream().filter(TimeSlotResponse::isAvailable)
                    .map(s -> s.getStartTime().format(HM)).collect(Collectors.joining("、"));
            lines.add(label + "：" + (times.isEmpty() ? "已額滿" : times)
                    + (d.isBefore(earliest) ? "（三日內，線上不能預約，請聯繫店家）" : ""));
        }
        return "查詢範圍 " + day(start) + " 起 " + n + " 天（這是查詢的天數，不是預約期限；線上最早可約 "
                + day(earliest) + "，之後的日期都能約）：\n" + String.join("\n", lines);
    }

    private String menu(User user, String petName) {
        PetResponse pet = petService.getMyPets(user.getUsername()).stream()
                .filter(p -> p.getName().equalsIgnoreCase(petName == null ? "" : petName.trim()))
                .findFirst().orElse(null);
        if (pet == null) {
            return "找不到這隻毛孩，請先用 get_my_pets 確認名字";
        }
        boolean existing = petConsumptionHistoryService.hasPriorPaidService(user.getId(), pet.getName(), null);
        if (!existing) {
            return pet.getName() + " 是第一次來：線上只選時段，到店後依狀況現場報價";
        }
        String type = pet.getPetType() == null ? null : pet.getPetType().name();
        List<GroomingItemResponse> items = groomingMenuFilter.filterForPetShape(
                groomingService.getBookableItems(), true, type, pet);
        StringBuilder sb = new StringBuilder(pet.getName()).append(" 線上可預約（原價，折扣結帳時自動計算）：");
        if (groomingMenuFilter.needsWeight(type, pet)) {
            sb.append("\n（還沒填體重，請先到毛孩檔案補上體重才看得到套餐）");
        }
        items.stream().limit(MAX_MENU_ITEMS).forEach(i -> sb.append("\n- ").append(i.getName()).append("：$")
                .append(i.getPrice() == null ? "現場報價" : String.valueOf(i.getPrice().intValue())));
        return sb.toString();
    }

    private String closedDates() {
        WeeklyClosureSetting weekly = closedDateService.getWeeklyClosureSetting();
        String fixed = Arrays.stream(DayOfWeek.values()).filter(weekly::isClosedOn)
                .map(AssistantTools::weekday).collect(Collectors.joining("、"));
        List<ClosedDate> upcoming = closedDateService.listUpcoming();
        String extra = upcoming.stream().limit(10)
                .map(c -> day(c.getDate()) + (c.getReason() == null || c.getReason().isBlank() ? "" : "（" + c.getReason() + "）"))
                .collect(Collectors.joining("、"));
        return "固定公休：" + (fixed.isEmpty() ? "無" : fixed) + "\n其他公休：" + (extra.isEmpty() ? "目前沒有" : extra);
    }

    private static PetType parsePetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PetType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 跟回答一樣用「11/24（二）」的格式，模型照抄就不會寫成 2026-11-24（週二）；不是今年才加年份。 */
    static String day(LocalDate d) {
        String md = (d.getYear() == LocalDate.now().getYear() ? "" : d.getYear() + "/")
                + d.getMonthValue() + "/" + d.getDayOfMonth();
        return md + "（" + weekday(d.getDayOfWeek()).substring(1) + "）";
    }

    private static String weekday(DayOfWeek d) {
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

    private static LocalDate date(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("缺少日期");
        }
        return LocalDate.parse(o.toString().trim());
    }

    private static Integer intValue(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return null;
        }
        try {
            return Integer.valueOf(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "品種未填" : s;
    }
}
