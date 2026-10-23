package com.petgrooming.pet_system.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 整合測試共用基底：整個測試執行期間共用同一個 MySQL 8 容器。
 *
 * 連線網址沿用正式環境的參數（時區 Asia/Taipei、UTF-8），讓測試與正式站的日期與編碼行為一致。
 * 各測試自行建立所需資料，並使用不會與其他測試衝突的唯一值（見 TestData）。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("petdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Taipei&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
