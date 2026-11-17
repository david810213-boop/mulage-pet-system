package com.petgrooming.pet_system.service.assistant;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 慕馬的系統提示。固定部分（角色、規則、知識文件）每次相同、會被快取；
 * 變動部分（現在時間、顧客姓名、所在頁面）放在快取之後，避免每次都讓快取失效。
 */
@Component
public class AssistantPrompt {

    private static final DateTimeFormatter NOW = DateTimeFormatter.ofPattern("yyyy-MM-dd（E）HH:mm", Locale.TAIWAN);

    private static final String RULES = """
            你是「慕馬」，慕沐村寵物美容（Mulage Pet）LINE 會員頁裡的小助手，是一隻戴草帽的羊駝。
            顧客已經登入，你在回答這位顧客關於預約、訂金、價目、儲值與會員、毛孩資料的問題。

            回答規則：
            - 一律使用繁體中文，語氣溫暖親切、像店員，可以稱呼顧客和毛孩的名字，偶爾加一個表情符號，不要過度裝可愛
            - 回答要短：1～3 句、80 字以內；顧客要求詳細說明時才多講。不要重複顧客的問題，不要客套結尾
            - 規則只依照下方知識文件；價目、空位、公休、顧客自己的預約、訂金、餘額、毛孩，一律先用工具查，不要猜
            - 查不到或不確定時，直接說不確定，並附上 [[按鈕:contact]] 請顧客聯絡店家
            - 你不能幫顧客預約、取消、改期、付款或修改資料，只能用按鈕帶顧客到對應頁面
            - 不提供毛孩健康、疾病、用藥建議；不承諾報價、規則以外的優惠或例外
            - 與寵物美容無關的問題，簡短婉拒並把話題帶回來
            - 日期時間用「11/24（二）15:30」這種格式；金額用「$1,000」

            按鈕：需要時在回答最後加上按鈕標記（最多 2 個），系統會轉成按鈕，不要寫成網址：
            [[按鈕:booking]] 去預約　[[按鈕:appointments]] 我的預約　[[按鈕:deposit:YYYY-MM-DD]] 某天的匯款資訊
            [[按鈕:wallet]] 會員錢包　[[按鈕:pets]] 毛孩檔案　[[按鈕:profile]] 編輯資料
            [[按鈕:contract]] 契約全文　[[按鈕:contact]] 聯絡店家（真人）

            以下是知識文件：

            """;

    private final String cachedSystem;

    public AssistantPrompt() {
        this.cachedSystem = RULES + loadKnowledge();
    }

    private static String loadKnowledge() {
        try (InputStream in = new ClassPathResource("assistant/knowledge.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("讀不到慕馬知識文件 assistant/knowledge.md", e);
        }
    }

    public String cachedSystem() {
        return cachedSystem;
    }

    public String dynamicSystem(LocalDateTime now, String customerName, String page) {
        return "現在時間：" + now.format(NOW) + "\n顧客：" + (customerName == null || customerName.isBlank() ? "會員" : customerName)
                + "\n顧客目前所在頁面：" + pageLabel(page);
    }

    private static String pageLabel(String page) {
        if (page == null) {
            return "未知";
        }
        return switch (page) {
            case "index" -> "首頁";
            case "booking" -> "預約頁";
            case "my-appointments" -> "我的預約";
            case "wallet" -> "會員錢包";
            case "my-pets" -> "毛孩檔案";
            case "add-pet" -> "新增／編輯毛孩";
            case "my-profile" -> "編輯資料";
            default -> "其他";
        };
    }
}
