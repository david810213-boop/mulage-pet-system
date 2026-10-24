package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.MemberCardTier;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.MemberException;
import com.petgrooming.pet_system.model.Pet;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.model.Wallet;
import com.petgrooming.pet_system.model.WalkInOrder;
import com.petgrooming.pet_system.repository.PetRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import com.petgrooming.pet_system.repository.WalletRepository;
import com.petgrooming.pet_system.repository.WalkInOrderRepository;
import com.petgrooming.pet_system.repository.TopUpRequestRepository;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.model.TopUpRequest;
import com.petgrooming.pet_system.model.Appointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map;

/**
 * 老客戶認領既有匯入資料：顧客用電話自助認領（需核對毛孩名字或姓名），以及店家後台手動綁定。
 * 兩者共用同一套過戶邏輯：寵物、消費紀錄、預約、儲值申請、錢包整批轉到目標帳號後刪除匯入用的暫時帳號。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberClaimService {

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final WalletRepository walletRepository;
    private final WalkInOrderRepository walkInOrderRepository;
    private final TopUpRequestRepository topUpRequestRepository;
    private final AppointmentRepository appointmentRepository;

    // 用途：顧客第一次用 LINE 登入時系統會自動建一筆空白新帳號（既有機制，不動它），
    // 這裡是額外的動作——把電話號碼比對到的「匯入但還沒被認領」的舊資料，整批
    // 過戶到目前這筆已經綁好 LINE 的帳號上，然後把那筆匯入用的暫時帳號刪掉，
    // 避免產生一個人對應兩筆帳號的重複資料。
    //
    // 安全限制：只有目前這個 LINE 帳號「還沒填過資料」（profileCompletedAt 為 null）
    // 才能認領，避免已經自己填好資料的人不小心把別人的資料領走蓋掉自己的。
    //
    // 認領時多一道確認——
    // 匯入帳號有毛孩就要輸入其中一隻毛孩的名字，沒有毛孩就輸入登記的姓名。
    // 原因：帳號裡可能有儲值金與會員卡，只憑電話就能認領的話，知道別人電話的人
    // 可以把整個帳號連錢包一起領走。
    @Transactional
    public User claimByPhone(User currentUser, String phone, String verification) {
        User imported = findClaimable(currentUser, phone)
                .orElseThrow(() -> new MemberException(ErrorCode.NOT_FOUND, "查無符合這支電話的既有會員資料"));
        if (!verifyClaim(imported, verification)) {
            throw new MemberException(hasActivePets(imported)
                    ? "毛孩名字不符，請再確認一次（輸入任一隻登記的毛孩名字即可）"
                    : "姓名不符，請輸入當初在店裡登記的姓名");
        }
        String normalized = MemberImportService.normalizePhone(phone);

        // 過戶基本資料（這裡的 currentUser 一定是還沒填過資料的全新帳號，見上面
        // profileCompletedAt 檢查，所以整批覆蓋沒有蓋掉顧客自己資料的風險）
        currentUser.setName(imported.getName());
        currentUser.setPhone(imported.getPhone());
        currentUser.setProfileCompletedAt(LocalDateTime.now());
        userRepository.save(currentUser);

        TransferSummary summary = transferAllDataAndDeleteSource(imported, currentUser);

        log.info("[會員認領] {} 認領了電話 {} 的匯入資料，過戶 {} 隻寵物",
                currentUser.getUsername(), normalized, summary.pets().size());

        return currentUser;
    }

    // ── 新客報到第一步「輸入電話分流」────────────
    // 查到還沒被認領的既有會員 → 回傳遮罩後的姓名、毛孩數、要用什麼確認；
    // 查不到 → matched=false，前端改走一般新客流程。不回傳毛孩名字等可直接
    // 拿來認領的資訊。
    public Map<String, Object> lookupForClaim(User currentUser, String phone) {
        Optional<User> found = findClaimable(currentUser, phone);
        if (found.isEmpty()) {
            return Map.of("matched", false);
        }
        User imported = found.get();
        long petCount = activePets(imported).size();
        return Map.of(
                "matched", true,
                "maskedName", maskName(imported.getName()),
                "petCount", petCount,
                "verifyBy", petCount > 0 ? "PET" : "NAME");
    }

    private Optional<User> findClaimable(User currentUser, String phone) {
        if (currentUser.getProfileCompletedAt() != null) {
            throw new MemberException("這個帳號已經填過資料了，無法再認領其他會員資料");
        }
        if (phone == null || phone.isBlank()) return Optional.empty();
        return userRepository
                .findFirstByPhoneAndLineUserIdIsNullAndRoleOrderByIdAsc(MemberImportService.normalizePhone(phone), UserRole.CUSTOMER)
                .filter(u -> !u.getId().equals(currentUser.getId()));
    }

    private List<Pet> activePets(User owner) {
        return petRepository.findByOwnerId(owner.getId()).stream().filter(p -> !p.isDeleted()).toList();
    }

    private boolean hasActivePets(User owner) {
        return !activePets(owner).isEmpty();
    }

    private boolean verifyClaim(User imported, String verification) {
        String input = squash(verification);
        if (input.isEmpty()) return false;
        List<Pet> pets = activePets(imported);
        if (!pets.isEmpty()) {
            return pets.stream().anyMatch(p -> squash(p.getName()).equalsIgnoreCase(input));
        }
        return squash(imported.getName()).equalsIgnoreCase(input);
    }

    // 去掉所有空白（含全形空白）再比對，避免多打一個空格就被判定不符
    private static String squash(String s) {
        return s == null ? "" : s.replaceAll("[\\s\u3000]", "");
    }

    // 王小明 → 王○明、王明 → 王○、歐陽小明 → 歐○○明
    static String maskName(String name) {
        String n = squash(name);
        if (n.isEmpty()) return "（未登記姓名）";
        if (n.length() == 1) return "○";
        if (n.length() == 2) return n.charAt(0) + "○";
        return n.charAt(0) + "○".repeat(n.length() - 2) + n.charAt(n.length() - 1);
    }

    // ── 店家後台手動綁定既有匯入資料 ──────────────
    //
    // 使用時機：顧客的 LINE 帳號自己已經先填過個人資料/新增過毛孩（例如測試時
    // 不小心先手動填了，沒有先用電話號碼認領），導致 claimByPhone() 的防呆
    // 擋下自動認領（"這個帳號已經填過資料了，無法再認領其他會員資料"）。這種
    // 情況只能由店家在後台人工判斷「這確實是同一個人」，手動把匯入的暫時帳號
    // 資料合併過去。
    //
    // 跟 claimByPhone() 的差異：
    // ① 不覆蓋目標會員已經填的姓名——目標帳號通常已經有自己填的真實資料，
    //    這是店家人工判斷合併，不該用匯入資料蓋掉顧客自己填寫的內容。電話
    //    則是「目標帳號原本沒填才帶入」——因為
    //    LINE 帳號常常還沒填過電話，這種情況匯入資料裡的電話應該要補上，
    //    只有目標已經自己填過電話時才不覆蓋
    // ② 實際的資料過戶（寵物/消費/預約/儲值/刪除來源帳號）跟 claimByPhone() 共用同一支
    //    transferAllDataAndDeleteSource()，避免同一套邏輯維護兩份。
    @Transactional
    public void manualMerge(String importedUsername, String targetUsername) {
        User imported = userRepository.findByUsername(importedUsername)
                .orElseThrow(() -> new MemberException(ErrorCode.NOT_FOUND, "找不到這個匯入帳號：" + importedUsername));
        if (!imported.getUsername().startsWith("imported_")) {
            throw new MemberException("只能合併「既有會員資料匯入」建立的暫時帳號（帳號以 imported_ 開頭），這筆不是");
        }
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new MemberException(ErrorCode.NOT_FOUND, "找不到目標會員帳號：" + targetUsername));
        if (imported.getId().equals(target.getId())) {
            throw new MemberException("來源跟目標是同一個帳號");
        }
        if (target.getUsername().startsWith("imported_")) {
            throw new MemberException("目標帳號也是匯入用的暫時帳號，請選一個真正在使用的會員帳號");
        }

        // 目標帳號沒填電話才帶入匯入資料的電話，已經自己填過電話的話不覆蓋——姓名不論如何都不覆蓋。
        if (isBlank(target.getPhone()) && !isBlank(imported.getPhone())) {
            target.setPhone(imported.getPhone());
            userRepository.save(target);
        }

        TransferSummary summary = transferAllDataAndDeleteSource(imported, target);

        log.info("[手動綁定] 店家手動把匯入帳號 {} 的資料合併到會員 {}，過戶 {} 隻寵物、{} 筆消費紀錄、{} 筆預約、{} 筆儲值申請",
                importedUsername, targetUsername, summary.pets().size(), summary.orders().size(),
                summary.appointments().size(), summary.topUps().size());
    }

    // ── claimByPhone() 跟 manualMerge() 共用的過戶邏輯 ──
    //
    // 過戶內容：寵物、消費紀錄（現場開單）、預約、待處理儲值申請、儲值餘額
    // （加總，不是覆蓋），最後刪除來源帳號。
    //
    // ⚠️ 兩個容易踩的坑（都實際發生過）：
    // ① User.pets 是 cascade = CascadeType.ALL 的雙向一對多關聯，只透過
    //    petRepository 改 pets 資料表的 owner_id，imported 物件自己記憶體裡的
    //    pets 集合不會同步更新，userRepository.delete(imported) 觸發 cascade
    //    刪除時會把已經過戶的寵物一起級聯刪除掉（已經實際發生過一次資料遺失）。
    //    修法：saveAll 後強制 flush，再明確清空 imported.getPets()，兩步都要做。
    // ② 沒有處理 imported 的錢包，刪除來源帳號時會撞上 wallets 表的外鍵限制，
    //    整個交易在 commit 階段失敗回滾（不會真的遺失資料，但操作會整個失敗，
    //    且拋出的例外訊息跟前端顯示的訊息對不上，很難排查）。修法：跟寵物一樣，
    //    有餘額就加總到目標帳號，再把來源帳號的錢包刪掉。
    private TransferSummary transferAllDataAndDeleteSource(User imported, User target) {
        // 過戶寵物（改 owner，不是複製一份新的）
        List<Pet> pets = petRepository.findByOwnerId(imported.getId());
        for (Pet pet : pets) {
            pet.setOwner(target);
        }
        petRepository.saveAll(pets);
        petRepository.flush();
        imported.getPets().clear();

        // 過戶消費紀錄（現場開單）
        List<WalkInOrder> orders = walkInOrderRepository.findByMemberId(imported.getId());
        for (WalkInOrder order : orders) {
            order.setMember(target);
        }
        walkInOrderRepository.saveAll(orders);

        // 過戶預約紀錄
        List<Appointment> appointments = appointmentRepository.findByUserId(imported.getId());
        for (Appointment appt : appointments) {
            appt.setUser(target);
        }
        appointmentRepository.saveAll(appointments);

        // 過戶待處理/歷史儲值申請
        List<TopUpRequest> topUps = topUpRequestRepository.findByUserId(imported.getId());
        for (TopUpRequest t : topUps) {
            t.setUser(target);
        }
        topUpRequestRepository.saveAll(topUps);

        // 儲值餘額加總到目標會員的錢包，不是覆蓋；沒有餘額也要把來源帳號的
        // 空錢包刪掉，不然會卡外鍵讓下面刪除來源帳號失敗。
        // 會員卡等級／開卡日／到期日也要一起帶過去，
        // 不然匯入時帶入的等級在顧客認領後就消失了。兩邊都有卡時保留「比較好的那張」。
        walletRepository.findByUserId(imported.getId()).ifPresent(importedWallet -> {
            boolean hasBalance = importedWallet.getBalance() != null && importedWallet.getBalance() > 0;
            boolean hasCard = importedWallet.getCardTier() != null
                    && importedWallet.getCardTier() != MemberCardTier.NONE;
            if (hasBalance || hasCard) {
                Wallet targetWallet = walletRepository.findByUserId(target.getId())
                        .orElseGet(() -> walletRepository.save(Wallet.builder().user(target).balance(0).build()));
                if (hasBalance) {
                    int targetBalance = targetWallet.getBalance() != null ? targetWallet.getBalance() : 0;
                    targetWallet.setBalance(targetBalance + importedWallet.getBalance());
                }
                if (hasCard && isBetterCard(importedWallet, targetWallet)) {
                    targetWallet.setCardTier(importedWallet.getCardTier());
                    targetWallet.setCardActivatedAt(importedWallet.getCardActivatedAt());
                    targetWallet.setCardExpiresAt(importedWallet.getCardExpiresAt());
                }
                walletRepository.save(targetWallet);
            }
            walletRepository.delete(importedWallet);
        });

        userRepository.delete(imported);

        return new TransferSummary(pets, orders, appointments, topUps);
    }

    // 兩張會員卡比較：有效的優先 → 等級高的優先 → 到期日晚的優先
    private boolean isBetterCard(Wallet candidate, Wallet current) {
        if (current.getCardTier() == null || current.getCardTier() == MemberCardTier.NONE) return true;
        boolean candActive = candidate.isCardActive();
        boolean currActive = current.isCardActive();
        if (candActive != currActive) return candActive;
        int byTier = Integer.compare(candidate.getCardTier().ordinal(), current.getCardTier().ordinal());
        if (byTier != 0) return byTier > 0;
        if (candidate.getCardExpiresAt() == null) return false;
        return current.getCardExpiresAt() == null || candidate.getCardExpiresAt().isAfter(current.getCardExpiresAt());
    }

    private record TransferSummary(
            List<Pet> pets, List<WalkInOrder> orders, List<Appointment> appointments, List<TopUpRequest> topUps) {
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
