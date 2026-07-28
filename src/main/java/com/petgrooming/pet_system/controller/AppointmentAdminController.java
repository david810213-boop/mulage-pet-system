package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.AppointmentNoteRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.ConfirmAppointmentRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.AppointmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 店家後台預約管理 API（需求 3、7）
 *
 * GET  /api/admin/appointments               所有預約（含店家內部備注）
 * POST /api/admin/appointments/{id}/confirm  確認預約並敲定最後時間
 * PUT  /api/admin/appointments/{id}/notes    設定備注（internalNote / memberNote）
 */
@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
public class AppointmentAdminController {

    private final AppointmentService appointmentService;

    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute("tokenUsername");
    }

    // 需求 7：後台清單，含 internalNote
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public ResponseEntity<List<AppointmentAdminResponse>> getAll() {
        return ResponseEntity.ok(appointmentService.getAllForAdmin());
    }

    // 需求 3：確認預約並敲定最後時間
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id,
                                     @RequestBody(required = false) ConfirmAppointmentRequest req,
                                     HttpServletRequest request) {
        try {
            AppointmentResponse res = appointmentService.confirm(
                    id, req != null ? req.getConfirmedTime() : null, currentUsername(request));
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 需求 7：設定雙可見性備注
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{id}/notes")
    public ResponseEntity<?> setNotes(@PathVariable Long id,
                                      @RequestBody AppointmentNoteRequest req,
                                      HttpServletRequest request) {
        try {
            AppointmentAdminResponse res = appointmentService.setNotes(
                    id, req.getInternalNote(), req.getMemberNote(), currentUsername(request));
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
