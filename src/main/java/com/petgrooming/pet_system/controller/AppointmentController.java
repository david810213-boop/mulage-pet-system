package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.CancelAppointmentRequest;
import com.petgrooming.pet_system.dto.TimeSlotResponse;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final OperationLogService operationLogService;

    // 從 LoginInterceptor 解析 JWT 後存入的 request attribute 取得目前登入者
    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute("tokenUsername");
    }

    // ── POST /api/appointments ─────────────────────────────────────────────
    // 建立預約（對應原本 bookAppointment），身分取自 JWT，店家網頁與顧客 LINE 共用
    @PostMapping
    public ResponseEntity<?> book(
            @RequestBody AppointmentRequest req,
            HttpServletRequest request) {
        try {
            AppointmentResponse res = appointmentService.book(req, currentUsername(request));
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            // 時間超出營業時間、時段重疊等錯誤
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── GET /api/appointments/my ───────────────────────────────────────────
    // 查詢自己的預約紀錄（對應原本 viewAppointments）
    @GetMapping("/my")
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(HttpServletRequest request) {
        return ResponseEntity.ok(appointmentService.getMyAppointments(currentUsername(request)));
    }

    // ── GET /api/appointments ──────────────────────────────────────────────
    // 查詢所有預約（STAFF / ADMIN，對應原本 viewAllAppointments）
    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAllAppointments() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    // ── POST /api/appointments/{id}/cancel ─────────────────────────────────
    // 取消預約：顧客可取消自己的未結帳預約，店家/員工可取消任何人的未結帳預約
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelAppointmentRequest req,
            HttpServletRequest request) {
        try {
            AppointmentResponse res = appointmentService.cancel(id, req, currentUsername(request));
            operationLogService.logByUsername(currentUsername(request), "APPOINTMENT", "CANCEL",
                    "預約 " + res.getAppointmentCode(),
                    req != null && req.getReason() != null ? req.getReason() : "顧客自行取消");
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── GET /api/appointments/slots?date=2025-06-01 ────────────────────────
    // 查詢某天可預約時段（對應原本 generateDailySlots + getAvailableSlots）
    @GetMapping("/slots")
    public ResponseEntity<List<TimeSlotResponse>> getAvailableSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.getAvailableSlots(date));
    }

    // ── GET /api/appointments/{id}/detail ──────────────────────────────────
    // 取得某筆預約的完整消費明細（服務項目、金額、付款方式、經手人等），供 LIFF 點擊查看用
    @GetMapping("/{id}/detail")
    public ResponseEntity<?> getDetail(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(appointmentService.getAppointmentDetail(id, currentUsername(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
