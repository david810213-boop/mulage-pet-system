-- 11/24～11/25 系統測試資料：刪除前預覽（只讀，不會改任何資料）
-- 測試帳號：系統管理員、洪大宝、demo@pet.com（帳號與毛孩保留，只刪單據與金流）
-- 另外：沒綁會員、寵物名有「測試」的現場單；測試零售商品、測試洗劑、測試代班

DROP TEMPORARY TABLE IF EXISTS t_users;
CREATE TEMPORARY TABLE t_users AS
    SELECT id, username, name, role FROM users
    WHERE name IN ('系統管理員', '洪大宝') OR username = 'demo@pet.com';

SELECT '① 測試帳號（帳號保留）' AS 區塊, id, username, name, role FROM t_users;

SELECT '② 預約' AS 區塊, a.id, CONCAT('AP', LPAD(a.id, 3, '0')) AS 編號, a.date, a.start_time, a.pet_name, a.status, a.paid
FROM appointments a WHERE a.user_id IN (SELECT id FROM t_users) ORDER BY a.id;

SELECT '③ 現場單' AS 區塊, w.id, w.pet_name, w.paid, w.created_at
FROM walk_in_orders w
WHERE w.member_id IN (SELECT id FROM t_users) OR (w.member_id IS NULL AND w.pet_name LIKE '%測試%')
ORDER BY w.id;

SELECT '④ 現場單裡的零售商品（真實商品的庫存不會自動補回）' AS 區塊, i.order_id, i.retail_product_id, i.item_name
FROM walk_in_order_items i JOIN walk_in_orders w ON w.id = i.order_id
WHERE i.retail_product_id IS NOT NULL
  AND (w.member_id IN (SELECT id FROM t_users) OR (w.member_id IS NULL AND w.pet_name LIKE '%測試%'));

SELECT '⑤ 交易紀錄' AS 區塊, t.id, t.appointment_id, t.payment_method, t.final_amount
FROM transactions t WHERE t.user_id IN (SELECT id FROM t_users) ORDER BY t.id;

SELECT '⑥ 錢包（會刪除，餘額歸零）' AS 區塊, w.id, w.user_id, w.balance
FROM wallets w WHERE w.user_id IN (SELECT id FROM t_users);

SELECT '⑦ 儲值申請' AS 區塊, r.id, r.user_id, r.amount, r.status FROM topup_requests r WHERE r.user_id IN (SELECT id FROM t_users);
SELECT '⑧ 訂金回報' AS 區塊, d.id, d.user_id, d.amount, d.status FROM deposit_payments d WHERE d.user_id IN (SELECT id FROM t_users);

SELECT '⑨ 固定組合（解鎖）' AS 區塊, p.id, p.name, p.locked_grooming_item_id
FROM pets p WHERE p.user_id IN (SELECT id FROM t_users) AND p.locked_grooming_item_id IS NOT NULL;

SELECT '⑩ 零售商品／店用洗劑' AS 區塊, 'retail' AS 類別, id, name FROM retail_products WHERE id IN (66, 67)
UNION ALL SELECT '⑩ 零售商品／店用洗劑', 'supply', id, name FROM store_supplies WHERE id = 1;

SELECT '⑪ 代班' AS 區塊, id, username, name, role FROM users WHERE id = 167;

DROP TEMPORARY TABLE IF EXISTS t_users;
