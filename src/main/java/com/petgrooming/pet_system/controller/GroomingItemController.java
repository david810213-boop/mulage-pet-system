package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.service.interfaces.GroomingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 提供美容服務項目清單的公開查詢 API，供 LIFF 預約頁面（或其他前端）選擇服務項目時使用
@RestController
@RequestMapping("/api/grooming-items")
@RequiredArgsConstructor
public class GroomingItemController {

    private final GroomingService groomingService;

    // ── GET /api/grooming-items ─────────────────────────────────────────────
    // 需求 4：LIFF 預約頁只顯示「可線上預約」的主項目
    // （大美容 / 小美容 / 精緻洗 / 定製洗），其餘調理項目留給現場開單。
    @GetMapping
    public ResponseEntity<List<GroomingItemResponse>> getBookableItems() {
        return ResponseEntity.ok(groomingService.getBookableItems());
    }

    // ── GET /api/grooming-items/all ─────────────────────────────────────────
    // 後台 / 現場開單用：回傳所有上架項目（含不可預約的調理項目）
    @GetMapping("/all")
    public ResponseEntity<List<GroomingItemResponse>> getAllItems() {
        return ResponseEntity.ok(groomingService.getAllItems());
    }
}
