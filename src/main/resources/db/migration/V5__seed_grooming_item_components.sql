-- V5：套餐組成種子資料（grooming_item_components）——貓咪 CAT001~024 + 狗狗 DOG001~036
-- 逐字從 DataInitializer.java 的 addComponents()/addDogTripletComponents() 呼叫機械展開生成。
--
-- 防呆判斷用 (grooming_item_id, performance_category) 這個組合鍵去查是否已存在，
-- 不是「這個品項底下有沒有任何組成」——因為同一品項在這個檔案裡會連續插入好幾筆
-- 不同分類的組成，如果用『有沒有任何組成』當條件，插入第一筆後，同品項後面幾筆
-- 會被誤判成『已經有了』而跳過。已經用腳本核對過同一品項底下的組成分類彼此
-- 不會重複，用這個組合鍵判斷是安全的。

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT001'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT001'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT002'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT002'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_L', 30.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT003'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_L'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT003'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT004'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT004'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT004'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT005'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT005'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT005'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_L', 30.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT006'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_L'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT006'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT006'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT007'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT007'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT007'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT008'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT008'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT008'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_L', 30.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT009'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_L'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT009'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT009'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT010'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT010'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT010'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT010'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT011'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT011'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT011'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT011'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_L', 30.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT012'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_L'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT012'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT012'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT012'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT013'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT013'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT014'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT014'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_L', 30.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT015'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_L'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT015'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT016'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT016'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT016'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT017'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT017'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT017'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_L', 30.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT018'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_L'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT018'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT018'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT019'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT019'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT019'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT020'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT020'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT020'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_L', 30.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT021'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_L'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT021'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT021'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT022'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT022'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT022'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT022'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_S', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT023'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_S'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT023'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT023'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT023'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_CAT_L', 30.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT024'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_CAT_L'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT024'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'TRIM', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT024'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'TRIM'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'CAT024'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG001'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG001'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG002'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG002'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG003'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG003'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG003'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG004'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG004'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG005'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG005'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG006'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG006'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG006'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG007'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG007'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG008'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG008'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG009'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG009'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG009'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG010'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG010'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG011'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG011'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG012'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG012'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG012'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG013'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG013'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG014'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG014'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG015'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG015'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG015'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG016'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG016'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG017'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG017'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG018'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG018'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG018'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG019'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG019'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG020'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG020'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_SMALL', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG021'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_SMALL'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG021'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG021'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG022'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG022'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG023'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG023'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG024'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG024'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG024'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG025'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG025'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG026'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG026'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG027'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG027'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG027'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG028'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG028'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG029'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG029'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG030'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG030'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG030'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG031'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG031'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG032'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG032'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG033'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG033'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG033'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG034'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG034'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG035'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG035'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BLOW_LARGE', 20.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG036'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BLOW_LARGE'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'BASIC', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG036'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'BASIC'
  )
LIMIT 1;

INSERT INTO grooming_item_components (grooming_item_id, performance_category, points)
SELECT gi.id, 'AD', 10.0
FROM grooming_items gi
WHERE gi.item_code = 'DOG036'
  AND NOT EXISTS (
    SELECT 1 FROM grooming_item_components c
    WHERE c.grooming_item_id = gi.id AND c.performance_category = 'AD'
  )
LIMIT 1;

