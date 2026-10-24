# 架構決策紀錄（ADR）

記錄專案中「為什麼這樣做」的重要設計決策。程式碼註解只保留一行指向對應的 ADR，完整背景與取捨寫在這裡。
變更歷史（何時、因為哪個需求改過）屬於 Git commit 訊息，不寫在程式碼裡，也不寫在 ADR 裡。

| 編號 | 標題 |
|---|---|
| [0001](0001-flyway-after-hibernate.md) | 服務項目種子資料用 Flyway，但在 Hibernate 建表之後才執行 |
| [0002](0002-pessimistic-locking.md) | 儲值金、時段名額、零售庫存以悲觀鎖防止超扣／超賣 |
| [0003](0003-business-exception.md) | 業務例外繼承 IllegalArgumentException，錯誤回應統一格式 |
| [0004](0004-discount-strategy.md) | 折扣逐項目擇優、不疊加，由 DiscountCalculator 統一計算 |
| [0005](0005-snapshot-and-pet-pairing.md) | 項目名稱存快照；以（會員, 寵物名）識別同一隻寵物 |
| [0006](0006-varchar-enum.md) | 列舉一律存成 VARCHAR，不用 MySQL 原生 ENUM |
| [0007](0007-csv-encoding.md) | CSV 匯入先偵測編碼（UTF-8 BOM／UTF-8／Big5／GBK） |
| [0008](0008-duplicate-submission.md) | 金流操作防重複送出：狀態列加鎖＋請求識別碼 |

新增 ADR：複製任一份的格式（背景、決策、後果），編號接續，並更新上表。
