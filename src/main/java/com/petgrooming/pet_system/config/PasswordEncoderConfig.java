package com.petgrooming.pet_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密碼雜湊設定
 * 只使用 spring-security-crypto 這個輕量模組提供的 BCryptPasswordEncoder，
 * 不引入完整的 spring-boot-starter-security，不會影響現有的 JWT + Interceptor 認證機制。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
