-- 預約狀態新增「待付訂金」。appointments.status 若是 Hibernate 早期建出的 MySQL 原生 ENUM，
-- ddl-auto=update 不會擴充允許值，寫入新狀態會報 Data truncated，因此改成 VARCHAR（見 docs/adr/0006-varchar-enum.md）。
-- 已經是 VARCHAR 的資料庫（例如測試環境新建的）執行這行也不會改變資料。
ALTER TABLE appointments MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED';
