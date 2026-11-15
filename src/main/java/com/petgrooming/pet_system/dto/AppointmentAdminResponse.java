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
 * 店家後台專用的預約回傳格式。
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
    // 同 AppointmentResponse 的說明，列表頁畫面
    // 要改讀這個欄位，不要直接讀 selectedItems（那是預約當下的原始選擇，
    // 核對時改過項目不會反映在上面）。
    private List<String> displayItemNames;
    private boolean firstVisitAssessment; // 初次・現場評估（金額現場報價）
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
    private boolean serviceEndedDone;
    private boolean pendingWireTransfer; // 已結帳選匯款，但店家尚未確認收款
    private Long pendingTransactionId;   // 待確認收款的交易紀錄 id

    // ── 預約訂金與改期 ──────────────────────────────────────────────
    private int depositShare;            // 應付訂金份額
    private int depositPaid;             // 已收訂金
    private int depositCredit;           // 尚未折抵或退還的訂金
    private LocalDateTime depositDueAt;  // 待付訂金的付款期限
    private int depositRefundAmount;     // 應退訂金
    private int depositForfeitedAmount;  // 不退（沒收）的訂金
    private String depositRefundStatus;  // NONE / PENDING / DONE
    private String depositRefundStatusLabel;
    private int rescheduleCount;
    private String rescheduledFrom;

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
        res.setFirstVisitAssessment(a.isFirstVisitAssessment());
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
        res.setServiceEndedDone(a.isServiceEndedDone());
        res.setDepositShare(a.getDepositShare());
        res.setDepositPaid(a.getDepositPaid());
        res.setDepositCredit(a.depositCredit());
        res.setDepositDueAt(a.getDepositDueAt());
        res.setDepositRefundAmount(a.getDepositRefundAmount());
        res.setDepositForfeitedAmount(a.getDepositForfeitedAmount());
        res.setDepositRefundStatus(a.getDepositRefundStatus().name());
        res.setDepositRefundStatusLabel(a.getDepositRefundStatus().getLabel());
        res.setRescheduleCount(a.getRescheduleCount());
        res.setRescheduledFrom(a.getRescheduledFrom());
        return res;
    }
}
