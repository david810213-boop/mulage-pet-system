package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.dto.DepositPaymentRequest;
import com.petgrooming.pet_system.dto.DepositPreviewResponse;
import com.petgrooming.pet_system.dto.DepositStatusResponse;
import com.petgrooming.pet_system.exception.AuthException;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.BookingDepositService;
import com.petgrooming.pet_system.service.OperationLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顧客端（LIFF）預約訂金：送出前試算、查詢匯款資訊、回報匯款。店家核帳在員工手機版 /m/deposits。
 */
@Tag(name = "預約訂金", description = "顧客端訂金試算、匯款資訊與回報匯款")
@RestController
@RequiredArgsConstructor
public class BookingDepositController {

    private final BookingDepositService bookingDepositService;
    private final OperationLogService operationLogService;

    /** 線上可預約的最早日期（三日後），LIFF 預約頁的日期選擇器用。 */
    @GetMapping("/api/appointments/booking-window")
    public ResponseEntity<Map<String, String>> bookingWindow() {
        return ResponseEntity.ok(Map.of("earliestDate", AppointmentService.earliestOnlineBookingDate().toString()));
    }

    @GetMapping("/api/me/deposits/preview")
    public ResponseEntity<DepositPreviewResponse> preview(@RequestParam Long petId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CurrentUser String username) {
        requireLogin(username);
        return ResponseEntity.ok(bookingDepositService.preview(username, petId, date));
    }

    @GetMapping("/api/me/deposits")
    public ResponseEntity<DepositStatusResponse> status(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CurrentUser String username) {
        requireLogin(username);
        return ResponseEntity.ok(bookingDepositService.status(username, date));
    }

    @PostMapping("/api/me/deposit-payments")
    public ResponseEntity<DepositStatusResponse> submit(@Valid @RequestBody DepositPaymentRequest req,
            @CurrentUser String username) {
        requireLogin(username);
        DepositStatusResponse res = bookingDepositService.submitPayment(username, req);
        operationLogService.logByUsername(username, "APPOINTMENT", "DEPOSIT_SUBMIT",
                "預約日期 " + req.date(), "$" + res.pendingAmount() + "（末五碼 " + req.lastFiveDigits() + "）");
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    private static void requireLogin(String username) {
        if (username == null) {
            throw new AuthException("請先登入");
        }
    }
}
