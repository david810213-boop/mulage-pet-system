package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.dto.WalletTransactionResponse;
import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.enums.WalletTransactionType;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.model.WalletTransaction;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txRepository;
    private final UserService userService;

    // ── 查詢錢包 ────────────────────────────────────────────────────────────
    public WalletResponse getWallet(String username) {
        Wallet wallet = getOrCreateWallet(username);
        return WalletResponse.from(wallet);
    }

    // ── 查詢異動紀錄 ────────────────────────────────────────────────────────
    public List<WalletTransactionResponse> getTransactions(String username) {
        Wallet wallet = getOrCreateWallet(username);
        return txRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId())
                .stream().map(WalletTransactionResponse::from).toList();
    }

    // ── 儲值（店家操作）────────────────────────────────────────────────────
    // 由店家後台幫顧客儲值，顧客自己不能直接操作（避免繞過實體金流）
    @Transactional
    public WalletResponse deposit(String username, DepositRequest req) {
        Wallet wallet = getOrCreateWallet(username);
        int amount = req.getAmount();

        // 1. 加入儲值金額
        wallet.setBalance(wallet.getBalance() + amount);

        // 2. 記錄儲值交易
        recordTransaction(wallet, WalletTransactionType.DEPOSIT, amount,
                req.getNote() != null ? req.getNote() : "儲值 $" + amount);

        // 3. 村民優惠方案：單筆滿 5000 贈 200 元（僅限第一次達到 VILLAGE 等級）
        MemberCardTier newTier = MemberCardTier.fromAmount(amount);
        if (newTier == MemberCardTier.VILLAGE && wallet.getCardTier() == MemberCardTier.NONE) {
            wallet.setBalance(wallet.getBalance() + 200);
            recordTransaction(wallet, WalletTransactionType.DEPOSIT_BONUS, 200, "村民優惠方案贈點 $200");
            log.info("使用者 {} 首次達到村民方案，贈送 200 元", username);
        }

        // 4. 更新會員卡等級（取歷史最高等級，不會降級）
        upgradeTierIfNeeded(wallet, newTier);

        walletRepository.save(wallet);
        return WalletResponse.from(wallet);
    }

    // ── 消費扣款（結帳時由 PaymentService 呼叫）──────────────────────────
    @Transactional
    public void deduct(String username, int amount, Long appointmentId) {
        Wallet wallet = getOrCreateWallet(username);
        if (wallet.getBalance() < amount) {
            throw new IllegalArgumentException("儲值金餘額不足，目前餘額：$" + wallet.getBalance());
        }
        wallet.setBalance(wallet.getBalance() - amount);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .type(WalletTransactionType.DEDUCT)
                .amount(-amount)
                .balanceAfter(wallet.getBalance())
                .note("預約 #" + appointmentId + " 消費扣款")
                .appointmentId(appointmentId)
                .build();
        txRepository.save(tx);
        walletRepository.save(wallet);
    }

    // ── 取得或建立錢包（加 @Transactional 防止並發重複建立）──────────────
    @Transactional
    public Wallet getOrCreateWallet(String username) {
        User user = userService.getUserEntityByUsername(username);
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    // 再查一次，避免並發時重複 insert（double-checked）
                    return walletRepository.findByUserId(user.getId())
                            .orElseGet(() -> {
                                Wallet newWallet = Wallet.builder().user(user).build();
                                return walletRepository.save(newWallet);
                            });
                });
    }

    // ── 私有：判斷是否升級卡等（只升不降）──────────────────────────────
    private void upgradeTierIfNeeded(Wallet wallet, MemberCardTier newTier) {
        // 只有當新等級高於現有等級時才升級
        if (newTier.ordinal() <= wallet.getCardTier().ordinal()) return;
        if (newTier == MemberCardTier.NONE) return;

        MemberCardTier oldTier = wallet.getCardTier();
        wallet.setCardTier(newTier);

        // 第一次開卡（從 NONE 升上來），設定開卡日期與到期日
        if (oldTier == MemberCardTier.NONE) {
            wallet.setCardActivatedAt(LocalDate.now());
            wallet.setCardExpiresAt(LocalDate.now().plusDays(365));
            log.info("使用者 {} 開卡：{}，到期日：{}", wallet.getUser().getUsername(),
                    newTier.getLabel(), wallet.getCardExpiresAt());
        }

        log.info("使用者 {} 升級至：{}", wallet.getUser().getUsername(), newTier.getLabel());
    }

    // ── 私有：記錄交易（帶餘額快照）────────────────────────────────────
    private void recordTransaction(Wallet wallet, WalletTransactionType type, int amount, String note) {
        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .type(type)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .note(note)
                .build();
        txRepository.save(tx);
    }
}
