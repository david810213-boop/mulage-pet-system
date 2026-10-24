# 0003 業務例外繼承 IllegalArgumentException，錯誤回應統一格式

## 背景
網頁版（Thymeleaf MVC）controller 有上百處 `catch (IllegalArgumentException e)` 把錯誤訊息顯示在畫面上；REST API 則需要一致的錯誤格式與狀態碼。

## 決策
- `BusinessException(ErrorCode, message)` **刻意繼承 `IllegalArgumentException`**，讓既有的 MVC catch 區塊不用修改就能接住；不要改成 `RuntimeException`。
- `ErrorCode` 決定 HTTP 狀態（不帶時預設 `BUSINESS_RULE`／400；`NOT_FOUND`／404、`FORBIDDEN`／403 等）。
- REST 回應由 `GlobalApiExceptionHandler`（最高優先）統一產生 `{ status, detail, code, message, errors? }`，`RestApiFallbackExceptionHandler` 處理其餘例外（500，並送 Sentry）。controller 不自己組錯誤回應。
- 前端讀 `message` 顯示、依 `code` 判斷；`fetch()` 一律先讀 `res.text()`，能解析成 JSON 才當 JSON。

## 後果
- 新的錯誤情境：`throw new XxxException(ErrorCode.NOT_FOUND, "找不到…")`。
