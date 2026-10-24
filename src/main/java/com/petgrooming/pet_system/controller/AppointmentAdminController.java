package com.petgrooming.pet_system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.AppointmentAdminResponse;
import com.petgrooming.pet_system.dto.AppointmentNoteRequest;
import com.petgrooming.pet_system.dto.AppointmentResponse;
import com.petgrooming.pet_system.dto.ConfirmAppointmentRequest;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.AppointmentQueryService;
import com.petgrooming.pet_system.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 店家後台預約管理 API
 *
 * GET  /api/admin/appointments               所有預約（含店家內部備注）
 * POST /api/admin/appointments/{id}/confirm  確認預約並敲定最後時間
 * PUT  /api/admin/appointments/{id}/notes    設定備注（internalNote / memberNote）
 */
@Tag(name = "管理端：預約", description = "預約清單、確認預約、備註")
@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
public class AppointmentAdminController {

    private final AppointmentService appointmentService;
    private final AppointmentQueryService appointmentQueryService;

    // 後台清單，含 internalNote
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @GetMapping
    public ResponseEntity<List<AppointmentAdminResponse>> getAll() {
        return ResponseEntity.ok(appointmentQueryService.getAllForAdmin());
    }

    // 確認預約並敲定最後時間
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id,
                                     @RequestBody(required = false) ConfirmAppointmentRequest req,
                                     @CurrentUser String username) {
        AppointmentResponse res = appointmentService.confirm(
                id, req != null ? req.getConfirmedTime() : null, username);
        return ResponseEntity.ok(res);
    }

    // 設定雙可見性備注
    @RequireRole({UserRole.ADMIN, UserRole.STAFF})
    @PutMapping("/{id}/notes")
    public ResponseEntity<?> setNotes(@PathVariable Long id,
                                      @RequestBody AppointmentNoteRequest req,
                                      @CurrentUser String username) {
        AppointmentAdminResponse res = appointmentService.setNotes(
                id, req.getInternalNote(), req.getMemberNote(), username);
        return ResponseEntity.ok(res);
    }
}
