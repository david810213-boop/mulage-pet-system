package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.InventoryException;
import com.petgrooming.pet_system.model.RetailProduct;
import com.petgrooming.pet_system.model.StoreSupply;
import com.petgrooming.pet_system.repository.RetailProductRepository;
import com.petgrooming.pet_system.repository.StoreSupplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 需求（追加，2026-10-02 店家確認 7）：商品條碼。
 *
 * - 條碼來源（7-2-C）：有原廠條碼就用原廠的；沒有的（例如分裝洗劑）由系統產生
 *   店內用 EAN-13：「20」＋類別碼（1＝零售商品、2＝店用洗劑）＋9 碼流水號（商品 id）＋檢查碼。
 *   20～29 開頭是 GS1 保留給店內自用的範圍，不會跟市售商品的國際條碼撞號。
 * - 零售商品跟店用洗劑共用同一個條碼空間，不能重複。
 * - 掃碼槍等同鍵盤輸入（7-1-A），前端把掃到的字串丟給 lookup() 查是哪一個商品。
 */
@Service
@RequiredArgsConstructor
public class BarcodeService {

    public static final String TYPE_RETAIL = "RETAIL";
    public static final String TYPE_SUPPLY = "SUPPLY";

    private final RetailProductRepository retailProductRepository;
    private final StoreSupplyRepository storeSupplyRepository;

    /** 掃碼查商品；查不到回傳 found=false。 */
    public Map<String, Object> lookup(String raw) {
        String code = normalize(raw);
        Map<String, Object> res = new LinkedHashMap<>();
        if (code == null) {
            res.put("found", false);
            return res;
        }
        var retail = retailProductRepository.findByBarcode(code);
        if (retail.isPresent()) {
            RetailProduct p = retail.get();
            res.put("found", true);
            res.put("type", TYPE_RETAIL);
            res.put("id", p.getId());
            res.put("name", p.getName());
            res.put("price", p.getPrice());
            res.put("stock", p.getStockQuantity());
            res.put("deleted", p.isDeleted());
            return res;
        }
        var supply = storeSupplyRepository.findByBarcode(code);
        if (supply.isPresent()) {
            StoreSupply s = supply.get();
            res.put("found", true);
            res.put("type", TYPE_SUPPLY);
            res.put("id", s.getId());
            res.put("name", s.getName());
            res.put("stock", s.getStockQuantity());
            res.put("deleted", s.isDeleted());
            return res;
        }
        res.put("found", false);
        return res;
    }

    /** 去掉前後空白；空字串視為沒有條碼。只接受英數字與 -（掃碼槍偶爾會多帶換行或空白）。 */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String code = raw.trim().replaceAll("\\s", "");
        if (code.isEmpty()) return null;
        if (!code.matches("[0-9A-Za-z\\-]{1,32}")) {
            throw new InventoryException("條碼格式不正確（只能是英文、數字或 -，最多 32 碼）：" + raw);
        }
        return code;
    }

    /** 檢查條碼沒有被別的商品（含另一類）用掉；selfType/selfId 是自己，可以略過。 */
    public void assertAvailable(String code, String selfType, Long selfId) {
        if (code == null) return;
        retailProductRepository.findByBarcode(code).ifPresent(p -> {
            if (!(TYPE_RETAIL.equals(selfType) && p.getId().equals(selfId))) {
                throw new InventoryException("條碼 " + code + " 已經用在零售商品「" + p.getName() + "」");
            }
        });
        storeSupplyRepository.findByBarcode(code).ifPresent(s -> {
            if (!(TYPE_SUPPLY.equals(selfType) && s.getId().equals(selfId))) {
                throw new InventoryException("條碼 " + code + " 已經用在店用洗劑「" + s.getName() + "」");
            }
        });
    }

    /** 產生店內 EAN-13：20 + 類別碼 + 9 碼 id + 檢查碼 */
    public static String generate(String type, long id) {
        String body = "20" + (TYPE_SUPPLY.equals(type) ? "2" : "1") + String.format("%09d", id % 1_000_000_000L);
        return body + ean13CheckDigit(body);
    }

    static int ean13CheckDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = first12.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        return (10 - sum % 10) % 10;
    }

    /** 幫所有還沒有條碼的上架商品產生店內條碼，回傳產生的數量。 */
    @Transactional
    public int generateMissing() {
        int n = 0;
        for (RetailProduct p : retailProductRepository.findByIsDeletedFalseAndBarcodeIsNull()) {
            String code = generate(TYPE_RETAIL, p.getId());
            if (storeSupplyRepository.findByBarcode(code).isPresent()) continue;
            p.setBarcode(code);
            retailProductRepository.save(p);
            n++;
        }
        for (StoreSupply s : storeSupplyRepository.findByIsDeletedFalseAndBarcodeIsNull()) {
            String code = generate(TYPE_SUPPLY, s.getId());
            if (retailProductRepository.findByBarcode(code).isPresent()) continue;
            s.setBarcode(code);
            storeSupplyRepository.save(s);
            n++;
        }
        return n;
    }
}
