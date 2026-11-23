-- V22：狗狗大美容（剪毛造型）加購：電剪／半手剪／全手剪，依體型×毛長（2026-11-24 店家確認）
--
-- 店家價目表是「含精緻洗」的總價區間，加購價 = 價目表起價 − 長毛精緻洗價，中長毛與長毛加購價相同（店家指定）。
-- 名稱標「起」：實際價格依造型而定，差額由店員核對時用「自訂金額加購」補收。
-- 只開放中長毛、長毛（短毛沒有大美容）；10kg 以下全開放，中型犬全手剪與 17kg 以上一律「到店面議」（價格 0，到店報價）。
-- 積分依店家指定（中大型以上比照中型）；可打會員折扣、假日可選、可與 AD／HC 一起加購；
-- 首次體驗不能選大美容，也不套用首次體驗 9 折（DogFirstVisitDiscountService）。
-- 設計見 docs/adr/0011-dog-tier-addons.md

INSERT IGNORE INTO grooming_items
    (item_code, name, description, price, is_deleted, bookable, performance_category, points,
     discount_eligible, requires_existing_customer, applicable_pet_type, dog_weight_tier, dog_coat_length, tier_addon)
VALUES
('CUT001', '小型犬-中長毛-大美容電剪（加購，$600起）', '體重5kg以下，中長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 600.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'SMALL', 'MEDIUM', true),
('CUT002', '小型犬-中長毛-大美容半手剪（加購，$1000起）', '體重5kg以下，中長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 1000.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'SMALL', 'MEDIUM', true),
('CUT003', '小型犬-中長毛-大美容全手剪（加購，$1400起）', '體重5kg以下，中長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 1400.0, false, true, 'TRIM', 25.0, true, false, 'DOG', 'SMALL', 'MEDIUM', true),
('CUT004', '小型犬-長毛-大美容電剪（加購，$600起）', '體重5kg以下，長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 600.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'SMALL', 'LONG', true),
('CUT005', '小型犬-長毛-大美容半手剪（加購，$1000起）', '體重5kg以下，長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 1000.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'SMALL', 'LONG', true),
('CUT006', '小型犬-長毛-大美容全手剪（加購，$1400起）', '體重5kg以下，長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 1400.0, false, true, 'TRIM', 25.0, true, false, 'DOG', 'SMALL', 'LONG', true),
('CUT007', '中小型犬-中長毛-大美容電剪（加購，$400起）', '體重6-10kg，中長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 400.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'MEDIUM_SMALL', 'MEDIUM', true),
('CUT008', '中小型犬-中長毛-大美容半手剪（加購，$800起）', '體重6-10kg，中長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 800.0, false, true, 'TRIM', 25.0, true, false, 'DOG', 'MEDIUM_SMALL', 'MEDIUM', true),
('CUT009', '中小型犬-中長毛-大美容全手剪（加購，$1400起）', '體重6-10kg，中長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 1400.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'MEDIUM_SMALL', 'MEDIUM', true),
('CUT010', '中小型犬-長毛-大美容電剪（加購，$400起）', '體重6-10kg，長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 400.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'MEDIUM_SMALL', 'LONG', true),
('CUT011', '中小型犬-長毛-大美容半手剪（加購，$800起）', '體重6-10kg，長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 800.0, false, true, 'TRIM', 25.0, true, false, 'DOG', 'MEDIUM_SMALL', 'LONG', true),
('CUT012', '中小型犬-長毛-大美容全手剪（加購，$1400起）', '體重6-10kg，長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 1400.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'MEDIUM_SMALL', 'LONG', true),
('CUT013', '中型犬-中長毛-大美容電剪（加購，$400起）', '體重11-16kg，中長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 400.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'MEDIUM', 'MEDIUM', true),
('CUT014', '中型犬-中長毛-大美容半手剪（加購，$1000起）', '體重11-16kg，中長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 1000.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'MEDIUM', 'MEDIUM', true),
('CUT015', '中型犬-中長毛-大美容全手剪（加購，到店面議）', '體重11-16kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 35.0, true, false, 'DOG', 'MEDIUM', 'MEDIUM', true),
('CUT016', '中型犬-長毛-大美容電剪（加購，$400起）', '體重11-16kg，長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 400.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'MEDIUM', 'LONG', true),
('CUT017', '中型犬-長毛-大美容半手剪（加購，$1000起）', '體重11-16kg，長毛，搭配精緻洗加購；依造型實際價格於核對時補差額', 1000.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'MEDIUM', 'LONG', true),
('CUT018', '中型犬-長毛-大美容全手剪（加購，到店面議）', '體重11-16kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 35.0, true, false, 'DOG', 'MEDIUM', 'LONG', true),
('CUT019', '中大型犬-中長毛-大美容電剪（加購，到店面議）', '體重17-22kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'MEDIUM_LARGE', 'MEDIUM', true),
('CUT020', '中大型犬-中長毛-大美容半手剪（加購，到店面議）', '體重17-22kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'MEDIUM_LARGE', 'MEDIUM', true),
('CUT021', '中大型犬-中長毛-大美容全手剪（加購，到店面議）', '體重17-22kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 35.0, true, false, 'DOG', 'MEDIUM_LARGE', 'MEDIUM', true),
('CUT022', '中大型犬-長毛-大美容電剪（加購，到店面議）', '體重17-22kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'MEDIUM_LARGE', 'LONG', true),
('CUT023', '中大型犬-長毛-大美容半手剪（加購，到店面議）', '體重17-22kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'MEDIUM_LARGE', 'LONG', true),
('CUT024', '中大型犬-長毛-大美容全手剪（加購，到店面議）', '體重17-22kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 35.0, true, false, 'DOG', 'MEDIUM_LARGE', 'LONG', true),
('CUT025', '大型犬-中長毛-大美容電剪（加購，到店面議）', '體重23-27kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'LARGE', 'MEDIUM', true),
('CUT026', '大型犬-中長毛-大美容半手剪（加購，到店面議）', '體重23-27kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'LARGE', 'MEDIUM', true),
('CUT027', '大型犬-中長毛-大美容全手剪（加購，到店面議）', '體重23-27kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 35.0, true, false, 'DOG', 'LARGE', 'MEDIUM', true),
('CUT028', '大型犬-長毛-大美容電剪（加購，到店面議）', '體重23-27kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'LARGE', 'LONG', true),
('CUT029', '大型犬-長毛-大美容半手剪（加購，到店面議）', '體重23-27kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'LARGE', 'LONG', true),
('CUT030', '大型犬-長毛-大美容全手剪（加購，到店面議）', '體重23-27kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 35.0, true, false, 'DOG', 'LARGE', 'LONG', true),
('CUT031', '特大型犬-中長毛-大美容電剪（加購，到店面議）', '體重28-33kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'EXTRA_LARGE', 'MEDIUM', true),
('CUT032', '特大型犬-中長毛-大美容半手剪（加購，到店面議）', '體重28-33kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'EXTRA_LARGE', 'MEDIUM', true),
('CUT033', '特大型犬-中長毛-大美容全手剪（加購，到店面議）', '體重28-33kg，中長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 35.0, true, false, 'DOG', 'EXTRA_LARGE', 'MEDIUM', true),
('CUT034', '特大型犬-長毛-大美容電剪（加購，到店面議）', '體重28-33kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 20.0, true, false, 'DOG', 'EXTRA_LARGE', 'LONG', true),
('CUT035', '特大型犬-長毛-大美容半手剪（加購，到店面議）', '體重28-33kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 30.0, true, false, 'DOG', 'EXTRA_LARGE', 'LONG', true),
('CUT036', '特大型犬-長毛-大美容全手剪（加購，到店面議）', '體重28-33kg，長毛，搭配精緻洗加購；價格到店面議，核對時補收', 0.0, false, true, 'TRIM', 35.0, true, false, 'DOG', 'EXTRA_LARGE', 'LONG', true);
