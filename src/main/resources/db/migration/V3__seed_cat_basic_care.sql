-- V3：貓咪基礎保養（CAT027）+ 長毛貓加購（CAT028）
-- 這 2 筆原始碼是直接用 GroomingItem.builder() 手寫（不是走 saveItem() helper），
-- 抽取腳本抓不到，這裡逐行核對 DataInitializer.java 第189~213行手動轉譯：
--   CAT027：貓咪基礎保養，400元，BASIC分類（10積分），requires_existing_customer=true
--   CAT028：長毛貓加購，100元，OTHER分類（0積分），discount_eligible=false，requires_existing_customer=true

INSERT IGNORE INTO grooming_items (item_code, name, description, price, is_deleted, bookable, performance_category, points, discount_eligible, requires_existing_customer, applicable_pet_type) VALUES ('CAT027', '貓咪基礎保養', '剃腳底毛、肛門周邊毛髮修整、耳道清潔、牙齒清潔、修剪指甲；僅限既有客戶，不適用初次來店貓咪', 400.0, false, false, 'BASIC', 10.0, true, true, 'CAT');

INSERT IGNORE INTO grooming_items (item_code, name, description, price, is_deleted, bookable, performance_category, points, discount_eligible, requires_existing_customer, applicable_pet_type) VALUES ('CAT028', '修剪圓圓饅頭腳（長毛貓加購）', '搭配貓咪基礎保養加購，僅限長毛貓', 100.0, false, false, 'OTHER', 0.0, false, true, 'CAT');
