-- V15：修正 V13 的 LIKE 規則沒有排除「中長毛」的問題（2026-09-06）
--
-- 背景：V13 用 `name LIKE '%長毛%'` 判斷長毛品項，但「中長毛」這個詞本身
-- 包含「長毛」兩個字（中+長毛），如果之後任何品項名稱裡出現「中長毛」，
-- 這條規則會把它誤判成長毛。
--
-- ⚠️ 目前實際資料沒有被這個問題影響到：V13 執行的當下，中長毛品項
-- （DOG049~072）還不存在，V14 是用 INSERT 直接指定 dog_coat_length='MEDIUM'，
-- 沒有經過 V13 那條 LIKE 規則，所以現有資料是正確的。這筆 migration純粹是
-- 防禦性修正——把判斷規則本身修正得更嚴謹，避免以後如果有人比照 V13 的
-- 寫法、對「中長毛」品項下同樣的 LIKE 規則，重蹈覆轍。
--
-- 這裡沒有直接改 V13 檔案本身：Flyway 對已經執行過的 migration 內容做
-- checksum 檢查，事後修改已執行過的檔案，下次部署會直接報錯中斷。用新增
-- 一筆 migration 修正是安全作法，不管 V13 到底有沒有在正式站執行過都適用。

UPDATE grooming_items
SET dog_coat_length = 'LONG'
WHERE item_code LIKE 'DOG%' AND name LIKE '%長毛%' AND name NOT LIKE '%中長毛%'
  AND (dog_coat_length IS NULL OR dog_coat_length <> 'LONG');

UPDATE grooming_items
SET dog_coat_length = 'MEDIUM'
WHERE item_code LIKE 'DOG%' AND name LIKE '%中長毛%'
  AND (dog_coat_length IS NULL OR dog_coat_length <> 'MEDIUM');
