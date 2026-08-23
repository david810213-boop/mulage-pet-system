-- V8：貓咪品種→毛髮分類對照表種子資料（2026-08-23，菜單簡化功能）
-- 依店家提供的品種清單建立初始對照，之後可在後台「貓咪品種毛髮分類對照表」
-- 頁面自行新增/調整，不用再靠改這個遷移檔案或重新部署。
-- 用 INSERT IGNORE（靠 breed_name 的 UNIQUE 限制）確保重複執行安全。

INSERT IGNORE INTO cat_breed_coat_mappings (breed_name, coat_category, sort_order) VALUES
('米克斯', 'SINGLE_LAYER', 10),

('曼赤肯', 'DOUBLE_LAYER', 20),
('英國短毛貓', 'DOUBLE_LAYER', 21),
('美國短毛貓', 'DOUBLE_LAYER', 22),
('加菲貓', 'DOUBLE_LAYER', 23),
('摺耳貓', 'DOUBLE_LAYER', 24),
('捲毛貓', 'DOUBLE_LAYER', 25),

('布偶貓', 'LONG_HAIR', 30),
('挪威森林貓', 'LONG_HAIR', 31),
('緬因貓', 'LONG_HAIR', 32),
('小步舞曲貓', 'LONG_HAIR', 33),
('金吉拉貓', 'LONG_HAIR', 34),
('英國長毛貓', 'LONG_HAIR', 35),
('喜馬拉雅貓', 'LONG_HAIR', 36);
