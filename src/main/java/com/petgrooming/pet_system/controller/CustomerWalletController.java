package com.petgrooming.pet_system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.dto.WalletTransactionResponse;
import com.petgrooming.pet_system.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 顧客端錢包 API（LIFF 呼叫）
 * 顧客只能查詢自己的餘額和紀錄，無法自己儲值
 * 儲值由店家後台操作（CustomerWalletAdminController）
 */
@Tag(name = "錢包", description = "顧客查詢自己的餘額與紀錄")
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class CustomerWalletController {

    private final WalletService walletService;

    // ── GET /api/wallet ────────────────────────────────────────────────────
    // 查詢自己的錢包（餘額、會員卡等級、折扣）
    @GetMapping
    public ResponseEntity<WalletResponse> getMyWallet(@CurrentUser String username) {
        return ResponseEntity.ok(walletService.getWallet(username));
    }

    // ── GET /api/wallet/transactions ───────────────────────────────────────
    // 查詢自己的儲值/消費紀錄
    @GetMapping("/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> getMyTransactions(@CurrentUser String username) {
        return ResponseEntity.ok(walletService.getTransactions(username));
    }
}
