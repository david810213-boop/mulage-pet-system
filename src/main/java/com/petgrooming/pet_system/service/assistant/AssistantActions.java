package com.petgrooming.pet_system.service.assistant;

import com.petgrooming.pet_system.dto.AssistantReplyResponse.Action;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析模型回答裡的按鈕標記 [[按鈕:代碼]]，轉成前端按鈕，並從文字移除。
 * 只接受白名單內的代碼，網址一律由系統產生，模型無法指定任意連結。
 */
final class AssistantActions {

    static final String CONTACT = "contact";

    private static final Pattern TAG = Pattern.compile("\\[\\[按鈕:([a-z_]+)(?::([0-9-]+))?]]");
    private static final int MAX_ACTIONS = 2;

    private AssistantActions() {
    }

    record Parsed(String text, List<Action> actions) {
        Parsed {
            actions = List.copyOf(actions);
        }
    }

    static Parsed parse(String raw) {
        String source = raw == null ? "" : raw;
        Matcher m = TAG.matcher(source);
        Set<String> seen = new LinkedHashSet<>();
        List<Action> actions = new ArrayList<>();
        int firstTag = -1;
        while (m.find()) {
            if (firstTag < 0) {
                firstTag = m.start();
            }
            Action a = toAction(m.group(1), m.group(2));
            if (a != null && actions.size() < MAX_ACTIONS && seen.add(a.url())) {
                actions.add(a);
            }
        }
        // 按鈕標記應該在最後；模型有時會在標記後面補上按鈕名稱，第一個標記之後的字一律不顯示
        String text = firstTag < 0 ? source : source.substring(0, firstTag);
        // 對話面板只顯示純文字，模型偶爾寫的 Markdown 粗體要拿掉
        String plain = text.replace("**", "").replace("__", "");
        return new Parsed(plain.replaceAll("[ \\t]+\\n", "\n").trim(), actions);
    }

    static Action contact() {
        return new Action("聯絡店家（真人）", "https://line.me/R/ti/p/@108dbaxm", CONTACT);
    }

    private static Action toAction(String code, String arg) {
        return switch (code) {
            case "booking" -> new Action("去預約", "/liff/booking.html", code);
            case "appointments" -> new Action("我的預約", "/liff/my-appointments.html", code);
            case "deposit" -> validDate(arg)
                    ? new Action("打開匯款資訊", "/liff/my-appointments.html?deposit=" + arg, code)
                    : new Action("我的預約", "/liff/my-appointments.html", "appointments");
            case "wallet" -> new Action("會員錢包", "/liff/wallet.html", code);
            case "pets" -> new Action("毛孩檔案", "/liff/my-pets.html", code);
            case "profile" -> new Action("編輯資料", "/liff/my-profile.html", code);
            case "contract" -> new Action("契約全文", "/liff/contract.html", code);
            case CONTACT -> contact();
            default -> null;
        };
    }

    private static boolean validDate(String s) {
        if (s == null) {
            return false;
        }
        try {
            LocalDate.parse(s);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
