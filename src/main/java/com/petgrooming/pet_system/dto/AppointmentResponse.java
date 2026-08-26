package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.GroomingItem;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// API 回傳給前端的格式（對應原本 AppointmentReceipt.toString()）
@Data
public class AppointmentResponse {
    private Long id;
    private String appointmentCode;     // 顯示用的編號，例如 AP001
    private String ownerName;           // 飼主姓名
    private String ownerEmail;          // 飼主 Email
    private String petName;
    private String petType;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private List<GroomingItem> selectedItems;
    // 需求（追加，2026-08-26 修正）：預約列表頁專用的「實際項目」顯示名稱——
    // 這隻寵物到店核對時如果被店員改過項目（例如從基礎定制調理升級成中階
    // 定制調理），selectedItems 是預約當下的原始選擇，不會跟著核對結果更新，
    // 導致列表頁跟消費明細顯示不一致（明細正確，列表還是舊的）。
    // 這個欄位由 AppointmentService 組裝時判斷：核對過（有 AppointmentItem
    // 紀錄）就用核對後的實際項目名稱，還沒核對就照舊用 selectedItems 的名稱，
    // 列表頁的畫面改讀這個欄位，不要再直接讀 selectedItems。
    private List<String> displayItemNames;
    private int totalAmount;
    private boolean paid;
    private AppointmentStatus status;
    private String statusLabel;
    private LocalDateTime confirmedTime; // 需求 3：店家敲定的最後時間
    private String memberNote;           // 需求 7：會員可見備注（不含店家內部備注）
    private boolean cancelled;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String cancelledBy;
    private boolean finalCheckDone; // 進行中核對是否已完成（結帳按鈕的顯示要用到，會員自助結帳時也需要）

    // 從 Entity 轉成 DTO 的靜態工廠方法
    public static AppointmentResponse from(Appointment a) {
        AppointmentResponse res = new AppointmentResponse();
        res.setId(a.getId());
        // 用 id 組出 AP001 格式的顯示用編號
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
        // ⚠️ 需求 7：只放 memberNote，internalNote 絕不放進顧客可見的 DTO
        res.setMemberNote(a.getMemberNote());
        res.setCancelled(a.isCancelled());
        res.setCancelledAt(a.getCancelledAt());
        res.setCancelReason(a.getCancelReason());
        res.setCancelledBy(a.getCancelledBy());
        res.setFinalCheckDone(a.isFinalCheckDone());
        return res;
    }
}