-- V9：幫貓咪套餐主力項目（CAT001~024）標上毛髮分類（2026-08-23，菜單簡化功能）
-- 依項目名稱本身的「單層毛/雙層毛/長毛貓」文字對應（跟 V2 建立這批項目時
-- 用的分組規則一致：每 3 項一組，單層毛/雙層毛/長毛貓依序循環）。
-- CAT025~028（加購項目）跟毛髮分類無關，維持 NULL，不受篩選影響。

UPDATE grooming_items SET cat_coat_category = 'SINGLE_LAYER' WHERE item_code IN ('CAT001', 'CAT004', 'CAT007', 'CAT010', 'CAT013', 'CAT016', 'CAT019', 'CAT022');
UPDATE grooming_items SET cat_coat_category = 'DOUBLE_LAYER' WHERE item_code IN ('CAT002', 'CAT005', 'CAT008', 'CAT011', 'CAT014', 'CAT017', 'CAT020', 'CAT023');
UPDATE grooming_items SET cat_coat_category = 'LONG_HAIR' WHERE item_code IN ('CAT003', 'CAT006', 'CAT009', 'CAT012', 'CAT015', 'CAT018', 'CAT021', 'CAT024');
