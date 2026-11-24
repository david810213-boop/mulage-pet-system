-- V23：大美容造型差額（2026-11-25 店家確認）
--
-- 大美容加購標「起」價，造型後的實際差額原本用「自訂金額加購」補收，但自訂項目一律不打折；
-- 店家要差額也能打會員折扣，所以另建一個可折扣的項目，店員核對時用「新增服務項目＋自訂價格」填實際差額。
-- 只在店內加（不開放線上預約）；積分 0，避免跟大美容加購本身的積分重複計算；
-- 不是依體型加購（tier_addon = false），所以不套用狗狗首次體驗 9 折（首次體驗不能選大美容）。
-- 設計見 docs/adr/0011-dog-tier-addons.md

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length, tier_addon)
VALUES
('CUT000', '大美容造型差額（核對時填實際差額）', '大美容加購為起價，依造型實際價格補收的差額；新增時在「自訂價格」填差額，可享會員折扣', 0.0,
 false, false, 'TRIM', 0.0, true, false, 'DOG', NULL, NULL, false);
