package com.petgrooming.pet_system.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需求（追加，2026-09-17）：CSRF Token 防護注解。
 *
 * 只標記在店家後台「高風險端點」上（密碼變更、建立員工帳號、帳號合併、
 * 手動儲值、刪除類操作），不是全站套用——這個系統沒有用 Spring Security，
 * Cookie 已經有 SameSite=Lax 擋掉最常見的跨站表單偽造，全站每個表單/fetch
 * 都補 token 的工程量太大，投報率不高。這裡改成只挑後果最嚴重、最值得
 * 額外加一層防護的端點標記這個注解，由 CsrfInterceptor 檢查。
 *
 * 使用方式：@RequireCsrf 標在 Controller 方法上，呼叫端要在 header
 * X-CSRF-Token 或表單欄位 _csrf 帶上跟登入時拿到的 XSRF-TOKEN Cookie
 * 一致的值，否則會被拒絕。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireCsrf {
}
