package com.petgrooming.pet_system.config;

import com.petgrooming.pet_system.interceptor.CsrfInterceptor;
import com.petgrooming.pet_system.interceptor.LoginInterceptor;
import com.petgrooming.pet_system.interceptor.RoleInterceptor;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 攔截順序：
 * order(1) LoginInterceptor → 確認是否登入
 * order(2) RoleInterceptor → 確認角色是否有權限（讀 @RequireRole）
 * order(3) CsrfInterceptor → 確認 CSRF token 是否正確（讀 @RequireCsrf，
 *          只標在高風險端點上，需求見 2026-09-17 追加說明）
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

        private final LoginInterceptor loginInterceptor;
        private final RoleInterceptor roleInterceptor;
        private final CsrfInterceptor csrfInterceptor;

        // ngrok 免費版會在第一次訪問時顯示安全警告頁面，加上這個 header 讓 ngrok 跳過該頁面
        // 讓 LIFF webview 能直接載入頁面，不被中途攔截
        @Bean
        public FilterRegistrationBean<Filter> ngrokSkipWarningFilter() {
                FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
                bean.setFilter((ServletRequest req, ServletResponse res, FilterChain chain) -> {
                        ((HttpServletResponse) res).setHeader("ngrok-skip-browser-warning", "true");
                        chain.doFilter(req, res);
                });
                bean.addUrlPatterns("/*");
                bean.setOrder(1);
                return bean;
        }

        // 打開網站根目錄（不帶任何路徑）時，導向後台首頁；
        // 未登入的話會照常被 LoginInterceptor 攔截、轉去登入頁，行為跟直接打 /dashboard 一致。
        @Override
        public void addViewControllers(@NonNull ViewControllerRegistry registry) {
                registry.addRedirectViewController("/", "/dashboard");
        }

        @Override
        public void addInterceptors(@NonNull InterceptorRegistry registry) {

                // 1. 登入檢查（優先，所有路徑）
                registry.addInterceptor(loginInterceptor)
                                .addPathPatterns("/**")
                                .excludePathPatterns(
                                                "/auth/login",
                                                "/auth/login/submit",
                                                "/auth/register",
                                                "/auth/register/submit",
                                                "/auth/logout",
                                                "/api/line/login",
                                                "/api/line/bind",
                                                "/test/**",
                                                "/liff/**",
                                                "/css/**",
                                                "/js/**",
                                                "/images/**",
                                                "/static/**",
                                                "/error",
                                                "/favicon.ico")
                                .order(1);

                // 2. 角色權限（在登入檢查之後）
                // 攔截全部路徑，只對有 @RequireRole 的 Controller 方法生效
                registry.addInterceptor(roleInterceptor)
                                .addPathPatterns("/**")
                                .excludePathPatterns(
                                                "/auth/**",
                                                "/api/line/login",
                                                "/api/line/bind",
                                                "/test/**",
                                                "/liff/**",
                                                "/css/**", "/js/**", "/images/**", "/static/**",
                                                "/error", "/favicon.ico")
                                .order(2);

                // 3. CSRF Token 驗證（在登入/角色檢查之後）
                // 攔截全部路徑，只對有 @RequireCsrf 的 Controller 方法生效
                // （目前只標在高風險端點：密碼變更、建立員工帳號、帳號合併、
                // 手動儲值、刪除操作，見各該注解使用處的說明）
                registry.addInterceptor(csrfInterceptor)
                                .addPathPatterns("/**")
                                .excludePathPatterns(
                                                "/auth/**",
                                                "/api/line/login",
                                                "/api/line/bind",
                                                "/test/**",
                                                "/liff/**",
                                                "/css/**", "/js/**", "/images/**", "/static/**",
                                                "/error", "/favicon.ico")
                                .order(3);
        }
}
