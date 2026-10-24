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
 * 資料種子／回填遷移執行器（只管「資料」，不管 schema）：在 Hibernate 依 ddl-auto 建好資料表之後，
 * 才手動執行 Flyway.migrate()，並排在 DataInitializer 之前。
 * 為什麼不用 Spring Boot 內建的 Flyway 自動啟動，見 docs/adr/0001-flyway-after-hibernate.md。
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
            log.info("[資料遷移] 本次啟動執行了 {} 個新的資料遷移檔案：{}",
                    result.migrationsExecuted,
                    result.migrations.stream().map(m -> m.version).toList());
        } else {
            log.info("[資料遷移] 沒有新的遷移檔案需要執行（所有版本都已套用過）");
        }
    }
}
