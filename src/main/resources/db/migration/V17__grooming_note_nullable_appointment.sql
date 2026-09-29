-- 需求（2026-09-29）：現場單核對也會寫入毛孩美容狀況紀錄，這種紀錄沒有預約 id，
-- 放寬 pet_grooming_notes.appointment_id 的 NOT NULL 限制
-- （Hibernate ddl-auto=update 不會幫既有欄位拿掉 NOT NULL，所以用 Flyway 處理）。
ALTER TABLE pet_grooming_notes MODIFY COLUMN appointment_id BIGINT NULL;
