package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.ConsumptionImportRow;
import com.petgrooming.pet_system.dto.MemberImportRow;
import com.petgrooming.pet_system.dto.WalletImportRow;
import com.petgrooming.pet_system.utils.CsvImportFileReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

/**
 * 既有會員資料匯入的 CSV 解析（會員＋寵物、儲值餘額、消費紀錄三種檔案）。
 * 編碼交給 CsvImportFileReader 偵測（UTF-8 BOM／無 BOM 時嚴格試 UTF-8→Big5→GBK），不假設一定是 UTF-8。
 */
@Slf4j
@Component
public class MemberCsvParser {

    // ── CSV 解析 ──────────────────────────────────────────────────────────
    // 極簡手刻解析，不依賴額外套件（專案目前沒有 CSV 函式庫的依賴）。
    // 欄位順序固定：家長姓名,電話,毛孩名字,物種,品種,體重,年齡,是否分離焦慮,注意事項
    // 不支援欄位值裡包含逗號（例如注意事項寫「怕生,咬人」會被誤判成兩欄）——
    // 這是簡化實作的已知限制，交付時要在後台頁面上明確提醒店家避免在內容裡打逗號。
    //
    // 編碼：交給 CsvImportFileReader 偵測（UTF-8 BOM / 無 BOM 時嚴格試 UTF-8→Big5→GBK），
    // 不再假設檔案一定是 UTF-8；BOM 也在那邊一併去掉，避免黏在第一個欄位值前面。
    public List<MemberImportRow> parseCsv(MultipartFile file) throws IOException {
        List<MemberImportRow> rows = new ArrayList<>();
        CsvImportFileReader.Parsed parsed = CsvImportFileReader.read(file);
        logDetectedCharset("會員資料", parsed);

        List<String> lines = parsed.lines();
        int rowNumber = 0;
        for (int i = 1; i < lines.size(); i++) { // i=0 是表頭，跳過不處理
            String line = lines.get(i);
            if (line.isBlank()) continue;
            rowNumber++;
            String[] cols = line.split(",", -1);
            MemberImportRow row = new MemberImportRow();
            row.setRowNumber(rowNumber);
            row.setOwnerName(col(cols, 0));
            row.setPhone(col(cols, 1));
            row.setPetName(col(cols, 2));
            row.setPetTypeRaw(col(cols, 3));
            row.setBreed(col(cols, 4));
            row.setWeightRaw(col(cols, 5));
            row.setAgeRaw(col(cols, 6));
            row.setSeparationAnxietyRaw(col(cols, 7));
            row.setNotes(col(cols, 8));
            rows.add(row);
        }
        return rows;
    }

    // 讀檔時把偵測到的編碼寫進 log，方便店家上傳出問題時對照排查。
    private void logDetectedCharset(String label, CsvImportFileReader.Parsed parsed) {
        if (parsed.isLenientFallback()) {
            log.warn("[{}匯入] 無法確定 CSV 檔案編碼，已改用 UTF-8 寬鬆模式讀取，內容可能有亂碼，"
                    + "請確認原始檔另存為 UTF-8 或 Big5", label);
        } else {
            log.info("[{}匯入] 偵測到 CSV 檔案編碼：{}", label, parsed.getCharset().displayName());
        }
    }

    private String col(String[] cols, int idx) {
        return idx < cols.length ? cols[idx].trim() : "";
    }

    // 需求（追加）：儲值餘額 CSV 解析。欄位順序：電話,儲值餘額,會員等級,到期日（後兩欄選填）
    // 編碼同 parseCsv，交給 CsvImportFileReader 偵測，不假設一定是 UTF-8。
    public List<WalletImportRow> parseWalletCsv(MultipartFile file) throws IOException {
        List<WalletImportRow> rows = new ArrayList<>();
        CsvImportFileReader.Parsed parsed = CsvImportFileReader.read(file);
        logDetectedCharset("儲值餘額", parsed);

        List<String> lines = parsed.lines();
        int rowNumber = 0;
        for (int i = 1; i < lines.size(); i++) { // i=0 是表頭，跳過
            String line = lines.get(i);
            if (line.isBlank()) continue;
            rowNumber++;
            String[] cols = line.split(",", -1);
            WalletImportRow row = new WalletImportRow();
            row.setRowNumber(rowNumber);
            row.setPhone(col(cols, 0));
            row.setBalanceRaw(col(cols, 1));
            row.setTierRaw(col(cols, 2));
            row.setExpiresRaw(col(cols, 3));
            rows.add(row);
        }
        return rows;
    }

    // 需求（追加）：消費紀錄 CSV 解析。欄位順序：電話,毛孩名字,消費日期,金額,備註
    // 編碼同 parseCsv，交給 CsvImportFileReader 偵測，不假設一定是 UTF-8。
    public List<ConsumptionImportRow> parseConsumptionCsv(MultipartFile file) throws IOException {
        List<ConsumptionImportRow> rows = new ArrayList<>();
        CsvImportFileReader.Parsed parsed = CsvImportFileReader.read(file);
        logDetectedCharset("消費紀錄", parsed);

        List<String> lines = parsed.lines();
        int rowNumber = 0;
        for (int i = 1; i < lines.size(); i++) { // i=0 是表頭，跳過
            String line = lines.get(i);
            if (line.isBlank()) continue;
            rowNumber++;
            String[] cols = line.split(",", -1);
            ConsumptionImportRow row = new ConsumptionImportRow();
            row.setRowNumber(rowNumber);
            row.setPhone(col(cols, 0));
            row.setPetName(col(cols, 1));
            row.setDateRaw(col(cols, 2));
            row.setAmountRaw(col(cols, 3));
            row.setNote(col(cols, 4));
            rows.add(row);
        }
        return rows;
    }
}
