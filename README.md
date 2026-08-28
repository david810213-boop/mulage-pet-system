# 慕沐村 Mulage Pet — 寵物美容店全端管理系統

> 一套從零開始設計、開發，並**實際運行於店家日常營運**的全端寵物美容店管理系統。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1)
![LINE](https://img.shields.io/badge/LINE-LIFF-00C300)
![Deploy](https://img.shields.io/badge/deploy-Railway-0B0D0E)
![Status](https://img.shields.io/badge/status-production-brightgreen)

---

## 關於這個專案

**慕沐村 Mulage pet** 是為合作的寵物美容店獨立開發的全端管理系統，目前**每天實際用於營運**：顧客用 LINE 預約，店員用後台開單收款，店家用報表看業績。

開發背景：服務業第一線資歷 18 年，非本科出身，透過自學完成架構設計、後端開發、資料庫設計、第三方串接與正式環境部署。以下記錄的是開發過程中遇到的幾個實際問題，以及當時的分析與解法。

---

## 系統能做什麼

| 顧客端（LINE LIFF） | 店家後台 |
|---|---|
| 免下載 App，LINE 內直接登入 | 預約確認、時段容量管理（含週期範本＋單日覆寫） |
| 線上預約、查看毛孩美容紀錄 | 現場開單（含純零售訂單） |
| 上傳毛孩照片、管理寵物檔案 | 會員儲值審核、金流對帳 |
| 儲值、查詢消費紀錄 | 庫存管理（零售商品 + 店用耗材） |
| 加入 Google 日曆 | 財務報表（Excel 匯出） |
| | 員工績效自動計算 |

---

## 技術棧

| 分類 | 技術 |
|---|---|
| 後端框架 | Spring Boot 4 (Java 17)、Spring MVC、Spring Data JPA |
| 資料庫 | MySQL 8（本機 Docker 開發，正式環境 Railway 託管），種子資料以 Flyway 管理 |
| 認證 / 授權 | JWT + 自訂 Interceptor 角色權限機制、BCrypt 密碼雜湊 |
| 前端 | Thymeleaf（後台）、LINE LIFF SDK + 原生 HTML/CSS/JS（顧客端） |
| 第三方整合 | LINE Login、LINE Messaging API、Cloudinary（圖片雲端儲存）、Google Calendar API |
| 檔案匯出 | Apache POI（財務報表 Excel 匯出） |
| 部署 | Railway（App + MySQL），GitHub Actions 自動部署 |

---

## 工程決策與問題排查（節選）

以下是專案中幾個具代表性的技術決策，記錄當時遇到的問題、考慮過的取捨，以及最後採用的做法。

### 1. 金流併發扣款的競態條件（Race Condition）

**問題**：原本的錢包扣款邏輯是「讀取餘額 → 比較 → 寫回」，在同一顧客短時間內觸發多筆請求時，可能出現餘額被超扣的風險。

**解法**：改用 `SELECT ... FOR UPDATE` 悲觀鎖重新讀取錢包列後才扣款，讓同一顧客的併發請求被序列化處理，從資料庫層級杜絕超扣。

### 2. 同時段預約超收

**問題**：店家規定同一時段最多接待固定數量的寵物，但「還不存在的預約列」沒有東西可以上鎖，光靠應用層判斷無法防止併發超賣。

**解法**：設計獨立的時段容量計數表（`SlotCapacity`），對「時段」本身加悲觀鎖，讓併發預約請求依序處理、即時回報剩餘名額。

### 3. 時段容量設定：週期範本與單日覆寫的分層設計

**問題**：店家反饋預約時間過於鬆散，希望能針對一天當中每個時段分別設定固定的預設可接組數（例如某時段固定接 4 組、下一個時段暫停、之後每個時段固定接 1 組），但同時仍需要能因應現場突發狀況（例如單一顧客一次帶多隻寵物）即時關閉後續時段。

**解法**：拆成兩層機制——一份通用的預設容量範本（不分星期幾），決定每個時段的預設上限；既有的單日容量計數表則保留作為即時覆寫用途，覆寫只影響當天、不回溯影響範本本身。範本值採 lazy 初始化寫法（第一次讀取時若不存在才建立預設列），而非透過資料庫遷移檔案寫入固定值，原因是這是店家會持續調整的營運設定，不是需要跨環境保持一致的固定資料。

### 4. 服務項目種子資料：從 `count()==0` 判斷到 Flyway

**問題**：服務項目的初始資料原本用 `count()==0` 判斷「只在全新資料庫時建立」，但正式站資料表早已不是空的，導致新增的種子資料實際上從未被種進正式環境，這個問題重複發生過。

**解法**：改用 Flyway 管理種子資料的新增與回填，schema 本身仍交由 Hibernate `ddl-auto=update` 處理；由於 Spring Boot 預設會讓 Flyway 在 Hibernate 建表之前執行，另外寫了一個 `ApplicationRunner` 手動控制執行順序，確保 Flyway 遷移一定在建表完成之後才跑。

### 5. Hibernate `ddl-auto=update` 的其他實務限制

專案未在 schema 本身導入額外遷移工具，純靠 Hibernate 自動建表，過程中歸納出幾條實務規範：新增 `NOT NULL` 欄位需搭配 `columnDefinition` 預設值，避免對既有資料的表格做 `ALTER TABLE` 時失敗；`ENUM` 型別改用 `VARCHAR` 儲存，因為 Hibernate 不會自動幫已存在的 ENUM 欄位擴充允許值清單。

### 6. LINE LIFF 多頁面路由的官方限制

**問題**：原本假設可用「單一 LIFF ID + Endpoint URL 路徑轉發」服務多個頁面，實測後行為不一致。

**解法**：查證 LINE 官方文件後確認 LIFF URL 的路徑會被包裝進 `liff.state` 查詢參數並不會真的轉發，改為「一個功能頁面對應一組獨立 LIFF App」的官方支援架構，目前共 9 個頁面各自獨立申請。

### 7. 前端錯誤訊息被靜默吞掉

**問題**：`fetch()` 呼叫後端 API 時，若錯誤回應是純文字而非 JSON，前端寫死用 `res.json()` 解析會拋出例外並被 `.catch()` 接住，導致看不到真正的失敗原因。

**解法**：統一後端錯誤回應格式為 JSON；前端改為先讀 `res.text()`，能解析成 JSON 才當 JSON 用。

---

## 業務邏輯複雜度（節選）

- **業績認列**嚴格區分「儲值」（預收款）與「結帳完成」（實際業績），兩者不混算
- **折扣互斥**：會員儲值折扣、貓咪 90 天回洗優惠、貓狗首次體驗優惠三者擇優適用，不疊加計算
- **服務項目套餐化**：貓狗價目表依毛長/體型/服務深度拆成數十種組合，並依寵物的體重/品種/毛髮分類自動篩選菜單；狗狗成犬可鎖定固定套餐，不用每次重新依體重篩選
- **假日限定完整套餐**：週六日顧客線上預約僅能選擇完整套餐，不開放單項加購，此限制只影響線上預約，店員現場開單不受影響
- **雙軌庫存扣減**：零售商品在結帳成功時扣庫存，店用耗材則依員工「領用登記」扣庫存，兩者觸發時機與資料模型不同
- **預約備注雙可見性**：內部備注（員工專用）與會員可見備注在 DTO 層隔離，避免內部資訊外洩給顧客
- **員工績效積分**：依接待／結帳／完成分類計算，支援多人拆分並處理浮點數誤差

---

## 專案結構

```
src/main/java/com/petgrooming/pet_system/
├── controller/     REST API（顧客端）+ MVC Controller（後台頁面）
├── service/        商業邏輯層
├── model/          JPA Entity
├── dto/            Request / Response 物件，嚴格區分內外部可見欄位
├── repository/     Spring Data JPA Repository
├── enums/          狀態機、角色、付款方式、折扣類型等列舉
├── interceptor/    JWT 驗證 + 角色權限攔截器
├── notification/   LINE 推播整合
└── config/         啟動設定、資料回填邏輯

src/main/resources/
├── templates/      Thymeleaf 後台頁面
├── db/migration/   Flyway 種子資料遷移檔案
└── static/liff/    LIFF 顧客端頁面（各自對應獨立 LIFF App）
```

---

## 本機啟動

```bash
# 1. 啟動本機 MySQL（Docker）
docker start pet-mysql

# 2. 設定環境變數（資料庫連線、JWT 密鑰、LINE / Cloudinary / Google Calendar 金鑰等）
#    請透過環境變數注入，不寫死於程式碼

# 3. 啟動應用程式
mvn spring-boot:run
```

應用程式預設運行於 `http://localhost:8081`。

---

## 關於開發過程

- 開發歷程橫跨兩台工作機協作，累積多次資料庫遷移與需求迭代
- Git 歷史保留實際開發過程中的修正紀錄，未經過整理
- 分支策略：`main`（穩定版）、`dev`（整合開發）、`redesign`（目前主要開發分支）、`feature/*`（單一功能開發）

---

## 關於我

David Hong，服務業第一線資歷 18 年，自學程式設計後獨力開發並持續維運這套系統。

架構決策、技術細節或背景相關問題歡迎聯繫：

- Email： david810213@gmail.com

---

## 授權

本專案為 慕沐村 Mulage Pet 實際營運系統，版權所有，未經授權不得轉載或商用。原始碼公開僅供技術審閱與作品集展示用途。
