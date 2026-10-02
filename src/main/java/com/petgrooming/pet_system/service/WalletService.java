package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.WalletResponse;
import com.petgrooming.pet_system.dto.WalletTransactionResponse;
import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.enums.WalletTransactionType;
import com.petgrooming.pet_system.exception.WalletException;
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

    public static final int MIN_DEPOSIT = 5000; // 單筆儲值下限（店家規定）

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
        int amount = req.getAmount() != null ? req.getAmount() : 0;
        // 需求（追加，2026-10-02 店家確認）：不開放單筆 5,000 以下的儲值
        if (amount < MIN_DEPOSIT) {
            throw new WalletException("單筆儲值最低 $5,000");
        }
        Wallet wallet = getOrCreateWallet(username);

        // 1. 加入儲值金額
        wallet.setBalance(wallet.getBalance() + amount);

        // 2. 記錄儲值交易
        recordTransaction(wallet, WalletTransactionType.DEPOSIT, amount,
                req.getNote() != null ? req.getNote() : "儲值 $" + amount);

        // 3. 村民優惠方案：單筆儲值落在村民級距（5,000～7,999）每次都贈 200 元
        //    （2026-10-02 店家確認：不限第一次）
        MemberCardTier newTier = MemberCardTier.fromAmount(amount);
        if (newTier == MemberCardTier.VILLAGE) {
            wallet.setBalance(wallet.getBalance() + 200);
            recordTransaction(wallet, WalletTransactionType.DEPOSIT_BONUS, 200, "村民優惠方案贈點 $200");
            log.info("使用者 {} 單筆儲值村民方案，贈送 200 元", username);
        }

        // 4. 依本次單筆儲值金額更新會員卡等級與有效期限
        applyTierRule(wallet, newTier);

        walletRepository.save(wallet);
        return WalletResponse.from(wallet);
    }

    // ── 消費扣款（結帳時由 PaymentService 呼叫）──────────────────────────
    // 以悲觀鎖鎖住錢包列，杜絕並發超扣（餘額存於 Wallet.balance 欄位）。
    @Transactional
    public void deduct(String username, int amount, Long appointmentId) {
        deduct(username, amount, appointmentId, "預約 #" + appointmentId + " 消費扣款");
    }

    // 需求：現場開單結帳也走這裡扣款，但沒有 appointmentId，改用自訂備註文字
    // （例如「現場單 #5 消費扣款」），避免顯示成看起來像 bug 的「預約 #null」。
    @Transactional
    public void deduct(String username, int amount, Long appointmentId, String note) {
        User user = userService.getUserEntityByUsername(username);

        // 先確保錢包存在（首次消費者可能還沒錢包）
        getOrCreateWallet(username);

        // 加鎖重新讀取，鎖持有到本交易 commit
        Wallet wallet = walletRepository.lockByUserId(user.getId())
                .orElseThrow(() -> new WalletException("找不到錢包"));

        if (wallet.getBalance() < amount) {
            throw new WalletException("儲值金餘額不足，目前餘額：$" + wallet.getBalance());
        }
        wallet.setBalance(wallet.getBalance() - amount);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .type(WalletTransactionType.DEDUCT)
                .amount(-amount)
                .balanceAfter(wallet.getBalance())
                .note(note)
                .appointmentId(appointmentId)
                .build();
        txRepository.save(tx);
        walletRepository.save(wallet);
    }

    // ── 退款補回儲值金（僅限原本用儲值金付款的訂單退款時呼叫）──────────────
    @Transactional
    public void refund(String username, int amount, Long appointmentId, String note) {
        User user = userService.getUserEntityByUsername(username);
        getOrCreateWallet(username);

        // 加鎖重新讀取，避免跟同一時間其他扣款/退款動作互相覆蓋
        Wallet wallet = walletRepository.lockByUserId(user.getId())
                .orElseThrow(() -> new WalletException("找不到錢包"));

        wallet.setBalance(wallet.getBalance() + amount);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .type(WalletTransactionType.REFUND)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .note(note)
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

    // ── 私有：會員卡等級規則（2026-09-30 依店家確認調整）─────────────────
    // 規則：
    //   ① 單筆未滿 5,000（NONE）：不影響等級與期限。
    //   ② 目前沒有卡、或卡已過期：等級直接改成本次單筆金額對應的等級（可能比以前低），
    //      有效期限從今天重新算一年。
    //   ③ 卡還有效、本次等級 ≥ 目前等級：升級（或同級續卡），有效期限從今天重新算一年。
    //   ④ 卡還有效、本次等級 < 目前等級：等級與期限都不變（不會因為小額儲值被降級，
    //      也不會用小額儲值延長高等級的期限）。
    // 例：2026-09-30 儲值 15,000 → 金卡 9 折，到 2027-09-29
    //     2026-10-30 再儲 5,000 → 維持金卡 9 折，期限仍是 2027-09-29（④），另贈 200 元
    //     2026-10-30 改儲 50,000 → 升 VIP 85 折，期限重算到 2027-10-29（③）
    // 舊版是「只升不降、到期日只在第一次開卡時設定」，會造成過期後再儲值卡還是過期。
    //   ⑤ 2026-10-02 店家確認：村民優惠方案沒有折扣，所以「沒有期限」（到期日留空）；
    //      到期日是「起算日＋1 年－1 天」（9/30 儲值 → 隔年 9/29）。
    private void applyTierRule(Wallet wallet, MemberCardTier newTier) {
        if (newTier == MemberCardTier.NONE) return;

        MemberCardTier oldTier = wallet.getCardTier();
        boolean active = wallet.isCardActive();
        if (active && newTier.ordinal() < oldTier.ordinal()) {
            log.info("使用者 {} 本次儲值等級 {} 低於目前有效等級 {}，等級與期限不變",
                    wallet.getUser().getUsername(), newTier.getLabel(), oldTier.getLabel());
            return;
        }

        LocalDate today = LocalDate.now();
        wallet.setCardTier(newTier);
        wallet.setCardActivatedAt(today);
        wallet.setCardExpiresAt(newTier == MemberCardTier.VILLAGE ? null : cardExpiryFrom(today));
        log.info("使用者 {} 會員卡：{} → {}（{}），到期日：{}", wallet.getUser().getUsername(),
                oldTier.getLabel(), newTier.getLabel(), active ? "有效期內" : "新開卡／過期重辦",
                wallet.getCardExpiresAt());
    }

    // 2026-09-30 → 2027-09-29（起算日＋1 年－1 天；閏年 2/29 起算會落在隔年 2/27）
    public static LocalDate cardExpiryFrom(LocalDate start) {
        return start.plusYears(1).minusDays(1);
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
