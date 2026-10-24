# 0006 列舉一律存成 VARCHAR，不用 MySQL 原生 ENUM

## 背景
Hibernate `ddl-auto=update` 不會幫已存在的 MySQL ENUM 欄位擴充允許值；Java enum 新增值後寫入會報 `Data truncated`。

## 決策
- 列舉欄位用 `@Enumerated(EnumType.STRING)` 搭配 VARCHAR 欄位，不依賴 MySQL 原生 ENUM。
- 新增 NOT NULL 欄位一律加 `columnDefinition = "... default ..."`，避免對既有資料的資料表 `ALTER TABLE` 失敗。

## 後果
- 新增列舉值不需要手動改資料庫。
