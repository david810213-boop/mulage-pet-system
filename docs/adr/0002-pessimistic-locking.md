# 0002 儲值金、時段名額、零售庫存以悲觀鎖防止超扣／超賣

## 背景
同一個錢包、同一個預約時段、同一個零售商品可能同時被多個請求修改（例如兩台裝置同時結帳），只靠「先讀再寫」會發生超扣、超收、超賣。

## 決策
- `WalletService.lockWallet`、`SlotCapacityService.lockSlot`、`RetailProductService.lockProduct` 以 `SELECT ... FOR UPDATE`（`@Lock(PESSIMISTIC_WRITE)`）鎖住該列，鎖持有到交易 commit，同一列的修改依序執行。
- 鎖定前先 `entityManager.flush()` 寫出本交易尚未寫入的修改，並把快取中的舊實體 detach，再執行鎖定查詢。
  - 不可改用 `refresh`：實體已持有鎖時 Hibernate 會改發一般查詢，在 MySQL 可重複讀隔離層級下只讀到交易開始時的快照。
  - 不可只用鎖定查詢：實體已在快取中時 Hibernate 會直接回傳快取中的舊值。
- 時段計數列若是在另一個交易（REQUIRES_NEW）中剛建立，必須用鎖定讀取（讀最新已提交資料）才看得到，不能用快照讀取。
- 儲值（deposit）也要上鎖，避免與扣款交錯造成死結或遺失更新。

## 後果
- 並發測試（`Concurrently` 輔助類別）驗證同時扣款／預約／賣出時只會成功應有的次數。
- 新增會修改這三種資源的流程，一律先呼叫對應的 lock 方法。
