# 慕沐村 Mulage Pet 寵物美容管理系統 — 專案背景（給 Claude Code 自動讀取）

> 這份檔案每次啟動 Claude Code 都會自動載入，只放「每次寫程式都用得到」的內容。
> 業務規則、測試流程、上線清單等較長的參考資料放在 `docs/` 資料夾，需要時再去讀，
> 讀取指引見本檔最後一節。

## 一、技術棧

- **後端**：Spring Boot 4.0.6、Java 17（本機曾出現 Java 21/25 環境，注意版本一致性）
- **模板**：Thymeleaf（伺服器端渲染，`th:` 語法）
- **資料庫**：MySQL，Spring Data JPA + Hibernate 7.2.12，`ddl-auto: update`，服務項目種子資料另用 Flyway 管理（見第五節）
- **建置**：Maven（`mvn clean compile`、`mvn spring-boot:run`，本機跑在 **port 8081**）。`pom.xml` 有 `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`，這個設定務必保留，不要移除（原因見第四節）
- **其他函式庫**：Lombok、Apache POI（Excel 匯出）、Cloudinary SDK（圖片雲端儲存）、Google Calendar API
- **前端**：純 HTML + 原生 JS（LIFF 頁面），無框架
- **套件名稱**：`com.petgrooming.pet_system`

## 二、部署與環境

- **GitHub**：`david810213-boop/mulage-pet-system`，開發分支 **`redesign`**，公開作品集分支 **`main`**（README.md 放 `main`，兩分支的 README 內容不一樣，改 README 要先確認自己在哪個分支）
- **正式環境**：Railway，`MySQL` 服務 + `mulage-pet-system`（App）服務，兩者已同區域（跨區延遲問題已解決，但 N+1 查詢問題本身還沒處理，見第六節）
- **本機資料庫**：Docker 容器 `pet-mysql`，對外 **port 3307**（容器內部仍是 3306）；⚠️ 這台電腦另外還有一個**原生 MySQL（port 3306）**，連線前務必先確認 port
- **連線正式環境資料庫**：`railway link` → `railway connect MySQL`（走 SSH tunnel，不用開 Public Networking）
- **本機與正式環境資料庫完全獨立、不同步**
- **環境變數**（`application.yml` 用 `${...}` 讀取，不寫死在檔案裡）：`DB_HOST/PORT/NAME/USERNAME/PASSWORD`、`JWT_SECRET`、`LINE_CHANNEL_ID`、`LINE_MESSAGING_CHANNEL_ACCESS_TOKEN`、`CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET`、`GOOGLE_CALENDAR_SERVICE_ACCOUNT_JSON_BASE64`、`GOOGLE_CALENDAR_ID`（後兩個沒設定會自動停用該功能，不影響其他功能）

## 三、雙軌身分認證架構

- **店家/員工**：帳號密碼登入網頁版，JWT 存在 Cookie，`LoginInterceptor` + `RoleInterceptor`（搭配 `@RequireRole` 註解）做權限控管
- **會員（顧客）**：透過 LINE LIFF 頁面，`liff.getIDToken()` 驗證後換發 JWT
- **⚠️ 重要**：新增任何 `/api/**` 端點，如果是要給「未登入狀態」呼叫的（例如 LINE 登入/綁定），**一定要手動加進 `WebConfig` 攔截器的白名單**（踩過這個坑：`/api/line/bind` 一開始漏加）
- 老客戶用電話號碼自助認領既有匯入資料（既有會員資料匯入功能，見 `docs/功能總覽與測試手冊.md` 七之一）

## 四、Java 特殊符號編碼踩坑（教訓成本很高，務必詳讀）

涉及生僻 Unicode 裝飾符號（Emoji、特殊符號）的字串：

- 一律用 `\uXXXX`（或代理對 `\uD8XX\uDCXX`）逃逸序列寫進字串，不要直接貼原始符號（位元組容易在存檔/複製貼上過程中被弄壞，導致 `javac` 報 `illegal start of expression`）
- **註解裡完全不要出現「反斜線＋u」這兩個字元相連的文字**，也不要放對照用的實際符號——Java 編譯器不分字串或註解，只要看到反斜線接 `u` 就會嘗試解析十六進位，一樣會報 `illegal unicode escape`
- `pom.xml` 的 `UTF-8` 編碼宣告是好習慣但不是萬靈藥，不能取代上述做法
- **交付涉及生僻符號的程式碼前，先在沙盒安裝 JDK 用 `javac` 實際編譯驗證過再打包**，不要只憑肉眼或猜測

完整除錯過程見 `docs/疑難排解與環境設定指南.md` 第十一節。

## 五、資料庫踩過的坑

1. **MySQL 原生 ENUM 欄位**：Hibernate `ddl-auto=update` 不會自動幫已存在的 ENUM 欄位擴充允許值，Java enum 加新值直接寫入會報 `Data truncated`。已修正做法：**改用 VARCHAR 存 enum**，不要依賴 MySQL 原生 ENUM
2. **新增 NOT NULL 欄位**：一律搭配 `columnDefinition = "... default ..."`，避免對已有資料的表格做 `ALTER TABLE` 失敗
3. **Thymeleaf inline JavaScript**（`/*[[${x}]]*/`）用 Jackson 序列化，不要把整個 JPA entity 塞進去（容易序列化失敗導致整頁渲染中斷），只塞畫面真正需要的欄位（用 Map 或輕量 DTO）
4. **Appointment / WalkInOrderItem 的品項名稱是「快照」**，不是即時關聯——服務項目改名不會影響歷史紀錄顯示的名稱，這是刻意設計
5. **服務項目種子資料用 Flyway 管理**：schema 仍由 Hibernate `ddl-auto=update` 自動處理，但 `GroomingItem`/`GroomingItemComponent` 的種子資料全部搬到 `src/main/resources/db/migration/Vn__xxx.sql`，由 `DataSeedMigrationRunner`（`ApplicationRunner`，手動控制在 Hibernate 建表之後才執行 `Flyway.migrate()`）負責。**以後新增服務項目種子資料，寫新的 `Vn__xxx.sql` 遷移檔案，不要再回頭改 `DataInitializer.java`**。店家會持續自己在後台調整的營運設定（時段容量範本、公休設定、匯款帳戶等）維持 `existsBy()`/lazy 初始化寫法，不走 Flyway
6. **CSV 匯入的編碼問題**（2026-09 踩坑）：讀取上傳檔案不能無條件假設 UTF-8——Windows 存出的 CSV 常是 Big5，`InputStreamReader` 解碼失敗時不會拋例外，而是靜默替換成 `U+FFFD`，導致 CJK 欄位（姓名、寵物名字、品種）整批變亂碼或解析失敗，但純數字/ASCII 欄位（電話、金額）不受影響，容易誤判成「只有部分資料匯入失敗」。**CSV 讀取一律先偵測編碼（BOM 判斷 + 嘗試 UTF-8 嚴格解碼失敗後退回 Big5/GBK），去除開頭 BOM，且解析失敗要記錄清楚是第幾列、哪個欄位，不要靜默跳過**

## 六、已知效能技術債（尚未解決）

`AppointmentService.getAllForAdmin()` 對每一筆歷史預約各自觸發多次額外查詢（N+1 查詢問題），Dashboard 跟部分後台列表頁會因此變慢。跨區域網路延遲問題已透過資料庫遷移解決，但 **N+1 查詢本身還沒修**，隨資料量增加可能再次變慢。修法方向：`getAllForAdmin()` 改用 `@BatchSize` 或 `JOIN FETCH` 把 N 次查詢併成 1~2 次；Dashboard 首頁改成只查「今天」「待確認」等真正需要的少量資料，不要撈全部歷史。完整排查記錄見 `docs/疑難排解與環境設定指南.md` 第十四節。

## 七、給接手對話的操作提醒（每次改動都要遵守）

- 每次修改都要交付**完整檔案**（不要只給片段）
- 每次改動後主動做**括號/標籤配對覆查**
- **交付程式碼前，先在沙盒環境實際驗證過語法**：Java 用 `javac`，JS 用 `node --check`
- **涉及非同步流程改動時（例如加 `await`），用 jsdom 走過完整初始化流程實際測試，不要只做語法檢查**——純語法檢查抓不到「執行順序」類的錯誤
- **`str_replace` 編輯時，`old_str` 的範圍要包含完整的舊內容**（含上方緊鄰的 annotation/註解），不要只匹配部分內容——曾經因為範圍沒包含完整的 `@GetMapping` 註解，導致重複標註讓整個專案編譯失敗，且錯誤訊息會出現大量不相關的 `cannot find symbol` 假象（因為 Lombok 註解處理整個失敗），排查心法是先找有沒有一個具體的語法錯誤，優先修那一個
- 涉及生僻 Unicode 裝飾符號的字串，一律先轉成 `\u` 逃逸序列，連解釋用的註解裡都不能出現「反斜線＋u」相連的文字
- `fetch()` 呼叫一律預期後端可能回傳純文字錯誤，不要寫死用 `res.json()` 解析，先讀 `res.text()` 能解析成 JSON 才當 JSON 用
- 合併 Git 分支衝突時，如果雙方版本內容差異很大，一定要問清楚使用者要保留哪一版，不要自己選
- **系統效能問題排查，先用瀏覽器 Performance API 實測 TTFB，再看 Railway 即時 log 找實際 SQL 執行模式，不要只憑猜測**
- **文件更新時，新版本要是完全獨立、自我完整的內容，不要寫「詳見前版本」「內容不變」這種依賴舊版才看得懂的引用**

## 八、需要時再讀的參考文件

平常開發不用主動讀下面這幾份，只有在對應情境出現時才去讀：

- **業務規則、折扣邏輯、預約篩選細節、完整測試流程** → 讀 `docs/功能總覽與測試手冊.md`
- **遇到編譯錯誤、環境設定問題、想查過去踩過的坑的完整記錄** → 讀 `docs/疑難排解與環境設定指南.md`
- **上線前檢查、資安待辦事項** → 讀 `docs/正式上線前準備工作清單.md`
