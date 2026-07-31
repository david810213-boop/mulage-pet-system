# 慕沐村 Mulage pet — 寵物美容店全端管理系統

一套從零開始獨立設計、開發並**實際用於店家日常營運**的寵物美容預約與管理系統。涵蓋顧客端 LINE 預約、店內後台管理、金流儲值與員工績效計算，非教學專案或練習作品。

> 開發者：David Hong｜服務業第一線資歷 18 年，自學程式設計後獨力用Ai協作完成本專案

---

## 這是什麼

慕沐村 Mulage pet 解決的是寵物美容店的真實營運痛點：顧客怎麼線上預約、店家怎麼避免同時段超收、金流怎麼安全地儲值扣款、員工績效怎麼公平拆算。系統分成兩端：

- **顧客端**：透過 LINE LIFF 完成登入、預約、查看毛孩美容紀錄、儲值與查詢消費紀錄，不需額外下載 App。
- **店家後台**：Spring MVC + Thymeleaf 管理介面，涵蓋預約確認、現場開單、會員管理、儲值審核、員工績效報表等。

---

## 技術棧

| 分類 | 技術 |
|---|---|
| 後端框架 | Spring Boot 4 (Java 17)、Spring MVC、Spring Data JPA |
| 資料庫 | MySQL 8(Docker 部署） |
| 認證 / 授權 | JWT + 自訂 Interceptor 角色權限機制（未引入完整 Spring Security，避免與既有機制衝突）、BCrypt 密碼雜湊 |
| 前端 | Thymeleaf（後台）、LINE LIFF SDK + 原生 HTML/CSS（顧客端） |
| 第三方整合 | LINE Login、LINE Messaging API（預約狀態推播） |
| 建置工具 | Maven |

---

## 技術亮點：三個實際解決過的工程問題

### 1. 金流併發扣款的競態條件（Race Condition）
原本的錢包扣款邏輯是「讀取餘額 → 比較 → 寫回」，在高併發情境下會出現超扣風險。改以 `SELECT ... FOR UPDATE` 悲觀鎖重新讀取錢包列後才扣款，確保同一顧客的併發請求會被序列化處理，杜絕餘額被錯誤扣除的情況。

### 2. 同時段預約超收
店家規定同一時段最多接待 5 隻寵物，但「還不存在的預約列」沒有東西可以上鎖。設計了獨立的時段容量計數表（`SlotCapacity`），對「時段」本身加悲觀鎖，讓併發預約請求依序處理、即時回報剩餘名額，從根本上避免超賣。

### 3. Hibernate `ddl-auto=update` 的實務限制
專案沒有導入 Flyway，純靠 Hibernate 自動建表。實際踩坑後歸納出一套規範：新增 NOT NULL 欄位必須搭配資料庫層級預設值（`columnDefinition`），因為 Hibernate 無法自動為既有資料列回填；欄位限制放寬（NOT NULL → nullable）不會自動生效，需要手動下 `ALTER TABLE`。這套規範已整理成內部文件，供後續開發／維運人員查閱。

---

## 資安事件處理紀錄：憑證外洩的發現與清除

開發過程中發現本機環境變數檔（`.env`，內含資料庫連線資訊）被誤 commit 進版本控制，且已存在於遠端多個分支的歷史紀錄中。處理流程如下，記錄下來作為往後專案的標準作業程序：

1. **定位範圍**：逐一檢查所有分支（`main`／`dev`／`redesign`／`feature/*`）的 commit 歷史與當前工作目錄，確認問題實際影響範圍，而非只看單一分支。
2. **立即止血**：`git rm --cached` 將檔案移出版控追蹤，並補強 `.gitignore` 規則（`.env`、`data/`、`*.db` 等），防止再次被誤加入。
3. **歷史清除**：使用 `git filter-repo` 改寫本機 repo 的完整歷史，移除所有 commit 中曾經包含的機密檔案；並對受影響的每個分支個別執行 `--force` 推送，逐一驗證清除結果。
4. **憑證輪替**：即使歷史已清除，仍將外洩過的資料庫密碼視為已洩漏處理，於雙開發環境（主機 Docker / 公司電腦 Portable MySQL）同步更換新密碼並驗證連線正常。
5. **事後驗證**：清除完成後，重新從遠端抓取每個分支並逐一確認 `.env`／`data/` 已不存在於任何歷史紀錄或工作目錄中。

> 這起事件也回饋強化了 `.gitignore` 規則與開發習慣：機密設定一律透過 `.env.example` 提供範例格式，實際 `.env` 從一開始就排除在版控之外。

---

## 系統涵蓋的完整業務邏輯

- 預約狀態機：待確認 → 已確認 → 進行中 → 已完成，含店家最終確認時間
- 現場開單（Walk-in）：非預約到店消費，員工經手人可事後補填並自動計算績效
- 會員儲值：僅限轉帳，採「申請 → 店家核帳 → 入帳」兩段式流程，避免顧客端直接竄改餘額
- 會員分級與升等紅利規則
- 員工績效積分：依接待／結帳／完成分類計算，支援多人拆分並避免浮點數誤差
- 預約備注雙可見性：內部備注（員工專用）與會員可見備注嚴格於 DTO 層隔離，避免內部資訊外洩給顧客

---

## 專案結構

```
src/main/java/com/petgrooming/pet_system/
├── controller/     REST API（顧客端）+ MVC Controller（後台頁面）
├── service/        商業邏輯層
├── model/          JPA Entity
├── dto/            Request / Response 物件，嚴格區分內外部可見欄位
├── repository/     Spring Data JPA Repository
├── enums/          狀態機、角色、付款方式等列舉
├── interceptor/    JWT 驗證 + 角色權限攔截器
├── notification/   LINE 推播整合
└── config/         啟動設定、資料回填邏輯
```

---

## 本機啟動

```bash
# 1. 啟動 MySQL（需先建立 .env，內容參考 .env.example）
docker start pet-mysql

# 2. 啟動應用程式
mvn spring-boot:run

# 3.（選用）若需要外部測試 LINE LIFF，另開終端機執行
ngrok http 8081
```

應用程式預設運行於 `http://localhost:8081`。

---

## 開發說明

- 開發歷程橫跨兩台工作機（主機 / 公司電腦）協作，累積多次資料庫遷移（H2 → MySQL）與需求迭代
- Git 歷史保留了真實的開發與修正過程，未經過整理美化
- 分支策略：`main`（穩定版）、`dev`（整合開發）、`redesign`（UI 重新設計）、`feature/*`（單一功能開發，如 `feature/line-auth`）

---

## 專案連結
https://github.com/david810213-boop/mulage-pet-system

