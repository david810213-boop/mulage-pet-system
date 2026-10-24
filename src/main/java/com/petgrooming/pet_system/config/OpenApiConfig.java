package com.petgrooming.pet_system.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 文件（Swagger UI：/swagger-ui.html）。預設關閉，開發時設定環境變數 API_DOCS_ENABLED=true 才開放，
 * 正式站不設定這個變數，不會對外公開 API 清單。
 * 在 Swagger UI 試打 API 建議用 Bearer token：以 Cookie 登入的寫入請求還要帶 X-CSRF-Token（見 CsrfInterceptor）。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    public OpenAPI petSystemOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("慕沐村 Mulage Pet API")
                        .description("LIFF 顧客端與員工端共用的 REST API。錯誤回應格式：{ status, detail, code, message, errors? }")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("LINE 登入（POST /api/line/login）或網頁版登入取得的 JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
