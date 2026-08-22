-- V6：一次性資料校正（原本 DataInitializer 裡『每次啟動都跑一次』的校正邏輯）
-- 改用 Flyway 之後這些校正只會真的執行一次，之後店家在後台手動調整這幾個欄位
-- 不會再被系統重啟時悄悄改回去（這是原本『每次啟動都跑』寫法的一個潛在風險，
-- 詳見 DataInitializer.java 保留的說明註解）。

-- 1) 折扣資格校正：GS001~012 + CHECKIN/CHECKOUT/COMPLETE 不打折，其餘計積分項目都打折
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'CHECKIN';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'CHECKOUT';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'COMPLETE';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS001';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS002';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS003';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS004';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS005';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS006';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS007';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS008';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS009';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS010';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS011';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'GS012';
UPDATE grooming_items SET discount_eligible = true WHERE item_code NOT IN ('CHECKIN', 'CHECKOUT', 'COMPLETE', 'GS001', 'GS002', 'GS003', 'GS004', 'GS005', 'GS006', 'GS007', 'GS008', 'GS009', 'GS010', 'GS011', 'GS012');

-- 2) CAT025/026 加購項目不打折
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'CAT025';
UPDATE grooming_items SET discount_eligible = false WHERE item_code = 'CAT026';

-- 3) DOG019~021（中大型犬-短毛）積分分類修正：原本誤判成大狗，短毛23kg門檻內應算小狗
UPDATE grooming_items SET performance_category = 'BATH_SMALL' WHERE item_code = 'DOG019';
UPDATE grooming_items SET performance_category = 'BATH_SMALL' WHERE item_code = 'DOG020';
UPDATE grooming_items SET performance_category = 'BATH_SMALL' WHERE item_code = 'DOG021';

-- 4) CAT013~024 描述文字修正（原本寫「回訪價格」易誤導成跟回洗優惠有關，實際無關）
UPDATE grooming_items SET description = '精緻洗，單層毛貓咪單次服務價格' WHERE item_code = 'CAT013';
UPDATE grooming_items SET description = '精緻洗，雙層毛貓咪單次服務價格' WHERE item_code = 'CAT014';
UPDATE grooming_items SET description = '精緻洗，長毛貓咪單次服務價格' WHERE item_code = 'CAT015';
UPDATE grooming_items SET description = '洗+剃，單層毛貓咪單次服務價格' WHERE item_code = 'CAT016';
UPDATE grooming_items SET description = '洗+剃，雙層毛貓咪單次服務價格' WHERE item_code = 'CAT017';
UPDATE grooming_items SET description = '洗+剃，長毛貓咪單次服務價格' WHERE item_code = 'CAT018';
UPDATE grooming_items SET description = '單層毛貓咪單次服務價格' WHERE item_code = 'CAT019';
UPDATE grooming_items SET description = '雙層毛貓咪單次服務價格' WHERE item_code = 'CAT020';
UPDATE grooming_items SET description = '長毛貓咪單次服務價格' WHERE item_code = 'CAT021';
UPDATE grooming_items SET description = '單層毛貓咪單次服務價格' WHERE item_code = 'CAT022';
UPDATE grooming_items SET description = '雙層毛貓咪單次服務價格' WHERE item_code = 'CAT023';
UPDATE grooming_items SET description = '長毛貓咪單次服務價格' WHERE item_code = 'CAT024';

-- 5) 貓咪 CAT001~028 回填適用物種為 CAT（V2/V3 新建立的已經直接帶入，這裡是保險，
--    確保萬一資料庫裡有更早期、在這批 Flyway 遷移之前就已存在的舊資料也一併校正）
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT001';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT002';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT003';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT004';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT005';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT006';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT007';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT008';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT009';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT010';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT011';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT012';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT013';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT014';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT015';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT016';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT017';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT018';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT019';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT020';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT021';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT022';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT023';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT024';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT025';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT026';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT027';
UPDATE grooming_items SET applicable_pet_type = 'CAT' WHERE item_code = 'CAT028';

-- 6) 狗狗 DOG001~036 回填適用物種為 DOG（同上，保險用途）
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG001';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG002';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG003';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG004';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG005';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG006';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG007';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG008';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG009';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG010';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG011';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG012';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG013';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG014';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG015';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG016';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG017';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG018';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG019';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG020';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG021';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG022';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG023';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG024';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG025';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG026';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG027';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG028';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG029';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG030';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG031';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG032';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG033';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG034';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG035';
UPDATE grooming_items SET applicable_pet_type = 'DOG' WHERE item_code = 'DOG036';
