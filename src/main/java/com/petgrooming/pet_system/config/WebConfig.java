package com.petgrooming.pet_system.config;

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
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 攔截順序：
 * order(1) LoginInterceptor → 確認是否登入
 * order(2) RoleInterceptor → 確認角色是否有權限（讀 @RequireRole）
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

        private final LoginInterceptor loginInterceptor;
        private final RoleInterceptor roleInterceptor;

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
                                                "/test/**",
                                                "/liff/**",
                                                "/css/**", "/js/**", "/images/**", "/static/**",
                                                "/error", "/favicon.ico")
                                .order(2);
        }
}
