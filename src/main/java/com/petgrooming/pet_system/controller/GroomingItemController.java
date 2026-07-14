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
    // 查詢所有上架中的美容服務項目（已下架的不會回傳）
    @GetMapping
    public ResponseEntity<List<GroomingItemResponse>> getAllItems() {
        return ResponseEntity.ok(groomingService.getAllItems());
    }
}
