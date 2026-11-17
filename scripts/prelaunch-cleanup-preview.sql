-- 上線前測試資料清除：預覽（只讀，不改任何資料）
--
-- 執行 prelaunch-cleanup.sql 之前先跑這支，確認要刪掉的筆數、要留下的員工帳號。
-- 用法：railway connect MySQL 進入 mysql 用戶端後，輸入
--   source C:/路徑/scripts/prelaunch-cleanup-preview.sql

-- 1. 會被清空的資料
SELECT '顧客帳號（role = CUSTOMER）' AS 項目, COUNT(*) AS 筆數 FROM users WHERE role = 'CUSTOMER'
UNION ALL SELECT '毛孩', COUNT(*) FROM pets
UNION ALL SELECT '預約', COUNT(*) FROM appointments
UNION ALL SELECT '預約項目', COUNT(*) FROM appointment_items
UNION ALL SELECT '預約與服務項目關聯', COUNT(*) FROM appointment_grooming_items
UNION ALL SELECT '交易紀錄', COUNT(*) FROM transactions
UNION ALL SELECT '現場單', COUNT(*) FROM walk_in_orders
UNION ALL SELECT '現場單項目', COUNT(*) FROM walk_in_order_items
UNION ALL SELECT '錢包', COUNT(*) FROM wallets
UNION ALL SELECT '錢包異動', COUNT(*) FROM wallet_transactions
UNION ALL SELECT '儲值申請', COUNT(*) FROM topup_requests
UNION ALL SELECT '訂金回報', COUNT(*) FROM deposit_payments
UNION ALL SELECT '積分紀錄', COUNT(*) FROM performance_records
UNION ALL SELECT '月結績效', COUNT(*) FROM monthly_performance
UNION ALL SELECT '美容紀錄', COUNT(*) FROM pet_grooming_notes
UNION ALL SELECT '美容照片', COUNT(*) FROM grooming_note_photos
UNION ALL SELECT '洗劑領用紀錄', COUNT(*) FROM supply_usage_records
UNION ALL SELECT '操作紀錄', COUNT(*) FROM operation_logs
UNION ALL SELECT '防重複送出識別碼', COUNT(*) FROM idempotency_keys
UNION ALL SELECT 'LINE 綁定碼', COUNT(*) FROM line_bind_tokens;

-- 2. 會留下的帳號（員工、管理員）。測試時建立、正式不用的員工帳號，清除後到網頁版刪除或停用
SELECT id, username, name, role, line_user_id IS NOT NULL AS 已綁定LINE
FROM users WHERE role <> 'CUSTOMER' ORDER BY role, id;

-- 3. 目前被佔用的時段名額（清除後全部歸零；單日調整過的名額上限會保留）
SELECT slot_date, slot_time, booked, capacity FROM slot_capacity WHERE booked > 0 ORDER BY slot_date, slot_time;

-- 4. 資料庫裡所有資料表（筆數為估計值）。出現上面沒列到的表，先問開發端再清
SELECT table_name, table_rows FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name;
