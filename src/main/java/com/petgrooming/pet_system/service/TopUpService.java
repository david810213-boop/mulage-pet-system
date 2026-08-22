package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.TopUpRequestCreate;
import com.petgrooming.pet_system.dto.TopUpRequestResponse;
import com.petgrooming.pet_system.enums.TopUpStatus;
import com.petgrooming.pet_system.model.TopUpRequest;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.TopUpRequestRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求 1：線上儲值僅限轉帳。
 *
 * 流程：
 *   顧客 submit()  → 建立 PENDING 申請（餘額不動）
 *   店家 confirm() → 呼叫既有 WalletService.deposit() 真正加值 + 升卡 + 贈點
 *   店家 reject()  → 標記駁回，餘額仍不動
 *
 * 重點：加值邏輯完全複用 WalletService.deposit()，
 * 這樣「村民方案贈點、會員卡升級」等既有規則不會有第二套實作導致行為分歧。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopUpService {

    private final TopUpRequestRepository topUpRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;

    // ── 顧客送出轉帳儲值申請 ────────────────────────────────────────────────
    @Transactional
    public TopUpRequestResponse submit(String username, TopUpRequestCreate req) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));

        TopUpRequest topUp = TopUpRequest.builder()
                .user(user)
                .amount(req.getAmount())
                .lastFiveDigits(req.getLastFiveDigits())
                .remitNote(req.getRemitNote())
                .status(TopUpStatus.PENDING)
                .build();

        TopUpRequest saved = topUpRepository.save(topUp);
        log.info("顧客 {} 送出轉帳儲值申請 #{}，金額 {}", username, saved.getId(), req.getAmount());
        return TopUpRequestResponse.from(saved);
    }

    // ── 顧客查自己的申請紀錄 ────────────────────────────────────────────────
    public List<TopUpRequestResponse> myRequests(String username) {
        return topUpRepository.findByUserUsernameOrderByCreatedAtDesc(username)
                .stream().map(TopUpRequestResponse::from).toList();
    }

    // ── 店家：待核帳清單 ────────────────────────────────────────────────────
    public List<TopUpRequestResponse> pending() {
        return topUpRepository.findByStatusOrderByCreatedAtAsc(TopUpStatus.PENDING)
                .stream().map(TopUpRequestResponse::from).toList();
    }

    // ── 店家：確認入帳 ──────────────────────────────────────────────────────
    @Transactional
    public TopUpRequestResponse confirm(Long id, String reviewerName) {
        TopUpRequest topUp = topUpRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到此儲值申請"));

        if (topUp.getStatus() != TopUpStatus.PENDING) {
            throw new IllegalArgumentException("此申請已處理過，狀態：" + topUp.getStatus().getLabel());
        }

        // 真正加值：複用既有錢包儲值邏輯（含贈點 / 升卡）
        DepositRequest deposit = new DepositRequest();
        deposit.setAmount(topUp.getAmount());
        deposit.setNote("轉帳儲值入帳 $" + topUp.getAmount()
                + (topUp.getLastFiveDigits() != null ? "（後五碼 " + topUp.getLastFiveDigits() + "）" : ""));
        walletService.deposit(topUp.getUser().getUsername(), deposit);

        topUp.setStatus(TopUpStatus.CONFIRMED);
        topUp.setReviewedAt(LocalDateTime.now());
        topUp.setReviewedBy(reviewerName);
        topUpRepository.save(topUp);

        log.info("儲值申請 #{} 已由 {} 確認入帳", id, reviewerName);
        return TopUpRequestResponse.from(topUp);
    }

    // ── 店家：駁回 ──────────────────────────────────────────────────────────
    @Transactional
    public TopUpRequestResponse reject(Long id, String reviewerName, String reason) {
        TopUpRequest topUp = topUpRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到此儲值申請"));

        if (topUp.getStatus() != TopUpStatus.PENDING) {
            throw new IllegalArgumentException("此申請已處理過，狀態：" + topUp.getStatus().getLabel());
        }

        topUp.setStatus(TopUpStatus.REJECTED);
        topUp.setReviewedAt(LocalDateTime.now());
        topUp.setReviewedBy(reviewerName);
        topUp.setRejectReason(reason);
        topUpRepository.save(topUp);

        log.info("儲值申請 #{} 已由 {} 駁回，原因：{}", id, reviewerName, reason);
        return TopUpRequestResponse.from(topUp);
    }

    // ── 店家：手動記錄一筆已入帳的儲值（現場收現金／匯款，不走 LIFF 申請流程）─
    // 供 WalletMvcController / CustomerWalletAdminController「幫顧客儲值」共用：
    // 錢包餘額已經由 WalletService.deposit() 加值完成，這裡只是「補登」一筆
    // 狀態直接是 CONFIRMED 的申請紀錄，讓財務報表「本月儲值金額」統計得到。
    @Transactional
    public void recordManualTopup(User user, int amount, String reviewedBy, String note) {
        TopUpRequest topUp = TopUpRequest.builder()
                .user(user)
                .amount(amount)
                .remitNote(note != null && !note.isBlank() ? note : "店家現場收款儲值")
                .status(TopUpStatus.CONFIRMED)
                .reviewedAt(LocalDateTime.now())
                .reviewedBy(reviewedBy)
                .build();
        topUpRepository.save(topUp);
    }
}
