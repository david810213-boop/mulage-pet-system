package com.petgrooming.pet_system.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 需求（追加，2026-09-08）：既有資料 CSV 匯入的編碼容錯讀取。
 *
 * 背景：店家用 Windows Excel「另存 CSV」時常存成 Big5，不是 UTF-8。
 * 舊版三支 parseXxxCsv 直接用 new InputStreamReader(is, UTF_8) 硬讀，Java 遇到
 * 不能解碼的 Big5 位元組不會丟例外，會默默換成 U+FFFD（置換字元），造成中文
 * 欄位（物種、品種、注意事項…）整批變亂碼，寵物解析因此連環失敗，但純 ASCII
 * 的欄位（電話、體重、儲值餘額）卻正常匯入，症狀很容易被誤判成別的問題。
 *
 * 這支工具改成：
 * 1. 先看 BOM（UTF-8 / UTF-16）決定編碼並去掉 BOM 位元組
 * 2. 沒有 BOM 就用「嚴格模式」依序試 UTF-8 → Big5 → GBK，第一個能完整解碼
 *    （沒有任何非法位元組）的就採用
 * 3. 都失敗才退回 UTF-8 寬鬆模式（結果可能含 U+FFFD），並回報
 *    {@link Parsed#isLenientFallback()}=true，交給呼叫端逐列標記錯誤
 * 4. 去掉解碼後字串開頭殘留的 U+FEFF，避免 BOM 黏在第一欄名稱前面
 *
 * 不引入 juniversalchardet 等外部套件——嚴格解碼逐一嘗試已足夠判斷，也符合
 * 本專案「CSV 手刻、不加函式庫」的既有做法。
 */
public final class CsvImportFileReader {

    private CsvImportFileReader() {}

    /** BOM 不存在時依序嘗試的候選編碼。UTF-8 擺第一，正常 UTF-8 檔一定先命中。 */
    private static final Charset[] FALLBACK_CHARSETS = {
            StandardCharsets.UTF_8,
            charsetOrNull("Big5"),
            charsetOrNull("GBK"),
    };

    /** 讀取結果：解碼後全文、實際採用的編碼、以及是否落到寬鬆模式。 */
    public static final class Parsed {
        private final String text;
        private final Charset charset;
        private final boolean lenientFallback;

        Parsed(String text, Charset charset, boolean lenientFallback) {
            this.text = text;
            this.charset = charset;
            this.lenientFallback = lenientFallback;
        }

        public Charset getCharset() {
            return charset;
        }

        /** true 代表沒有任何候選編碼能乾淨解碼，已改用 UTF-8 寬鬆模式，內容可能有亂碼。 */
        public boolean isLenientFallback() {
            return lenientFallback;
        }

        /** 依 \r\n / \r / \n 切行；不做 trim，交由呼叫端處理。行為比照 BufferedReader.readLine()。 */
        public List<String> lines() {
            List<String> result = new ArrayList<>();
            int start = 0;
            int n = text.length();
            for (int i = 0; i < n; i++) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r') {
                    result.add(text.substring(start, i));
                    if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    start = i + 1;
                }
            }
            if (start < n) {
                result.add(text.substring(start));
            }
            return result;
        }
    }

    public static Parsed read(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();

        // 1. BOM 判定：有 BOM 就直接依 BOM 決定編碼，並跳過 BOM 位元組
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return decoded(bytes, 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return decoded(bytes, 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return decoded(bytes, 2, StandardCharsets.UTF_16BE);
        }

        // 2. 沒 BOM：嚴格模式依序試 UTF-8 → Big5 → GBK
        for (Charset cs : FALLBACK_CHARSETS) {
            if (cs == null) {
                continue;
            }
            String strict = tryStrictDecode(bytes, cs);
            if (strict != null) {
                return new Parsed(stripLeadingBom(strict), cs, false);
            }
        }

        // 3. 都不乾淨：退回 UTF-8 寬鬆模式（結果可能含 U+FFFD）
        String lenient = new String(bytes, StandardCharsets.UTF_8);
        return new Parsed(stripLeadingBom(lenient), StandardCharsets.UTF_8, true);
    }

    private static Parsed decoded(byte[] bytes, int offset, Charset charset) {
        String text = new String(bytes, offset, bytes.length - offset, charset);
        return new Parsed(stripLeadingBom(text), charset, false);
    }

    private static String tryStrictDecode(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /** U+FEFF：BOM 解碼後在字串裡的表現形式（零寬不換行空格）。 */
    private static final char BOM_CHAR = 0xFEFF;

    private static String stripLeadingBom(String s) {
        return (!s.isEmpty() && s.charAt(0) == BOM_CHAR) ? s.substring(1) : s;
    }

    private static Charset charsetOrNull(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }
}
