package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.model.AppointmentItem;
import lombok.Data;

@Data
public class AppointmentItemResponse {
    private Long id;
    private Long appointmentId;
    private String appointmentCode;
    private String petName;
    private String itemName;
    private int price;
    private double points;
    private boolean operatorFilled;
    private String operatorName;

    public static AppointmentItemResponse from(AppointmentItem item) {
        AppointmentItemResponse res = new AppointmentItemResponse();
        res.setId(item.getId());
        res.setAppointmentId(item.getAppointment().getId());
        res.setAppointmentCode(String.format("AP%03d", item.getAppointment().getId()));
        res.setPetName(item.getAppointment().getPetName());
        res.setItemName(item.getItemName());
        res.setPrice(item.getPrice());
        res.setPoints(item.getPoints());
        res.setOperatorFilled(item.isOperatorFilled());
        res.setOperatorName(item.getOperatorStaff() != null ? item.getOperatorStaff().getName() : null);
        return res;
    }
}
