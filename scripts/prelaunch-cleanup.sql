-- 上線前測試資料清除（只在正式上線前執行一次）
--
-- 刪除：所有顧客帳號與其毛孩、預約、交易、現場單、錢包、儲值申請、訂金、積分、美容紀錄、
--       零售商品與店用洗劑（品項與庫存都是測試資料，店家上線前以 CSV 重新匯入）、洗劑領用、
--       操作紀錄；時段已預約數歸零；舊 LINE 搬家時留下的備份表。
-- 保留：員工與管理員帳號、服務項目與價目、時段容量範本與單日上限、公休設定、匯款帳戶、
--       乙方簽名檔、獎勵級距、Flyway 遷移紀錄。
--
-- 執行順序：
--   1. 先備份（Railway 備份服務按 Run now，確認雲端硬碟有新檔案）
--   2. 跑 prelaunch-cleanup-preview.sql 確認筆數
--   3. railway connect MySQL，輸入 source C:/路徑/scripts/prelaunch-cleanup.sql
--   4. 看最後的檢查結果，每一列都要是 0
--
-- 刪除在同一個交易內，中途任何一句失敗會停在錯誤處、不會 COMMIT。
-- 這時輸入 ROLLBACK; 再回報錯誤訊息，資料不會被刪一半。
-- CI 的 PrelaunchCleanupScriptTest 會用實際的資料表結構（含外鍵）跑過這支腳本。

START TRANSACTION;

DELETE FROM grooming_note_photos;
DELETE FROM pet_grooming_notes;
DELETE FROM performance_records;
DELETE FROM monthly_performance;
DELETE FROM wallet_transactions;
DELETE FROM transactions;
DELETE FROM deposit_payments;
DELETE FROM appointment_items;
DELETE FROM appointment_grooming_items;
DELETE FROM appointments;
DELETE FROM walk_in_order_items;
DELETE FROM walk_in_orders;
DELETE FROM topup_requests;
DELETE FROM wallets;
DELETE FROM pets;
DELETE FROM supply_usage_records;
DELETE FROM retail_products;
DELETE FROM store_supplies;
DELETE FROM idempotency_keys;
DELETE FROM line_bind_tokens;
DELETE FROM operation_logs;
DELETE FROM users WHERE role = 'CUSTOMER';

-- 員工帳號留下，但測試期間累積的登入失敗次數與鎖定一併清掉
UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE role <> 'CUSTOMER';

-- 預約都刪了，已預約數要跟著歸零，否則名額會一直被佔住；單日調整過的上限（capacity）保留
UPDATE slot_capacity SET booked = 0 WHERE booked <> 0;

COMMIT;

-- 以下是結構變更，MySQL 會自動提交，所以放在交易之外
DROP TABLE IF EXISTS users_line_backup_20260927;

-- 預約編號 AP001 由 id 產生，清空後讓正式資料從 1 開始
ALTER TABLE appointments AUTO_INCREMENT = 1;
ALTER TABLE walk_in_orders AUTO_INCREMENT = 1;
ALTER TABLE transactions AUTO_INCREMENT = 1;
ALTER TABLE topup_requests AUTO_INCREMENT = 1;
ALTER TABLE deposit_payments AUTO_INCREMENT = 1;
ALTER TABLE retail_products AUTO_INCREMENT = 1;
ALTER TABLE store_supplies AUTO_INCREMENT = 1;

-- 檢查：每一列都要是 0
SELECT '顧客帳號' AS 項目, COUNT(*) AS 剩餘 FROM users WHERE role = 'CUSTOMER'
UNION ALL SELECT '毛孩', COUNT(*) FROM pets
UNION ALL SELECT '預約', COUNT(*) FROM appointments
UNION ALL SELECT '交易紀錄', COUNT(*) FROM transactions
UNION ALL SELECT '現場單', COUNT(*) FROM walk_in_orders
UNION ALL SELECT '錢包', COUNT(*) FROM wallets
UNION ALL SELECT '儲值申請', COUNT(*) FROM topup_requests
UNION ALL SELECT '訂金回報', COUNT(*) FROM deposit_payments
UNION ALL SELECT '積分紀錄', COUNT(*) FROM performance_records
UNION ALL SELECT '零售商品', COUNT(*) FROM retail_products
UNION ALL SELECT '店用洗劑', COUNT(*) FROM store_supplies
UNION ALL SELECT '被佔用的時段名額', COALESCE(SUM(booked), 0) FROM slot_capacity
UNION ALL SELECT '舊 LINE 備份表', COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'users_line_backup_20260927';

-- 留下的員工帳號
SELECT id, username, name, role FROM users ORDER BY role, id;
