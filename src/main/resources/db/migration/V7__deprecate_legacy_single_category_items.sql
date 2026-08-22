-- V7：下架套餐化改版前的舊版單一積分分類項目
-- 這些項目（BATH_S/L/CS/CL、BLOW_S/L/CS/CL）現在的積分已經改成套餐自動展開的
-- 副組成在算（見 V5），不應該再讓店員在選單上單獨選到。用軟刪除（is_deleted=true）
-- 處理，歷史交易紀錄不受影響（品項名稱/價格本來就是結帳當下的快照，跟這個
-- is_deleted 標記無關）。

UPDATE grooming_items SET is_deleted = true WHERE item_code = 'BATH_S';
UPDATE grooming_items SET is_deleted = true WHERE item_code = 'BATH_L';
UPDATE grooming_items SET is_deleted = true WHERE item_code = 'BATH_CS';
UPDATE grooming_items SET is_deleted = true WHERE item_code = 'BATH_CL';
UPDATE grooming_items SET is_deleted = true WHERE item_code = 'BLOW_S';
UPDATE grooming_items SET is_deleted = true WHERE item_code = 'BLOW_L';
UPDATE grooming_items SET is_deleted = true WHERE item_code = 'BLOW_CS';
UPDATE grooming_items SET is_deleted = true WHERE item_code = 'BLOW_CL';
