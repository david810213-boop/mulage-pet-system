package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.CreateTopUpRequest;
import com.petgrooming.pet_system.dto.TopUpRequestResponse;
import com.petgrooming.pet_system.dto.TopUpReviewRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.TopUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 線上轉帳儲值 API
 *
 * 顧客端：
 *   POST /api/topup            送出轉帳儲值申請
 *   GET  /api/topup/my         查自己的申請紀錄
 * 店家端：
 *   GET  /api/admin/topup/pending      待核帳清單
 *   POST /api/admin/topup/{id}/confirm 確認入帳
 *   POST /api/admin/topup/{id}/reject  駁回
 */
@RestController
@RequiredArgsConstructor
public class TopUpController {

    private final TopUpService topUpService;
    private final OperationLogService operationLogService;

    // ── 顧客送出申請 ────────────────────────────────────────────────────────
    @PostMapping({"/api/top-ups", "/api/topup"})
    public ResponseEntity<?> submit(@Valid @RequestBody CreateTopUpRequest req, @CurrentUser String username) {
        var res = topUpService.submit(username, req);
        operationLogService.logByUsername(username, "WALLET", "TOPUP_REQUEST",
                "會員 " + username, "+$" + req.getAmount() + "（末五碼 " + req.getLastFiveDigits() + "）");
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // ── 顧客查自己的申請 ────────────────────────────────────────────────────
    @GetMapping({"/api/me/top-ups", "/api/topup/my"})
    public ResponseEntity<List<TopUpRequestResponse>> myRequests(@CurrentUser String username) {
        return ResponseEntity.ok(topUpService.myRequests(username));
    }

    // ── 店家待核帳清單 ──────────────────────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping({"/api/admin/top-ups/pending", "/api/admin/topup/pending"})
    public ResponseEntity<List<TopUpRequestResponse>> pending() {
        return ResponseEntity.ok(topUpService.pending());
    }

    // ── 店家確認入帳 ────────────────────────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping({"/api/admin/top-ups/{id}/confirm", "/api/admin/topup/{id}/confirm"})
    public ResponseEntity<?> confirm(@PathVariable Long id, @CurrentUser String username) {
        var res = topUpService.confirm(id, username);
        operationLogService.logByUsername(username, "WALLET", "TOPUP_CONFIRM",
                "儲值申請 #" + id, null);
        return ResponseEntity.ok(res);
    }

    // ── 店家駁回 ────────────────────────────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping({"/api/admin/top-ups/{id}/reject", "/api/admin/topup/{id}/reject"})
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @RequestBody(required = false) TopUpReviewRequest req,
                                    @CurrentUser String username) {
        String reason = req != null ? req.getRejectReason() : null;
        var res = topUpService.reject(id, username, reason);
        operationLogService.logByUsername(username, "WALLET", "TOPUP_REJECT",
                "儲值申請 #" + id, reason);
        return ResponseEntity.ok(res);
    }
}
