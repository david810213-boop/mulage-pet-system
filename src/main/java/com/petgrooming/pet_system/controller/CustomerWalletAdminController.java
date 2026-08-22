package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.dto.WalletTransactionResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 店家端儲值管理 API（STAFF / ADMIN 操作）
 * 幫指定顧客儲值、查詢任意顧客的錢包與紀錄
 */
@RestController
@RequestMapping("/api/admin/wallet")
@RequiredArgsConstructor
public class CustomerWalletAdminController {

    private final WalletService walletService;
    private final OperationLogService operationLogService;
    private final TopUpService topUpService;
    private final UserService userService;

    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute("tokenUsername");
    }

    // ── GET /api/admin/wallet/{username} ───────────────────────────────────
    // 查詢指定顧客的錢包
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{username}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable String username) {
        return ResponseEntity.ok(walletService.getWallet(username));
    }

    // ── GET /api/admin/wallet/{username}/transactions ──────────────────────
    // 查詢指定顧客的異動紀錄
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/{username}/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> getTransactions(@PathVariable String username) {
        return ResponseEntity.ok(walletService.getTransactions(username));
    }

    // ── POST /api/admin/wallet/{username}/deposit ──────────────────────────
    // 幫指定顧客儲值（收到現金/匯款後由店家操作）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{username}/deposit")
    public ResponseEntity<?> deposit(
            @PathVariable String username,
            @Valid @RequestBody DepositRequest req,
            HttpServletRequest request) {
        try {
            WalletResponse res = walletService.deposit(username, req);
            String reviewer = currentUsername(request);
            try {
                var targetCustomer = userService.getUserEntityByUsername(username);
                String reviewerName = reviewer != null ? userService.getUserEntityByUsername(reviewer).getName() : "系統管理員";
                topUpService.recordManualTopup(targetCustomer, req.getAmount(), reviewerName, req.getNote());
            } catch (Exception ignored) {
                // 補登儲值申請紀錄失敗不影響已完成的儲值本身，僅財務報表統計可能漏這一筆
            }
            operationLogService.logByUsername(reviewer, "WALLET", "DEPOSIT",
                    "會員 " + username, "+$" + req.getAmount() + (req.getNote() != null ? "（" + req.getNote() + "）" : ""));
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
