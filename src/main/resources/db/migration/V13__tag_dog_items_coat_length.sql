-- V13：幫狗狗套餐項目標上毛長（短毛/長毛），供依「體重＋毛長」雙重篩選使用
-- （2026-09-06，需求：狗狗預約選單改成同時判斷體重級距與後台定義的毛長，
--  不再只用體重篩選——目前只顯示同體重級距底下的短毛/長毛任選，改成只
--  顯示符合這隻狗實際定義毛長的那一種）。
--
-- 欄位本身（dog_coat_length）由 Hibernate ddl-auto=update 依 Entity 新欄位
-- 自動加上，這裡只負責回填既有 48 筆狗狗品項（DOG001~048）的值。
-- 依品項名稱裡明確寫的「短毛」/「長毛」字樣回填，不用一個個列代碼，
-- 比對規則簡單可靠、之後新增品項只要照命名慣例命名就會被規則涵蓋到
-- （但新增品項仍建議額外手動確認一次，LIKE 規則終究是取巧寫法）。

UPDATE grooming_items SET dog_coat_length = 'SHORT' WHERE item_code LIKE 'DOG%' AND name LIKE '%短毛%';
UPDATE grooming_items SET dog_coat_length = 'LONG'  WHERE item_code LIKE 'DOG%' AND name LIKE '%長毛%';
