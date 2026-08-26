-- V12：新增狗狗「高階定制調理」服務等級（2026-08-26）
-- 依照店家提供的完整價目圖，補上原本 36 項（精緻洗/基礎定制調理/中階定制調理）
-- 之外的第 4 個服務等級。DOG037~048，共 12 項（6 體重級距 × 短毛/長毛）。
--
-- 這批新項目的「積分分類」跟「體重級距標記」直接用 INSERT...SELECT 複製自
-- 同一個體重級距、同樣毛長的既有項目（例如 DOG037 複製自 DOG001），不是憑
-- 印象重新指定——因為實測發現同一個體重級距底下，短毛/長毛可能對應到不同
-- 積分分類（例如中大型犬 17-22kg：短毛是 BATH_SMALL、長毛卻是 BATH_LARGE），
-- 用複製的方式確保新項目跟既有項目的分類邏輯完全一致，不會猜錯。
--
-- 價格是店家價目圖上「高階定制調理」欄位的「up」起價（實際上限是開放式的，
-- 依毛孩實際情況現場另外用「自訂金額加購」機制加收）。

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG037', '小型犬-短毛-高階定制調理',
       '體重5kg以下，短毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       1500.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG001';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG038', '小型犬-長毛-高階定制調理',
       '體重5kg以下，長毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       1600.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG004';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG039', '中小型犬-短毛-高階定制調理',
       '體重6-10kg，短毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       1600.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG007';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG040', '中小型犬-長毛-高階定制調理',
       '體重6-10kg，長毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       2400.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG010';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG041', '中型犬-短毛-高階定制調理',
       '體重11-16kg，短毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       2000.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG013';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG042', '中型犬-長毛-高階定制調理',
       '體重11-16kg，長毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       2800.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG016';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG043', '中大型犬-短毛-高階定制調理',
       '體重17-22kg，短毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       2400.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG019';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG044', '中大型犬-長毛-高階定制調理',
       '體重17-22kg，長毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       3600.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG022';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG045', '大型犬-短毛-高階定制調理',
       '體重23-27kg，短毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       3200.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG025';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG046', '大型犬-長毛-高階定制調理',
       '體重23-27kg，長毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       4000.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG028';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG047', '特大型犬-短毛-高階定制調理',
       '體重28-33kg，短毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       4000.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG031';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier)
SELECT 'DOG048', '特大型犬-長毛-高階定制調理',
       '體重28-33kg，長毛。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       4800.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier
FROM grooming_items gi WHERE gi.item_code = 'DOG034';

-- 副組成（吹毛+基礎美容）比照同一個來源項目複製，維持績效拆分邏輯一致
INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT new_item.id, c.performance_category, c.points
FROM grooming_items src
JOIN grooming_item_components c ON c.grooming_item_id = src.id
JOIN grooming_items new_item ON new_item.item_code IN
    ('DOG037','DOG038','DOG039','DOG040','DOG041','DOG042','DOG043','DOG044','DOG045','DOG046','DOG047','DOG048')
WHERE src.item_code = CASE new_item.item_code
    WHEN 'DOG037' THEN 'DOG001' WHEN 'DOG038' THEN 'DOG004'
    WHEN 'DOG039' THEN 'DOG007' WHEN 'DOG040' THEN 'DOG010'
    WHEN 'DOG041' THEN 'DOG013' WHEN 'DOG042' THEN 'DOG016'
    WHEN 'DOG043' THEN 'DOG019' WHEN 'DOG044' THEN 'DOG022'
    WHEN 'DOG045' THEN 'DOG025' WHEN 'DOG046' THEN 'DOG028'
    WHEN 'DOG047' THEN 'DOG031' WHEN 'DOG048' THEN 'DOG034'
  END
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components existing
    WHERE existing.grooming_item_id = new_item.id
      AND existing.performance_category = c.performance_category
  );
