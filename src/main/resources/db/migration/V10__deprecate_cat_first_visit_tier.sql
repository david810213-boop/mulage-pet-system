-- V10：下架貓咪「初體驗價目表」（CAT001~012），2026-08-23 菜單簡化
-- 原因：不再讓客人/店員自己判斷「這隻貓是不是真的第一次來，該選哪張價目表」，
-- 改成只保留「單次價目表」（CAT013~024，即原價），系統依這隻貓有沒有任何一筆
-- 已結帳消費紀錄，自動判斷要不要套用首次體驗 9 折（CatFirstVisitDiscountService），
-- 效果上約等於初體驗價（原始兩張表本來就大約差 9 折，詳見貓咪服務項目建立清單
-- 文件的說明），但不用再讓客人/店員手動選表。
--
-- 用軟刪除（is_deleted=true）處理，不是真的刪除資料列：歷史交易紀錄的品項名稱
-- 是結帳當下的快照，不受這裡影響；GroomingItem 本身的 is_deleted=true 只是讓
-- findByIsDeletedFalse() 這類查詢（後台服務項目列表、LIFF/現場開單菜單）看不到
-- 它，比照 V7 下架舊版單一積分分類項目的同一套機制。

UPDATE grooming_items SET is_deleted = true WHERE item_code IN (
    'CAT001', 'CAT002', 'CAT003', 'CAT004', 'CAT005', 'CAT006',
    'CAT007', 'CAT008', 'CAT009', 'CAT010', 'CAT011', 'CAT012'
);
