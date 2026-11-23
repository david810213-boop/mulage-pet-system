-- V21：狗狗固定套餐改為固定組合（客製化鎖定），原本已鎖定單一套餐的狗轉成「只有一項的組合」，
-- 價格固定為目前價格。pet_locked_items 表由 Hibernate 建立，本遷移在建表之後執行。
-- 設計見 docs/adr/0012-staff-part-time-and-custom-lock.md
INSERT INTO pet_locked_items (pet_id, grooming_item_id, locked_price)
SELECT p.id, p.locked_grooming_item_id, ROUND(g.price)
FROM pets p
JOIN grooming_items g ON g.id = p.locked_grooming_item_id
WHERE p.locked_grooming_item_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM pet_locked_items x WHERE x.pet_id = p.id);
