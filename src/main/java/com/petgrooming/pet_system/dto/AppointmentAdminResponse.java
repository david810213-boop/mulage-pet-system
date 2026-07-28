package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 需求 7：店家後台專用的預約回傳格式。
 * 與顧客端 AppointmentResponse 的差別：這裡「額外」包含 internalNote（店家內部備注）。
 * 此 DTO 只會由 RequireRole(ADMIN/STAFF) 的端點使用，不會出現在任何顧客 API。
 */
@Data
public class AppointmentAdminResponse {
    private Long id;
    private String appointmentCode;
    private String ownerName;
    private String ownerEmail;
    private String petName;
    private String petType;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private List<GroomingItem> selectedItems;
    private int totalAmount;
    private boolean paid;
    private AppointmentStatus status;
    private String statusLabel;
    private LocalDateTime confirmedTime;
    private String internalNote;  // ★ 店家內部備注（僅後台）
    private String memberNote;    // 會員可見備注
    private boolean cancelled;
    private String cancelledBy;
    private String cancelReason;
    private boolean finalCheckDone;
    private String finalCheckNote;
    private boolean checkinOrderConfirmed;

    public static AppointmentAdminResponse from(Appointment a) {
        AppointmentAdminResponse res = new AppointmentAdminResponse();
        res.setId(a.getId());
        res.setAppointmentCode(String.format("AP%03d", a.getId()));
        res.setOwnerName(a.getUser().getName());
        res.setOwnerEmail(a.getUser().getUsername());
        res.setPetName(a.getPetName());
        res.setPetType(a.getPetType());
        res.setDate(a.getDate());
        res.setStartTime(a.getStartTime());
        res.setEndTime(a.getEndTime());
        res.setSelectedItems(a.getSelectedItems());
        res.setTotalAmount(a.getTotalAmount());
        res.setPaid(a.isPaid());
        res.setStatus(a.getStatus());
        res.setStatusLabel(a.getStatus().getLabel());
        res.setConfirmedTime(a.getConfirmedTime());
        res.setInternalNote(a.getInternalNote());
        res.setMemberNote(a.getMemberNote());
        res.setCancelled(a.isCancelled());
        res.setCancelledBy(a.getCancelledBy());
        res.setCancelReason(a.getCancelReason());
        res.setFinalCheckDone(a.isFinalCheckDone());
        res.setFinalCheckNote(a.getFinalCheckNote());
        res.setCheckinOrderConfirmed(a.isCheckinOrderConfirmed());
        return res;
    }
}
