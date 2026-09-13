package com.petgrooming.pet_system.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 需求（追加，2026-09-13）：全站業務例外的共同基底類別。
 *
 * 背景：原本全部業務邏輯錯誤都丟 {@link IllegalArgumentException} /
 * {@link IllegalStateException}，Controller 端只能靠 catch 這兩種泛用型別，
 * 完全看不出這個錯誤來自哪個業務領域（會員？寵物？預約？），也沒辦法依
 * 情境給不同的 HTTP 狀態碼（例如「找不到這筆資料」理論上該是 404，但泛用
 * 例外只能統一回 400）。
 *
 * 這裡改成依業務領域分類（見同目錄下 MemberException / PetException /
 * AppointmentException / PaymentException / WalletException /
 * AuthException），全部繼承這個基底類別，帶一個可選的 HttpStatus（預設
 * 400 Bad Request，符合目前絕大多數情境）。GlobalApiExceptionHandler 只要
 * 攔截這個基底類別一次，所有子類別都會自動被接住，不用每個子類別各寫一支
 * ExceptionHandler。
 *
 * ⚠️ 重要設計決定：這個類別繼承的是 {@link IllegalArgumentException}，
 * 不是直接繼承 RuntimeException。原因：專案裡有好幾個 MVC controller
 * （例如 PetMvcController）本來就有 `catch (IllegalArgumentException e)`
 * 包住 service 呼叫，用來在同一個畫面顯示錯誤訊息。如果這裡直接繼承
 * RuntimeException，PetService 這批已經遷移成拋 PetException 的地方，
 * 這些 MVC controller 的 catch 就再也接不到，會變成整支例外沒人接、
 * 直接讓使用者看到 500 錯誤頁面——這正是「確保所有功能能夠照常跑動」
 * 這個要求下最需要避免的事。繼承 IllegalArgumentException 之後，這些
 * 舊有的 catch 區塊完全不用改，行為就跟以前一樣，同時 REST controller
 * 這邊透過 GlobalApiExceptionHandler 攔截更精確的 BusinessException，
 * 一樣拿得到分類後的例外類型跟自訂狀態碼。
 *
 * ⚠️ 這是新架構的起點，不是一次性全部遷移完成：目前只有部分 service
 * （MemberImportService、PetService、LineBindService、WalletService、
 * TopUpService）已經改用這套新例外，其餘 service（尤其 AppointmentService、
 * PaymentService，throw 的地方非常多）暫時維持丟 IllegalArgumentException，
 * GlobalApiExceptionHandler 對兩種都有處理，行為不會壞，之後可以逐步遷移。
 */
@Getter
public class BusinessException extends IllegalArgumentException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
