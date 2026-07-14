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
    private int totalAmount;
    private boolean paid;
    private AppointmentStatus status;
    private String statusLabel;
    private boolean cancelled;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String cancelledBy;

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
        res.setCancelled(a.isCancelled());
        res.setCancelledAt(a.getCancelledAt());
        res.setCancelReason(a.getCancelReason());
        res.setCancelledBy(a.getCancelledBy());
        return res;
    }
}