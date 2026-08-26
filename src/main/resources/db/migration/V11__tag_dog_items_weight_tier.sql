-- V11：幫狗狗套餐項目（DOG001~036）標上體重級距（2026-08-24，狗狗定價流程簡化）
-- 對照 V4 建立時每 6 項一組的體重描述文字：
--   DOG001~006：小型5kg以下  DOG007~012：中小型6-10kg  DOG013~018：中型11-16kg
--   DOG019~024：中大型17-22kg  DOG025~030：大型23-27kg  DOG031~036：特大型28-33kg

UPDATE grooming_items SET dog_weight_tier = 'SMALL' WHERE item_code IN ('DOG001','DOG002','DOG003','DOG004','DOG005','DOG006');
UPDATE grooming_items SET dog_weight_tier = 'MEDIUM_SMALL' WHERE item_code IN ('DOG007','DOG008','DOG009','DOG010','DOG011','DOG012');
UPDATE grooming_items SET dog_weight_tier = 'MEDIUM' WHERE item_code IN ('DOG013','DOG014','DOG015','DOG016','DOG017','DOG018');
UPDATE grooming_items SET dog_weight_tier = 'MEDIUM_LARGE' WHERE item_code IN ('DOG019','DOG020','DOG021','DOG022','DOG023','DOG024');
UPDATE grooming_items SET dog_weight_tier = 'LARGE' WHERE item_code IN ('DOG025','DOG026','DOG027','DOG028','DOG029','DOG030');
UPDATE grooming_items SET dog_weight_tier = 'EXTRA_LARGE' WHERE item_code IN ('DOG031','DOG032','DOG033','DOG034','DOG035','DOG036');
