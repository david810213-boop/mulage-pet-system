-- V20：狗狗改成「體型-毛長-精緻洗」＋依體型加購基礎／中階 AD／高階 HC 定制調理（2026-11-23 店家確認）
--
-- 原本的基礎／中階／高階定制調理套餐（DOG002 等）店家已經自己下架，改成精緻洗＋加購。
-- 加購價格依店家價目表換算（店家指定）：
--   基礎、中階 AD ＝ 原定制調理價 − 精緻洗價；中長毛比照長毛
--   高階 HC ＝ 精緻洗價（也就是 HC 總價為精緻洗兩倍）；小型犬 HC 加購不分毛長一律 800，特大型犬 HC 加購為 2000／2200／2400（短／中長／長毛）
-- 積分：精緻洗本身已含洗、吹、基礎美容，所以基礎加購 0 分；AD 10 分、HC 15 分，不論價格（店家確認）。
-- 加購可享會員折扣、可線上預約，依體重級距與毛長篩選，鎖定套餐與假日規則都不擋（tier_addon）。
-- 設計見 docs/adr/0011-dog-tier-addons.md

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length, tier_addon)
VALUES
('DOG073', '小型犬-短毛-基礎定制調理（加購）', '體重5kg以下，短毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'SMALL', 'SHORT', true),
('DOG074', '小型犬-短毛-中階定制調理AD（加購）', '體重5kg以下，短毛，搭配精緻洗加購', 500.0, false, true, 'AD', 10.0, true, false, 'DOG', 'SMALL', 'SHORT', true),
('DOG075', '小型犬-短毛-高階定制調理HC（加購）', '體重5kg以下，短毛，搭配精緻洗加購', 800.0, false, true, 'HC', 15.0, true, false, 'DOG', 'SMALL', 'SHORT', true),
('DOG076', '小型犬-中長毛-基礎定制調理（加購）', '體重5kg以下，中長毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'SMALL', 'MEDIUM', true),
('DOG077', '小型犬-中長毛-中階定制調理AD（加購）', '體重5kg以下，中長毛，搭配精緻洗加購', 500.0, false, true, 'AD', 10.0, true, false, 'DOG', 'SMALL', 'MEDIUM', true),
('DOG078', '小型犬-中長毛-高階定制調理HC（加購）', '體重5kg以下，中長毛，搭配精緻洗加購', 800.0, false, true, 'HC', 15.0, true, false, 'DOG', 'SMALL', 'MEDIUM', true),
('DOG079', '小型犬-長毛-基礎定制調理（加購）', '體重5kg以下，長毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'SMALL', 'LONG', true),
('DOG080', '小型犬-長毛-中階定制調理AD（加購）', '體重5kg以下，長毛，搭配精緻洗加購', 500.0, false, true, 'AD', 10.0, true, false, 'DOG', 'SMALL', 'LONG', true),
('DOG081', '小型犬-長毛-高階定制調理HC（加購）', '體重5kg以下，長毛，搭配精緻洗加購', 800.0, false, true, 'HC', 15.0, true, false, 'DOG', 'SMALL', 'LONG', true),
('DOG082', '中小型犬-短毛-基礎定制調理（加購）', '體重6-10kg，短毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM_SMALL', 'SHORT', true),
('DOG083', '中小型犬-短毛-中階定制調理AD（加購）', '體重6-10kg，短毛，搭配精緻洗加購', 500.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM_SMALL', 'SHORT', true),
('DOG084', '中小型犬-短毛-高階定制調理HC（加購）', '體重6-10kg，短毛，搭配精緻洗加購', 800.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM_SMALL', 'SHORT', true),
('DOG085', '中小型犬-中長毛-基礎定制調理（加購）', '體重6-10kg，中長毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM_SMALL', 'MEDIUM', true),
('DOG086', '中小型犬-中長毛-中階定制調理AD（加購）', '體重6-10kg，中長毛，搭配精緻洗加購', 500.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM_SMALL', 'MEDIUM', true),
('DOG087', '中小型犬-中長毛-高階定制調理HC（加購）', '體重6-10kg，中長毛，搭配精緻洗加購', 1000.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM_SMALL', 'MEDIUM', true),
('DOG088', '中小型犬-長毛-基礎定制調理（加購）', '體重6-10kg，長毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM_SMALL', 'LONG', true),
('DOG089', '中小型犬-長毛-中階定制調理AD（加購）', '體重6-10kg，長毛，搭配精緻洗加購', 500.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM_SMALL', 'LONG', true),
('DOG090', '中小型犬-長毛-高階定制調理HC（加購）', '體重6-10kg，長毛，搭配精緻洗加購', 1200.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM_SMALL', 'LONG', true),
('DOG091', '中型犬-短毛-基礎定制調理（加購）', '體重11-16kg，短毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM', 'SHORT', true),
('DOG092', '中型犬-短毛-中階定制調理AD（加購）', '體重11-16kg，短毛，搭配精緻洗加購', 500.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM', 'SHORT', true),
('DOG093', '中型犬-短毛-高階定制調理HC（加購）', '體重11-16kg，短毛，搭配精緻洗加購', 1000.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM', 'SHORT', true),
('DOG094', '中型犬-中長毛-基礎定制調理（加購）', '體重11-16kg，中長毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM', 'MEDIUM', true),
('DOG095', '中型犬-中長毛-中階定制調理AD（加購）', '體重11-16kg，中長毛，搭配精緻洗加購', 600.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM', 'MEDIUM', true),
('DOG096', '中型犬-中長毛-高階定制調理HC（加購）', '體重11-16kg，中長毛，搭配精緻洗加購', 1200.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM', 'MEDIUM', true),
('DOG097', '中型犬-長毛-基礎定制調理（加購）', '體重11-16kg，長毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM', 'LONG', true),
('DOG098', '中型犬-長毛-中階定制調理AD（加購）', '體重11-16kg，長毛，搭配精緻洗加購', 600.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM', 'LONG', true),
('DOG099', '中型犬-長毛-高階定制調理HC（加購）', '體重11-16kg，長毛，搭配精緻洗加購', 1400.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM', 'LONG', true),
('DOG100', '中大型犬-短毛-基礎定制調理（加購）', '體重17-22kg，短毛，搭配精緻洗加購', 300.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM_LARGE', 'SHORT', true),
('DOG101', '中大型犬-短毛-中階定制調理AD（加購）', '體重17-22kg，短毛，搭配精緻洗加購', 600.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM_LARGE', 'SHORT', true),
('DOG102', '中大型犬-短毛-高階定制調理HC（加購）', '體重17-22kg，短毛，搭配精緻洗加購', 1200.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM_LARGE', 'SHORT', true),
('DOG103', '中大型犬-中長毛-基礎定制調理（加購）', '體重17-22kg，中長毛，搭配精緻洗加購', 400.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM_LARGE', 'MEDIUM', true),
('DOG104', '中大型犬-中長毛-中階定制調理AD（加購）', '體重17-22kg，中長毛，搭配精緻洗加購', 800.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM_LARGE', 'MEDIUM', true),
('DOG105', '中大型犬-中長毛-高階定制調理HC（加購）', '體重17-22kg，中長毛，搭配精緻洗加購', 1500.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM_LARGE', 'MEDIUM', true),
('DOG106', '中大型犬-長毛-基礎定制調理（加購）', '體重17-22kg，長毛，搭配精緻洗加購', 400.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'MEDIUM_LARGE', 'LONG', true),
('DOG107', '中大型犬-長毛-中階定制調理AD（加購）', '體重17-22kg，長毛，搭配精緻洗加購', 800.0, false, true, 'AD', 10.0, true, false, 'DOG', 'MEDIUM_LARGE', 'LONG', true),
('DOG108', '中大型犬-長毛-高階定制調理HC（加購）', '體重17-22kg，長毛，搭配精緻洗加購', 1800.0, false, true, 'HC', 15.0, true, false, 'DOG', 'MEDIUM_LARGE', 'LONG', true),
('DOG109', '大型犬-短毛-基礎定制調理（加購）', '體重23-27kg，短毛，搭配精緻洗加購', 400.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'LARGE', 'SHORT', true),
('DOG110', '大型犬-短毛-中階定制調理AD（加購）', '體重23-27kg，短毛，搭配精緻洗加購', 800.0, false, true, 'AD', 10.0, true, false, 'DOG', 'LARGE', 'SHORT', true),
('DOG111', '大型犬-短毛-高階定制調理HC（加購）', '體重23-27kg，短毛，搭配精緻洗加購', 1600.0, false, true, 'HC', 15.0, true, false, 'DOG', 'LARGE', 'SHORT', true),
('DOG112', '大型犬-中長毛-基礎定制調理（加購）', '體重23-27kg，中長毛，搭配精緻洗加購', 400.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'LARGE', 'MEDIUM', true),
('DOG113', '大型犬-中長毛-中階定制調理AD（加購）', '體重23-27kg，中長毛，搭配精緻洗加購', 800.0, false, true, 'AD', 10.0, true, false, 'DOG', 'LARGE', 'MEDIUM', true),
('DOG114', '大型犬-中長毛-高階定制調理HC（加購）', '體重23-27kg，中長毛，搭配精緻洗加購', 1900.0, false, true, 'HC', 15.0, true, false, 'DOG', 'LARGE', 'MEDIUM', true),
('DOG115', '大型犬-長毛-基礎定制調理（加購）', '體重23-27kg，長毛，搭配精緻洗加購', 400.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'LARGE', 'LONG', true),
('DOG116', '大型犬-長毛-中階定制調理AD（加購）', '體重23-27kg，長毛，搭配精緻洗加購', 800.0, false, true, 'AD', 10.0, true, false, 'DOG', 'LARGE', 'LONG', true),
('DOG117', '大型犬-長毛-高階定制調理HC（加購）', '體重23-27kg，長毛，搭配精緻洗加購', 2200.0, false, true, 'HC', 15.0, true, false, 'DOG', 'LARGE', 'LONG', true),
('DOG118', '特大型犬-短毛-基礎定制調理（加購）', '體重28-33kg，短毛，搭配精緻洗加購', 400.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'EXTRA_LARGE', 'SHORT', true),
('DOG119', '特大型犬-短毛-中階定制調理AD（加購）', '體重28-33kg，短毛，搭配精緻洗加購', 1000.0, false, true, 'AD', 10.0, true, false, 'DOG', 'EXTRA_LARGE', 'SHORT', true),
('DOG120', '特大型犬-短毛-高階定制調理HC（加購）', '體重28-33kg，短毛，搭配精緻洗加購', 2000.0, false, true, 'HC', 15.0, true, false, 'DOG', 'EXTRA_LARGE', 'SHORT', true),
('DOG121', '特大型犬-中長毛-基礎定制調理（加購）', '體重28-33kg，中長毛，搭配精緻洗加購', 600.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'EXTRA_LARGE', 'MEDIUM', true),
('DOG122', '特大型犬-中長毛-中階定制調理AD（加購）', '體重28-33kg，中長毛，搭配精緻洗加購', 1200.0, false, true, 'AD', 10.0, true, false, 'DOG', 'EXTRA_LARGE', 'MEDIUM', true),
('DOG123', '特大型犬-中長毛-高階定制調理HC（加購）', '體重28-33kg，中長毛，搭配精緻洗加購', 2200.0, false, true, 'HC', 15.0, true, false, 'DOG', 'EXTRA_LARGE', 'MEDIUM', true),
('DOG124', '特大型犬-長毛-基礎定制調理（加購）', '體重28-33kg，長毛，搭配精緻洗加購', 600.0, false, true, 'OTHER', 0.0, true, false, 'DOG', 'EXTRA_LARGE', 'LONG', true),
('DOG125', '特大型犬-長毛-中階定制調理AD（加購）', '體重28-33kg，長毛，搭配精緻洗加購', 1200.0, false, true, 'AD', 10.0, true, false, 'DOG', 'EXTRA_LARGE', 'LONG', true),
('DOG126', '特大型犬-長毛-高階定制調理HC（加購）', '體重28-33kg，長毛，搭配精緻洗加購', 2400.0, false, true, 'HC', 15.0, true, false, 'DOG', 'EXTRA_LARGE', 'LONG', true);

-- 被上面的加購取代：店家先用通用項目代替的 AD／HC，以及拿 DOG036 改成的「AD-中階調理+500」
UPDATE grooming_items SET is_deleted = true
WHERE item_code IN ('AD', 'HC', 'DOG036', 'GS014', 'GS015', 'GS016', 'GS017', 'GS018', 'GS019', 'GS020', 'GS021', 'GS022');

-- 會員折扣（店家確認）：高密度被毛犬種是同體型洗澡加價，要打折；清潔費、局部修剪不打折
UPDATE grooming_items SET discount_eligible = true WHERE item_code IN ('GS023', 'GS024', 'GS025', 'GS026');
UPDATE grooming_items SET discount_eligible = false WHERE item_code IN ('GS013', 'PARTIAL');
-- 美容加購價目表上的單項加購（修屁股、除廢毛、拆結費、刷牙、局部修剪、頭部修剪、貴賓腳）不打折（店家確認）；
-- 這些多半是店家在後台自己新增的，代碼不固定，所以依名稱比對
UPDATE grooming_items SET discount_eligible = false
WHERE tier_addon = false
  AND (name LIKE '%屁股%' OR name LIKE '%廢毛%' OR name LIKE '%拆結%' OR name LIKE '%刷牙%'
       OR name LIKE '%局部修剪%' OR name LIKE '%頭部修剪%' OR name LIKE '%貴賓腳%');

-- 鎖定的套餐已經下架的狗，解除鎖定，改回依體重篩選
UPDATE pets SET locked_grooming_item_id = NULL
WHERE locked_grooming_item_id IN (SELECT id FROM (SELECT id FROM grooming_items WHERE is_deleted = true) AS removed);

-- 精緻洗價格依店家確認的新價目表（中長毛是店家在後台自己新增的項目，一併對齊）
UPDATE grooming_items SET price = 700.0 WHERE dog_weight_tier = 'SMALL' AND dog_coat_length = 'SHORT' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 750.0 WHERE dog_weight_tier = 'SMALL' AND dog_coat_length = 'MEDIUM' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 800.0 WHERE dog_weight_tier = 'SMALL' AND dog_coat_length = 'LONG' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 800.0 WHERE dog_weight_tier = 'MEDIUM_SMALL' AND dog_coat_length = 'SHORT' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1000.0 WHERE dog_weight_tier = 'MEDIUM_SMALL' AND dog_coat_length = 'MEDIUM' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1200.0 WHERE dog_weight_tier = 'MEDIUM_SMALL' AND dog_coat_length = 'LONG' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1000.0 WHERE dog_weight_tier = 'MEDIUM' AND dog_coat_length = 'SHORT' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1200.0 WHERE dog_weight_tier = 'MEDIUM' AND dog_coat_length = 'MEDIUM' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1400.0 WHERE dog_weight_tier = 'MEDIUM' AND dog_coat_length = 'LONG' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1200.0 WHERE dog_weight_tier = 'MEDIUM_LARGE' AND dog_coat_length = 'SHORT' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1500.0 WHERE dog_weight_tier = 'MEDIUM_LARGE' AND dog_coat_length = 'MEDIUM' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1800.0 WHERE dog_weight_tier = 'MEDIUM_LARGE' AND dog_coat_length = 'LONG' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1600.0 WHERE dog_weight_tier = 'LARGE' AND dog_coat_length = 'SHORT' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 1900.0 WHERE dog_weight_tier = 'LARGE' AND dog_coat_length = 'MEDIUM' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 2200.0 WHERE dog_weight_tier = 'LARGE' AND dog_coat_length = 'LONG' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 2000.0 WHERE dog_weight_tier = 'EXTRA_LARGE' AND dog_coat_length = 'SHORT' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 2300.0 WHERE dog_weight_tier = 'EXTRA_LARGE' AND dog_coat_length = 'MEDIUM' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
UPDATE grooming_items SET price = 2600.0 WHERE dog_weight_tier = 'EXTRA_LARGE' AND dog_coat_length = 'LONG' AND tier_addon = false AND is_deleted = false AND name LIKE '%精緻洗%';
