package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.enums.BankAccountPurpose;
import com.petgrooming.pet_system.model.BankAccountInfo;
import com.petgrooming.pet_system.model.CompanySignature;
import com.petgrooming.pet_system.model.PricingSettings;
import com.petgrooming.pet_system.repository.BankAccountInfoRepository;
import com.petgrooming.pet_system.repository.CompanySignatureRepository;
import com.petgrooming.pet_system.repository.PricingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 店家設定：匯款帳號與收款 QR Code、乙方電子簽名檔、體重定價門檻。
 */
@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private final BankAccountInfoRepository bankAccountInfoRepository;
    private final CloudinaryService cloudinaryService; // 需求 21：匯款/儲值 QR Code 上傳
    private final CompanySignatureRepository companySignatureRepository;
    private final PricingSettingsRepository pricingSettingsRepository; // 需求（追加）：體重定價門檻

    // ── 店家匯款帳號資訊（依用途分開：結帳收款 / 儲值金收款大額專用）────
    // 需求（追加）：LIFF 儲值金常有大額匯款，跟現場結帳的日常小額收款要分開帳戶方便對帳。
    public BankAccountInfo getBankAccountInfo(
            BankAccountPurpose purpose) {
        return bankAccountInfoRepository.findByPurpose(purpose)
                .orElse(BankAccountInfo.builder()
                        .purpose(purpose)
                        .bankName("尚未設定").accountNumber("尚未設定").accountHolder("尚未設定").build());
    }

    @Transactional
    public void updateBankAccountInfo(BankAccountPurpose purpose,
                                      String bankName, String accountNumber, String accountHolder) {
        BankAccountInfo info = bankAccountInfoRepository.findByPurpose(purpose)
                .orElse(BankAccountInfo.builder().purpose(purpose).build());
        info.setBankName(bankName);
        info.setAccountNumber(accountNumber);
        info.setAccountHolder(accountHolder);
        bankAccountInfoRepository.save(info);
    }

    // ── 需求 21：上傳/更換匯款收款 QR Code（依用途各自獨立一張）──────────
    @Transactional
    public void updateBankAccountQrCode(BankAccountPurpose purpose,
                                        MultipartFile file) {
        BankAccountInfo info = bankAccountInfoRepository.findByPurpose(purpose)
                .orElse(BankAccountInfo.builder().purpose(purpose).build());

        CloudinaryService.UploadResult result = cloudinaryService.upload(file, "bank-account");

        String oldPublicId = info.getQrCodePublicId();
        info.setQrCodeUrl(result.url());
        info.setQrCodePublicId(result.publicId());
        bankAccountInfoRepository.save(info);

        cloudinaryService.deleteQuietly(oldPublicId);
    }

    // ── 需求 22：乙方（店家）固定電子簽名檔，顯示在顧客端契約最下方 ──────
    public String getCompanySignatureImage() {
        return companySignatureRepository.findFirstByOrderByIdDesc()
                .map(CompanySignature::getSignatureImage)
                .orElse(null);
    }

    @Transactional
    public void updateCompanySignature(String base64Image) {
        CompanySignature sig = companySignatureRepository.findFirstByOrderByIdDesc()
                .orElse(CompanySignature.builder().build());
        sig.setSignatureImage(base64Image);
        companySignatureRepository.save(sig);
    }

    // ── 需求（追加）：體重定價門檻設定（單例，同 CompanySignature 做法）─────
    public PricingSettings getPricingSettings() {
        return pricingSettingsRepository.findFirstByOrderByIdDesc()
                .orElse(PricingSettings.builder().build());
    }

    @Transactional
    public void updatePricingSettings(PricingSettings updated) {
        PricingSettings settings = pricingSettingsRepository.findFirstByOrderByIdDesc()
                .orElse(PricingSettings.builder().build());
        settings.setCatBaseWeightLimit(updated.getCatBaseWeightLimit());
        settings.setCatMidWeightLimit(updated.getCatMidWeightLimit());
        settings.setCatMidSurcharge(updated.getCatMidSurcharge());
        settings.setCatHighWeightLimit(updated.getCatHighWeightLimit());
        settings.setCatHighSurcharge(updated.getCatHighSurcharge());
        settings.setDogLongCoatWeightLimit(updated.getDogLongCoatWeightLimit());
        settings.setDogShortCoatWeightLimit(updated.getDogShortCoatWeightLimit());
        pricingSettingsRepository.save(settings);
    }
}
