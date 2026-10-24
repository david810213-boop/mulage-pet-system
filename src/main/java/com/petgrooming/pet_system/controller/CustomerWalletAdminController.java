package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireCsrf;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.dto.WalletTransactionResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.OperationLogService;
import com.petgrooming.pet_system.service.TopUpService;
import com.petgrooming.pet_system.service.UserService;
import com.petgrooming.pet_system.service.WalletService;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CustomerWalletAdminController {

    private final WalletService walletService;
    private final OperationLogService operationLogService;
    private final TopUpService topUpService;
    private final UserService userService;

    // ── GET /api/admin/wallets/{userId} ────────────────────────────────────
    // 查詢指定顧客的錢包
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/wallets/{userId}")
    public ResponseEntity<WalletResponse> getWalletById(@PathVariable Long userId) {
        return getWallet(usernameOf(userId));
    }

    // ── GET /api/admin/wallets/{userId}/transactions ───────────────────────
    // 查詢指定顧客的異動紀錄
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/wallets/{userId}/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> getTransactionsById(@PathVariable Long userId) {
        return getTransactions(usernameOf(userId));
    }

    // ── POST /api/admin/wallets/{userId}/deposit ───────────────────────────
    // 幫指定顧客儲值（收到現金/匯款後由店家操作）
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @RequireCsrf
    @PostMapping("/wallets/{userId}/deposit")
    public ResponseEntity<?> depositById(
            @PathVariable Long userId,
            @Valid @RequestBody DepositRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @CurrentUser String reviewer) {
        return deposit(usernameOf(userId), req, idempotencyKey, reviewer);
    }

    private String usernameOf(Long userId) {
        return userService.getUserEntityById(userId).getUsername();
    }

    // 以下三個是舊路徑（網址帶帳號），改用上方以 userId 識別的路徑
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/wallet/{username}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable String username) {
        return ResponseEntity.ok(walletService.getWallet(username));
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping("/wallet/{username}/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> getTransactions(@PathVariable String username) {
        return ResponseEntity.ok(walletService.getTransactions(username));
    }

    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @RequireCsrf
    @PostMapping("/wallet/{username}/deposit")
    public ResponseEntity<?> deposit(
            @PathVariable String username,
            @Valid @RequestBody DepositRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @CurrentUser String reviewer) {
        if (idempotencyKey != null) {
            req.setIdempotencyKey(idempotencyKey);
        }
        WalletResponse res = walletService.deposit(username, req);
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
    }
}
