package com.petgrooming.pet_system.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入目前登入者（由 LoginInterceptor 驗證 JWT 後取得），取代在 controller 裡讀 request 屬性。
 *
 * 支援的參數型別：
 * <ul>
 *   <li>{@code @CurrentUser String username}：登入帳號</li>
 *   <li>{@code @CurrentUser User user}：登入者實體</li>
 * </ul>
 * 未登入、或帳號已不存在時注入 null；需要登入的路徑已由 LoginInterceptor 擋下，正常情況不會是 null。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
