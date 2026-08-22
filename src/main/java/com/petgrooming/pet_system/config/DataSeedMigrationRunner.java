package com.petgrooming.pet_system.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 資料種子/回填遷移執行器（只管「資料」，不管 schema）。
 *
 * 為什麼不用 Spring Boot 內建的 Flyway 自動啟動機制（application.yml 已設
 * spring.flyway.enabled=false 關掉）：
 * Spring Boot 預設會讓 Flyway 在整個應用程式啟動最早期執行——早於 Hibernate
 * 依 ddl-auto=update 建立資料表結構之前。這個專案的 schema 仍然交給 Hibernate
 * 自動管理（沒有改成 Flyway 全面接管，那是更大幅度的架構調整），如果放任
 * Flyway 用預設時機執行，在一個全新的資料庫上（例如本機第一次建立、或未來
 * 換一顆新資料庫）執行 db/migration 底下的 INSERT 語句時，對應的資料表根本
 * 還不存在，會直接執行失敗。
 *
 * 解法：關掉自動機制，改成這個 ApplicationRunner 手動呼叫 Flyway.migrate()。
 * ApplicationRunner 保證在整個 Spring ApplicationContext 完全初始化完成之後
 * 才執行——這代表 EntityManagerFactory（連帶 Hibernate 的 ddl-auto 建表動作）
 * 一定已經跑完，此時資料表都已經就緒，遷移檔案裡的 INSERT/UPDATE 才不會撲空。
 *
 * @Order(Ordered.HIGHEST_PRECEDENCE) 確保這個 Runner 比 DataInitializer 更早
 * 執行——DataInitializer 裡的「偵測預設密碼未修改」等檢查邏輯，順序上不依賴
 * 這裡的遷移內容，但讓資料遷移永遠排在最前面，是比較不會出錯的保守做法。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class DataSeedMigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;

    public DataSeedMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .table("flyway_schema_history")
                .load();

        var result = flyway.migrate();
        if (result.migrationsExecuted > 0) {
            log.info("✨ [資料遷移] 本次啟動執行了 {} 個新的資料遷移檔案：{}",
                    result.migrationsExecuted,
                    result.migrations.stream().map(m -> m.version).toList());
        } else {
            log.info("[資料遷移] 沒有新的遷移檔案需要執行（所有版本都已套用過）");
        }
    }
}
