-- 11/24～11/25 系統測試資料清除
--
-- 刪除：測試帳號（系統管理員、洪大宝、demo@pet.com）以「顧客身分」產生的預約、現場單、交易紀錄、
--       積分、美容紀錄、錢包與儲值紀錄、儲值申請、訂金回報；沒綁會員且寵物名有「測試」的現場單；
--       測試零售商品 #66、#67；測試洗劑 #1 與領用紀錄；測試代班 #167；
--       測試帳號毛孩的固定組合解鎖；未來測試預約佔用的時段名額歸還。
-- 保留：測試帳號本身與毛孩、操作紀錄（稽核用）、其他所有顧客與員工資料。
--
-- 執行順序：
--   1. 先備份（Railway 備份服務按 Run now）
--   2. railway connect MySQL，source C:/路徑/scripts/test-data-cleanup-preview.sql，核對清單
--   3. source C:/路徑/scripts/test-data-cleanup.sql
--   4. 看最後的檢查結果，每一列都要是 0
--
-- 刪除在同一個交易內；中途出錯不會 COMMIT，輸入 ROLLBACK; 再回報錯誤訊息。
-- 名稱檢查（零售商品、洗劑名稱要有「測試」、代班名稱要有「代班」）不符合時那一筆不刪。

DROP TEMPORARY TABLE IF EXISTS t_users;
DROP TEMPORARY TABLE IF EXISTS t_appts;
DROP TEMPORARY TABLE IF EXISTS t_walkins;
DROP TEMPORARY TABLE IF EXISTS t_wallets;
DROP TEMPORARY TABLE IF EXISTS t_notes;
DROP TEMPORARY TABLE IF EXISTS t_slots;

CREATE TEMPORARY TABLE t_users AS
    SELECT id FROM users WHERE name IN ('系統管理員', '洪大宝') OR username = 'demo@pet.com';
CREATE TEMPORARY TABLE t_appts AS
    SELECT id FROM appointments WHERE user_id IN (SELECT id FROM t_users);
CREATE TEMPORARY TABLE t_walkins AS
    SELECT id FROM walk_in_orders
    WHERE member_id IN (SELECT id FROM t_users) OR (member_id IS NULL AND pet_name LIKE '%測試%');
CREATE TEMPORARY TABLE t_wallets AS
    SELECT id FROM wallets WHERE user_id IN (SELECT id FROM t_users);
CREATE TEMPORARY TABLE t_notes AS
    SELECT id FROM pet_grooming_notes
    WHERE appointment_id IN (SELECT id FROM t_appts) OR walk_in_order_id IN (SELECT id FROM t_walkins);
-- 今天以後、還沒取消的測試預約佔著時段名額
CREATE TEMPORARY TABLE t_slots AS
    SELECT date AS slot_date, start_time AS slot_time, COUNT(*) AS cnt FROM appointments
    WHERE id IN (SELECT id FROM t_appts) AND status <> 'CANCELLED' AND date >= CURDATE()
    GROUP BY date, start_time;

START TRANSACTION;

UPDATE slot_capacity s JOIN t_slots x ON s.slot_date = x.slot_date AND s.slot_time = x.slot_time
SET s.booked = GREATEST(s.booked - x.cnt, 0);

DELETE FROM grooming_note_photos WHERE note_id IN (SELECT id FROM t_notes);
DELETE FROM pet_grooming_notes WHERE id IN (SELECT id FROM t_notes);
DELETE FROM performance_records
WHERE appointment_id IN (SELECT id FROM t_appts) OR walk_in_order_id IN (SELECT id FROM t_walkins);
DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM t_wallets);
DELETE FROM wallets WHERE id IN (SELECT id FROM t_wallets);
DELETE FROM transactions WHERE appointment_id IN (SELECT id FROM t_appts);
DELETE FROM transactions WHERE user_id IN (SELECT id FROM t_users);
DELETE FROM appointment_items WHERE appointment_id IN (SELECT id FROM t_appts);
DELETE FROM appointment_grooming_items WHERE appointment_id IN (SELECT id FROM t_appts);
DELETE FROM appointments WHERE id IN (SELECT id FROM t_appts);
DELETE FROM walk_in_order_items WHERE order_id IN (SELECT id FROM t_walkins);
DELETE FROM walk_in_orders WHERE id IN (SELECT id FROM t_walkins);
DELETE FROM topup_requests WHERE user_id IN (SELECT id FROM t_users);
DELETE FROM deposit_payments WHERE user_id IN (SELECT id FROM t_users);

DELETE FROM pet_locked_items WHERE pet_id IN (SELECT id FROM pets WHERE user_id IN (SELECT id FROM t_users));
UPDATE pets SET locked_grooming_item_id = NULL WHERE user_id IN (SELECT id FROM t_users);

DELETE FROM retail_products WHERE id IN (66, 67) AND name LIKE '%測試%';
DELETE FROM supply_usage_records WHERE supply_id = 1
    AND supply_id IN (SELECT id FROM store_supplies WHERE name LIKE '%測試%');
DELETE FROM store_supplies WHERE id = 1 AND name LIKE '%測試%';

DELETE FROM performance_records WHERE staff_id = 167
    AND staff_id IN (SELECT id FROM users WHERE name LIKE '%代班%');
DELETE FROM monthly_performance WHERE staff_id = 167
    AND staff_id IN (SELECT id FROM users WHERE name LIKE '%代班%');
DELETE FROM users WHERE id = 167 AND name LIKE '%代班%' AND role <> 'CUSTOMER';

COMMIT;

-- 檢查：每一列都要是 0（MySQL 同一句查詢不能重複用同一個暫存表，所以分開查）
SELECT '測試預約' AS 項目, COUNT(*) AS 剩餘 FROM appointments WHERE id IN (SELECT id FROM t_appts);
SELECT '測試現場單' AS 項目, COUNT(*) AS 剩餘 FROM walk_in_orders WHERE id IN (SELECT id FROM t_walkins);
SELECT '測試錢包' AS 項目, COUNT(*) AS 剩餘 FROM wallets WHERE id IN (SELECT id FROM t_wallets);
SELECT '測試交易／儲值申請／訂金回報' AS 項目,
    (SELECT COUNT(*) FROM transactions t JOIN users u ON u.id = t.user_id
        WHERE u.name IN ('系統管理員', '洪大宝') OR u.username = 'demo@pet.com')
  + (SELECT COUNT(*) FROM topup_requests r JOIN users u ON u.id = r.user_id
        WHERE u.name IN ('系統管理員', '洪大宝') OR u.username = 'demo@pet.com')
  + (SELECT COUNT(*) FROM deposit_payments d JOIN users u ON u.id = d.user_id
        WHERE u.name IN ('系統管理員', '洪大宝') OR u.username = 'demo@pet.com') AS 剩餘;
SELECT '測試零售商品／洗劑／代班' AS 項目,
    (SELECT COUNT(*) FROM retail_products WHERE id IN (66, 67) AND name LIKE '%測試%')
  + (SELECT COUNT(*) FROM store_supplies WHERE id = 1 AND name LIKE '%測試%')
  + (SELECT COUNT(*) FROM users WHERE id = 167 AND name LIKE '%代班%') AS 剩餘;

DROP TEMPORARY TABLE IF EXISTS t_users;
DROP TEMPORARY TABLE IF EXISTS t_appts;
DROP TEMPORARY TABLE IF EXISTS t_walkins;
DROP TEMPORARY TABLE IF EXISTS t_wallets;
DROP TEMPORARY TABLE IF EXISTS t_notes;
DROP TEMPORARY TABLE IF EXISTS t_slots;
