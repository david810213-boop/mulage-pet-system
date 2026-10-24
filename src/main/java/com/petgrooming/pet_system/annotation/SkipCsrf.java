package com.petgrooming.pet_system.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 略過 CSRF Token 檢查。CsrfInterceptor 預設會檢查所有以 Cookie 登入的寫入請求，
 * 只有確定無法帶 token、且已用其他方式驗證來源的端點（例如外部平台的回呼）才標這個註解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipCsrf {
}
