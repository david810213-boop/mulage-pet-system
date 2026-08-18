package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.model.LineBindToken;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.LineBindTokenRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 店員/店家綁定 LINE 帳號——產生一次性驗證碼、驗證碼兌換綁定。
 */
@Service
@RequiredArgsConstructor
public class LineBindService {

    private static final int CODE_EXPIRY_MINUTES = 10;
    private final LineBindTokenRepository lineBindTokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    // 產生一組 6 位數驗證碼給指定店員帳號使用
    @Transactional
    public String generateCode(String targetUsername) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        lineBindTokenRepository.save(LineBindToken.builder()
                .code(code)
                .targetUsername(targetUsername)
                .build());
        return code;
    }

    // 手機那端輸入驗證碼 + LINE userId，兌換綁定
    @Transactional
    public User bindByCode(String code, String lineUserId) {
        LineBindToken token = lineBindTokenRepository.findByCodeAndUsedFalse(code)
                .orElseThrow(() -> new IllegalArgumentException("驗證碼不存在或已使用過，請回後台重新產生"));

        long minutesElapsed = ChronoUnit.MINUTES.between(token.getCreatedAt(), LocalDateTime.now());
        if (minutesElapsed > CODE_EXPIRY_MINUTES) {
            throw new IllegalArgumentException("驗證碼已過期（超過 " + CODE_EXPIRY_MINUTES + " 分鐘），請回後台重新產生");
        }

        User user = userRepository.findByUsername(token.getTargetUsername())
                .orElseThrow(() -> new IllegalArgumentException("找不到對應的帳號"));

        user.setLineUserId(lineUserId);
        userRepository.save(user);

        token.setUsed(true);
        lineBindTokenRepository.save(token);

        return user;
    }
}
