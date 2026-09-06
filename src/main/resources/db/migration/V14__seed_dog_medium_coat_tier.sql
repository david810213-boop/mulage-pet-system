-- V14：新增狗狗「中長毛」套餐（2026-09-06，需求：中長毛狗狗要有自己的
-- 套餐分類，不能只在短毛/長毛之間硬選一邊）。
--
-- 價格規則（店家指定）：每一項的價格 =（同體重級距＋同服務等級的短毛價格
-- + 長毛價格）÷ 2。DOG049~066 對應原本 36 項基礎套餐（精緻洗/基礎定制
-- 調理/中階定制調理），DOG067~072 對應 DOG037~048 高階定制調理那 12 項。
--
-- 積分分類（performance_category）跟體重級距一樣，直接用 INSERT...SELECT
-- 複製自既有項目，不是憑印象重新指定——跟 V12 同一套理由：同一個體重級距
-- 底下，短毛/長毛可能對應到不同積分分類（實測發現「中大型犬 17-22kg」這個
-- 級距就是短毛 BATH_SMALL、長毛卻是 BATH_LARGE）。
-- ⚠️ 這種短毛/長毛分類不一致的級距（目前只有中大型犬 17-22kg 這一組），
-- 中長毛統一複製自「長毛」那一邊（積分分類跟著算比較高的 BATH_LARGE）
-- ——這是我方這次自己做的判斷，理由是中長毛的整理工時比較接近長毛，
-- 不是店家明確指定的規則，如果店家覺得應該比照短毛，請直接回饋再調整，
-- 只要改這個 migration 裡對應那幾筆的來源代碼即可。
-- 其餘體重級距短毛/長毛分類本來就一致，複製自哪一邊結果都相同。

-- ── 基礎 18 項（精緻洗／基礎定制調理／中階定制調理 × 6 體重級距）──────

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG049', '小型犬-中長毛-精緻洗', '體重5kg以下，中長毛（短毛/長毛均價）',
       750.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG001';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG050', '小型犬-中長毛-基礎定制調理', '體重5kg以下，中長毛（短毛/長毛均價）',
       1050.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG002';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG051', '小型犬-中長毛-中階定制調理', '體重5kg以下，中長毛（短毛/長毛均價）',
       1250.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG003';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG052', '中小型犬-中長毛-精緻洗', '體重6-10kg，中長毛（短毛/長毛均價）',
       1000.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG007';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG053', '中小型犬-中長毛-基礎定制調理', '體重6-10kg，中長毛（短毛/長毛均價）',
       2300.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG008';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG054', '中小型犬-中長毛-中階定制調理', '體重6-10kg，中長毛（短毛/長毛均價）',
       2500.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG009';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG055', '中型犬-中長毛-精緻洗', '體重11-16kg，中長毛（短毛/長毛均價）',
       1200.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG013';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG056', '中型犬-中長毛-基礎定制調理', '體重11-16kg，中長毛（短毛/長毛均價）',
       2700.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG014';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG057', '中型犬-中長毛-中階定制調理', '體重11-16kg，中長毛（短毛/長毛均價）',
       2950.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG015';

-- ⚠️ 中大型犬 17-22kg：短毛/長毛積分分類不一致，複製自長毛（DOG022/023/024，BATH_LARGE）
INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG058', '中大型犬-中長毛-精緻洗', '體重17-22kg，中長毛（短毛/長毛均價）',
       1500.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG022';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG059', '中大型犬-中長毛-基礎定制調理', '體重17-22kg，中長毛（短毛/長毛均價）',
       3350.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG023';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG060', '中大型犬-中長毛-中階定制調理', '體重17-22kg，中長毛（短毛/長毛均價）',
       3700.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG024';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG061', '大型犬-中長毛-精緻洗', '體重23-27kg，中長毛（短毛/長毛均價）',
       1900.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG025';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG062', '大型犬-中長毛-基礎定制調理', '體重23-27kg，中長毛（短毛/長毛均價）',
       4200.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG026';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG063', '大型犬-中長毛-中階定制調理', '體重23-27kg，中長毛（短毛/長毛均價）',
       4600.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG027';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG064', '特大型犬-中長毛-精緻洗', '體重28-33kg，中長毛（短毛/長毛均價）',
       2300.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG031';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG065', '特大型犬-中長毛-基礎定制調理', '體重28-33kg，中長毛（短毛/長毛均價）',
       5100.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG032';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG066', '特大型犬-中長毛-中階定制調理', '體重28-33kg，中長毛（短毛/長毛均價）',
       5700.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG033';

-- ── 高階定制調理 6 項（對應 DOG037~048）─────────────────────────────
-- 這個服務等級價格本身就是「起價」（實際依毛況現場另外用自訂金額加購
-- 補收差額），中長毛的均價一樣只是起價的均價。

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG067', '小型犬-中長毛-高階定制調理',
       '體重5kg以下，中長毛（短毛/長毛起價均價）。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       1550.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG037';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG068', '中小型犬-中長毛-高階定制調理',
       '體重6-10kg，中長毛（短毛/長毛起價均價）。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       2000.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG039';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG069', '中型犬-中長毛-高階定制調理',
       '體重11-16kg，中長毛（短毛/長毛起價均價）。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       2400.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG041';

-- ⚠️ 一樣是中大型犬 17-22kg 這個級距，複製自長毛版（DOG044，BATH_LARGE）
INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG070', '中大型犬-中長毛-高階定制調理',
       '體重17-22kg，中長毛（短毛/長毛起價均價）。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       3000.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG044';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG071', '大型犬-中長毛-高階定制調理',
       '體重23-27kg，中長毛（短毛/長毛起價均價）。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       3600.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG045';

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length)
SELECT 'DOG072', '特大型犬-中長毛-高階定制調理',
       '體重28-33kg，中長毛（短毛/長毛起價均價）。此為起價，實際依毛孩毛況/特殊剪法現場另外報價（用自訂金額加購補收差額）',
       4400.0, false, gi.bookable, gi.performance_category, gi.points, gi.discount_eligible, gi.requires_existing_customer,
       gi.applicable_pet_type, gi.dog_weight_tier, 'MEDIUM'
FROM grooming_items gi WHERE gi.item_code = 'DOG047';

-- ── 副組成（吹毛+基礎美容）比照 V12 同一套做法，複製自來源項目 ─────────
INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT new_item.id, c.performance_category, c.points
FROM grooming_items src
JOIN grooming_item_components c ON c.grooming_item_id = src.id
JOIN grooming_items new_item ON new_item.item_code IN
    ('DOG049','DOG050','DOG051','DOG052','DOG053','DOG054','DOG055','DOG056','DOG057',
     'DOG058','DOG059','DOG060','DOG061','DOG062','DOG063','DOG064','DOG065','DOG066',
     'DOG067','DOG068','DOG069','DOG070','DOG071','DOG072')
WHERE src.item_code = CASE new_item.item_code
    WHEN 'DOG049' THEN 'DOG001' WHEN 'DOG050' THEN 'DOG002' WHEN 'DOG051' THEN 'DOG003'
    WHEN 'DOG052' THEN 'DOG007' WHEN 'DOG053' THEN 'DOG008' WHEN 'DOG054' THEN 'DOG009'
    WHEN 'DOG055' THEN 'DOG013' WHEN 'DOG056' THEN 'DOG014' WHEN 'DOG057' THEN 'DOG015'
    WHEN 'DOG058' THEN 'DOG022' WHEN 'DOG059' THEN 'DOG023' WHEN 'DOG060' THEN 'DOG024'
    WHEN 'DOG061' THEN 'DOG025' WHEN 'DOG062' THEN 'DOG026' WHEN 'DOG063' THEN 'DOG027'
    WHEN 'DOG064' THEN 'DOG031' WHEN 'DOG065' THEN 'DOG032' WHEN 'DOG066' THEN 'DOG033'
    WHEN 'DOG067' THEN 'DOG037' WHEN 'DOG068' THEN 'DOG039' WHEN 'DOG069' THEN 'DOG041'
    WHEN 'DOG070' THEN 'DOG044' WHEN 'DOG071' THEN 'DOG045' WHEN 'DOG072' THEN 'DOG047'
  END
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components existing
    WHERE existing.grooming_item_id = new_item.id
      AND existing.performance_category = c.performance_category
  );
