# 0001 服務項目種子資料用 Flyway，但在 Hibernate 建表之後才執行

## 背景
資料表結構由 Hibernate `ddl-auto=update` 管理；服務項目（`GroomingItem`／`GroomingItemComponent`）的初始資料需要版本化、可重複部署。
Spring Boot 內建的 Flyway 自動啟動會在 Hibernate 建表**之前**執行，在全新的資料庫上，遷移檔裡的 INSERT 會因為資料表還不存在而失敗。

## 決策
- `spring.flyway.enabled=false` 關閉自動啟動，改由 `DataSeedMigrationRunner`（`ApplicationRunner`，`@Order(HIGHEST_PRECEDENCE)`）手動呼叫 `Flyway.migrate()`。ApplicationRunner 在整個 ApplicationContext 初始化完成後才執行，此時 Hibernate 已建好資料表。
- Flyway 只管**資料**（`db/migration/Vn__xxx.sql`），不管 schema。
- 店家會在後台持續調整的營運設定（時段容量範本、公休、匯款帳戶等）維持 `existsBy()`／lazy 初始化，不走 Flyway。

## 後果
- 新增服務項目種子資料：寫新的 `Vn__xxx.sql`，不要改 `DataInitializer`。
- 若日後改成 Flyway 全面管理 schema，這個 Runner 可以拿掉，改回 Spring Boot 內建機制。
