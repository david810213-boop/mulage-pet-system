package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.SimpleImportResult;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.StoreSupply;
import com.petgrooming.pet_system.repository.RetailProductRepository;
import com.petgrooming.pet_system.repository.StoreSupplyRepository;
import com.petgrooming.pet_system.utils.CsvImportFileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 需求（追加，2026-10-02 店家確認 7）：零售商品／店用洗劑 CSV 批次匯入。
 *
 * 零售商品欄位：名稱,條碼,售價,成本,庫存,說明
 * 店用洗劑欄位：名稱,條碼,庫存,安全庫存,成本
 *
 * 規則：
 * - 先用條碼比對；沒有條碼就用名稱比對（只比上架中的商品）。
 * - 已存在（7-3-A）：更新售價／成本／說明（洗劑：安全庫存／成本），條碼空白時補上；
 *   「庫存不動」——庫存請用盤點調整，避免重複匯入把庫存洗掉。
 * - 新商品（7-4-A）：庫存欄就是目前實際庫存；條碼空白由系統產生店內條碼。
 * - 每一列各自處理，有錯的列跳過並回報「第幾列：原因」，不影響其他列。
 * - 跟會員匯入一樣不支援欄位內容有逗號。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryImportService {

    private final RetailProductRepository retailProductRepository;
    private final StoreSupplyRepository storeSupplyRepository;
    private final RetailProductService retailProductService;
    private final StoreSupplyService storeSupplyService;
    private final BarcodeService barcodeService;

    // 刻意不加 @Transactional：每一列各自呼叫 create/update（各自一個交易），
    // 某一列出錯不會把整批 rollback，也不會讓已成功的列一起失敗。
    public SimpleImportResult importRetail(MultipartFile file) throws IOException {
        List<String[]> rows = parse(file);
        List<String> errors = new ArrayList<>();
        int ok = 0, created = 0, updated = 0;
        for (int i = 0; i < rows.size(); i++) {
            String[] c = rows.get(i);
            int rowNo = i + 1;
            try {
                String name = col(c, 0);
                if (name.isEmpty()) throw new IllegalArgumentException("名稱空白");
                String code = barcode(col(c, 1));
                int price = intCol(col(c, 2), "售價", true);
                int cost = intCol(col(c, 3), "成本", false);
                int stock = intCol(col(c, 4), "庫存", false);
                String desc = col(c, 5);

                Optional<RetailProduct> existing = code != null
                        ? retailProductRepository.findByBarcode(code)
                        : retailProductRepository.findFirstByNameAndIsDeletedFalse(name);
                if (existing.isEmpty() && code != null) {
                    // 有條碼但系統裡這個條碼還沒用過：再用名稱找一次（舊商品還沒有條碼的情況）
                    existing = retailProductRepository.findFirstByNameAndIsDeletedFalse(name)
                            .filter(p -> p.getBarcode() == null || p.getBarcode().startsWith("20"));
                }
                barcodeService.assertAvailable(code, BarcodeService.TYPE_RETAIL,
                        existing.map(RetailProduct::getId).orElse(null));
                if (existing.isPresent()) {
                    RetailProduct p = existing.get();
                    if (p.isDeleted()) throw new IllegalArgumentException("條碼 " + code + " 是已下架的商品「" + p.getName() + "」");
                    retailProductService.update(p.getId(), name, price,
                            desc.isEmpty() ? p.getDescription() : desc, cost, code != null ? code : p.getBarcode());
                    updated++;
                } else {
                    retailProductService.create(name, price, stock, desc, cost, code);
                    created++;
                }
                ok++;
            } catch (Exception e) {
                errors.add("第 " + rowNo + " 列：" + e.getMessage());
            }
        }
        log.info("📦 [零售商品匯入] 新增 {}、更新 {}、錯誤 {}", created, updated, errors.size());
        return SimpleImportResult.builder().totalRows(rows.size()).succeeded(ok).rowErrors(errors).build();
    }

    public SimpleImportResult importSupplies(MultipartFile file) throws IOException {
        List<String[]> rows = parse(file);
        List<String> errors = new ArrayList<>();
        int ok = 0, created = 0, updated = 0;
        for (int i = 0; i < rows.size(); i++) {
            String[] c = rows.get(i);
            int rowNo = i + 1;
            try {
                String name = col(c, 0);
                if (name.isEmpty()) throw new IllegalArgumentException("名稱空白");
                String code = barcode(col(c, 1));
                int stock = intCol(col(c, 2), "庫存", false);
                int safety = intCol(col(c, 3), "安全庫存", false);
                int cost = intCol(col(c, 4), "成本", false);

                Optional<StoreSupply> existing = code != null
                        ? storeSupplyRepository.findByBarcode(code)
                        : storeSupplyRepository.findFirstByNameAndIsDeletedFalse(name);
                if (existing.isEmpty() && code != null) {
                    existing = storeSupplyRepository.findFirstByNameAndIsDeletedFalse(name)
                            .filter(s -> s.getBarcode() == null || s.getBarcode().startsWith("20"));
                }
                barcodeService.assertAvailable(code, BarcodeService.TYPE_SUPPLY,
                        existing.map(StoreSupply::getId).orElse(null));
                if (existing.isPresent()) {
                    StoreSupply s = existing.get();
                    if (s.isDeleted()) throw new IllegalArgumentException("條碼 " + code + " 是已下架的品項「" + s.getName() + "」");
                    storeSupplyService.update(s.getId(), name, safety, cost, code != null ? code : s.getBarcode());
                    updated++;
                } else {
                    storeSupplyService.create(name, stock, safety, cost, code);
                    created++;
                }
                ok++;
            } catch (Exception e) {
                errors.add("第 " + rowNo + " 列：" + e.getMessage());
            }
        }
        log.info("🧴 [店用洗劑匯入] 新增 {}、更新 {}、錯誤 {}", created, updated, errors.size());
        return SimpleImportResult.builder().totalRows(rows.size()).succeeded(ok).rowErrors(errors).build();
    }

    // 第一列是表頭，略過；空白列略過
    private List<String[]> parse(MultipartFile file) throws IOException {
        List<String> lines = CsvImportFileReader.read(file).lines();
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            rows.add(lines.get(i).split(",", -1));
        }
        return rows;
    }

    private static String col(String[] cols, int idx) {
        return idx < cols.length ? cols[idx].trim().replaceAll("^\"|\"$", "") : "";
    }

    // Excel 把長數字存成科學記號（例如 4.71E+12）時，條碼已經失真，直接擋下並提示
    private static String barcode(String raw) {
        if (raw.matches("(?i)[0-9.]+E\\+?[0-9]+")) {
            throw new IllegalArgumentException("條碼變成科學記號（" + raw + "），請在 Excel 把條碼欄設成「文字」格式後重新存檔");
        }
        return BarcodeService.normalize(raw);
    }

    private static int intCol(String raw, String label, boolean required) {
        if (raw.isEmpty()) {
            if (required) throw new IllegalArgumentException(label + "空白");
            return 0;
        }
        try {
            int v = (int) Math.round(Double.parseDouble(raw.replace("$", "").replace("NT", "")));
            if (v < 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "格式錯誤：" + raw);
        }
    }
}
