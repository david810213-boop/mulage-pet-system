package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.enums.PetType;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.exception.AuthException;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.AppointmentDetailService;
import com.petgrooming.pet_system.service.ClosedDateService;
import com.petgrooming.pet_system.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentDetailService appointmentDetailService;
    private final OperationLogService operationLogService;
    private final ClosedDateService closedDateService;

    // ── POST /api/appointments ─────────────────────────────────────────────
    // 建立預約（對應原本 bookAppointment），身分取自 JWT，店家網頁與顧客 LINE 共用
    @PostMapping
    public ResponseEntity<?> book(
            @RequestBody AppointmentRequest req,
            @CurrentUser String username) {
        AppointmentResponse res = appointmentService.book(req, username);
        operationLogService.logByUsername(username, "APPOINTMENT", "BOOK",
                "預約 " + res.getAppointmentCode(), res.getPetName());
        return ResponseEntity.ok(res);
    }

    // ── GET /api/appointments/my ───────────────────────────────────────────
    // 查詢自己的預約紀錄（對應原本 viewAppointments）
    @GetMapping("/my")
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(@CurrentUser String username) {
        return ResponseEntity.ok(appointmentQueryService.getMyAppointments(username));
    }

    // ── GET /api/appointments ──────────────────────────────────────────────
    // 查詢所有預約（STAFF / ADMIN，對應原本 viewAllAppointments）
    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAllAppointments() {
        return ResponseEntity.ok(appointmentQueryService.getAllAppointments());
    }

    // ── POST /api/appointments/{id}/cancel ─────────────────────────────────
    // 取消預約：顧客可取消自己的未結帳預約，店家/員工可取消任何人的未結帳預約
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelAppointmentRequest req,
            @CurrentUser String username) {
        AppointmentResponse res = appointmentService.cancel(id, req, username);
        operationLogService.logByUsername(username, "APPOINTMENT", "CANCEL",
                "預約 " + res.getAppointmentCode(),
                req != null && req.getReason() != null ? req.getReason() : "顧客自行取消");
        return ResponseEntity.ok(res);
    }

    // ── GET /api/appointments/slots?date=2025-06-01[&petType=DOG] ──────────
    // 查詢某天可預約時段（對應原本 generateDailySlots + getAvailableSlots）。
    // 帶入 petType 時，該時段限定的物種跟這隻寵物不符
    // 也會反映在 available 上；petType 格式不合法（不是 DOG/CAT）就當作沒帶，
    // 不讓前端傳壞值時整支 API 500。
    @GetMapping("/slots")
    public ResponseEntity<List<TimeSlotResponse>> getAvailableSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String petType) {
        PetType parsedPetType = null;
        if (petType != null && !petType.isBlank()) {
            try {
                parsedPetType = PetType.valueOf(petType.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 不合法的值當作沒帶，不篩選物種
            }
        }
        // 過去的日期一律沒有可預約時段（預約頁專用；
        // 員工時段管理走 MobileScheduleController，不受影響）
        if (date.isBefore(LocalDate.now())) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(appointmentQueryService.getAvailableSlots(date, parsedPetType));
    }

    // ── GET /api/appointments/first-visit?petId= ─────────────────────────
    // LIFF 預約頁選好毛孩後查詢是不是初次預約
    // （初次只選時段，不選服務項目）。
    @GetMapping("/first-visit")
    public ResponseEntity<?> firstVisit(@RequestParam Long petId, @CurrentUser String username) {
        if (username == null) {
            throw new AuthException("請先登入");
        }
        return ResponseEntity.ok(appointmentQueryService.firstVisitStatus(username, petId));
    }

    // ── GET /api/appointments/closed-dates ───────────────────────────────
    // 查詢今天以後的公休日清單，供 LIFF 預約頁在選日期後即時提示「公休」，
    // 不用等 /slots 回傳空清單才知道，UX 更明確。
    @GetMapping("/closed-dates")
    public ResponseEntity<List<String>> getClosedDates() {
        return ResponseEntity.ok(
                closedDateService.listUpcoming().stream()
                        .map(cd -> cd.getDate().toString())
                        .toList());
    }

    // ── GET /api/appointments/{id}/contract ────────────────────────────────
    // 契約副本——「雙方各執一份契約正本」，顧客在 LIFF「我的預約」
    // 可以隨時查看這筆預約簽署的契約紀錄（簽名、時間、版本）；店員也可以查。
    @GetMapping("/{id}/contract")
    public ResponseEntity<?> getContract(@PathVariable Long id, @CurrentUser String username) {
        return ResponseEntity.ok(appointmentQueryService.getContractRecord(id, username));
    }

    // ── GET /api/appointments/{id}/detail ──────────────────────────────────
    // 取得某筆預約的完整消費明細（服務項目、金額、付款方式、經手人等），供 LIFF 點擊查看用
    @GetMapping("/{id}/detail")
    public ResponseEntity<?> getDetail(@PathVariable Long id, @CurrentUser String username) {
        return ResponseEntity.ok(appointmentDetailService.getAppointmentDetail(id, username));
    }
}
