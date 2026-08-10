package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.TopUpRequestCreate;
import com.petgrooming.pet_system.dto.TopUpRequestResponse;
import com.petgrooming.pet_system.dto.TopUpReviewRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.TopUpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 需求 1：線上轉帳儲值 API
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

    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute("tokenUsername");
    }

    // ── 顧客送出申請 ────────────────────────────────────────────────────────
    @PostMapping("/api/topup")
    public ResponseEntity<?> submit(@Valid @RequestBody TopUpRequestCreate req,
                                    HttpServletRequest request) {
        try {
            var res = topUpService.submit(currentUsername(request), req);
            operationLogService.logByUsername(currentUsername(request), "WALLET", "TOPUP_REQUEST",
                    "會員 " + currentUsername(request), "+$" + req.getAmount() + "（末五碼 " + req.getLastFiveDigits() + "）");
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── 顧客查自己的申請 ────────────────────────────────────────────────────
    @GetMapping("/api/topup/my")
    public ResponseEntity<List<TopUpRequestResponse>> myRequests(HttpServletRequest request) {
        return ResponseEntity.ok(topUpService.myRequests(currentUsername(request)));
    }

    // ── 店家待核帳清單 ──────────────────────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/api/admin/topup/pending")
    public ResponseEntity<List<TopUpRequestResponse>> pending() {
        return ResponseEntity.ok(topUpService.pending());
    }

    // ── 店家確認入帳 ────────────────────────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/api/admin/topup/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id, HttpServletRequest request) {
        try {
            var res = topUpService.confirm(id, currentUsername(request));
            operationLogService.logByUsername(currentUsername(request), "WALLET", "TOPUP_CONFIRM",
                    "儲值申請 #" + id, null);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── 店家駁回 ────────────────────────────────────────────────────────────
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/api/admin/topup/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @RequestBody(required = false) TopUpReviewRequest req,
                                    HttpServletRequest request) {
        try {
            String reason = req != null ? req.getRejectReason() : null;
            var res = topUpService.reject(id, currentUsername(request), reason);
            operationLogService.logByUsername(currentUsername(request), "WALLET", "TOPUP_REJECT",
                    "儲值申請 #" + id, reason);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
