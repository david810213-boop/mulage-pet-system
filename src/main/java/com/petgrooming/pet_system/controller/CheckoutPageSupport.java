package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.PaymentMethod;
import com.petgrooming.pet_system.service.CheckoutPreviewService.WalletPreview;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * 結帳頁（預約結帳、現場單結帳，網頁版與手機版）共用的畫面資料組裝。
 */
final class CheckoutPageSupport {

    /** 需求 10：結帳畫面不提供「信用卡」選項（歷史資料仍保留該列舉值，僅畫面隱藏）。 */
    static final List<PaymentMethod> CHECKOUT_PAYMENT_METHODS = Arrays.stream(PaymentMethod.values())
            .filter(m -> m != PaymentMethod.CREDIT_CARD)
            .toList();

    private CheckoutPageSupport() {
    }

    static void addWalletPreview(Model model, WalletPreview preview) {
        model.addAttribute("walletBalance", preview.balance());
        model.addAttribute("walletLowBalance", preview.lowBalance());
        model.addAttribute("walletDiscount", preview.discount());
        model.addAttribute("walletDiscountActive", preview.discountActive());
        model.addAttribute("walletFinalAmount", preview.finalAmount());
    }

    /**
     * 需求（2026-08-24）：狗狗定價流程簡化——結帳完成後，如果這隻狗還沒鎖定固定套餐
     * （幼犬、或成犬還沒被判定定型），跳提醒請店員記得更新體重，因為體重是下次開單自動篩選菜單的依據。
     * 已鎖定的狗不需要提醒。結帳後導向的頁面都有放這個彈窗，讀 flash attribute 顯示。
     */
    static void addWeightUpdateReminder(RedirectAttributes redirectAttributes, PetResponse pet) {
        if (pet != null && pet.getPetType() != null && "DOG".equalsIgnoreCase(pet.getPetType().name())
                && pet.getLockedGroomingItemId() == null) {
            redirectAttributes.addFlashAttribute("weightReminderPetId", pet.getId());
            redirectAttributes.addFlashAttribute("weightReminderPetName", pet.getName());
            redirectAttributes.addFlashAttribute("weightReminderCurrentWeight", pet.getWeight());
        }
    }
}
