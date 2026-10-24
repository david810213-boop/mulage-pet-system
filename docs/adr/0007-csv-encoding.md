# 0007 CSV 匯入先偵測編碼（UTF-8 BOM／UTF-8／Big5／GBK）

## 背景
Windows 存出的 CSV 常是 Big5。`InputStreamReader` 解碼失敗時不會拋例外，而是靜默替換成 U+FFFD，中文欄位整批變亂碼，但數字欄位正常，容易誤判成「只有部分資料匯入失敗」。

## 決策
- `CsvImportFileReader` 先看 BOM；沒有 BOM 時依序嚴格嘗試 UTF-8 → Big5 → GBK，都失敗才用 UTF-8 寬鬆模式並在 log 警告；開頭的 BOM 一併去除。
- 解析失敗要寫清楚第幾列、哪個欄位；欄位出現 U+FFFD 時直接擋下並說明是編碼問題，不靜默跳過。

## 後果
- `MemberCsvImportTest` 驗證 UTF-8 BOM 與 Big5 檔案都能正確匯入。
- 新的 CSV 匯入功能一律透過 `CsvImportFileReader` 讀檔。
