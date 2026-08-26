# 疑難排解與環境設定指南（更新版）

**更新日期：2026-08-24**（本次更新：Flyway 已正式接進來、新增 Thymeleaf SpEL 樣板中斷陷阱、th:onclick 語法陷阱、Maven 依賴版本查證教訓）

---

## 一、雙電腦交替開發：換行符號問題

**症狀**：切換電腦後 `git status` 顯示幾乎所有檔案都是「已修改」，但看不出實際改了什麼。

**原因**：CRLF（Windows）跟 LF（倉庫既有格式）不一致，Git 把每一行都當成「刪除+新增」。

**確認方式**：
```
git diff --stat <某個檔案>
git diff <某個檔案>
```

**清除假性差異**：
```
git checkout -- .
```

**長期建議**：在專案根目錄加 `.gitattributes`，內容 `* text=auto`，兩台電腦都設定 `git config core.autocrlf true`。

---

## 二、本機 MySQL 有兩個實例，容易連錯

- **原生安裝的 MySQL**：預設 port **3306**
- **Docker 容器 `pet-mysql`**：對外開 port **3307**（容器內部仍是3306）

**App 實際使用的是 Docker 容器（3307）**：
```
mysql -h 127.0.0.1 -P 3307 -u root -p petdb
```

**確認連對地方**：查詢一個最近才新增的表格是否存在（例如 `grooming_item_components`、`pricing_settings`、`cat_breed_coat_mappings`、`flyway_schema_history`），單純查 port 變數不可靠。

---

## 三、正式環境（Railway）資料庫連線

**本機與正式環境資料庫完全獨立、不同步**。

```bash
npm i -g @railway/cli
railway login
cd <專案資料夾>
railway link
railway connect MySQL
```

`USE railway;`（正式環境資料庫名稱是 `railway`，不是本機的 `petdb`）。

**⚠️ 公司電腦連不了**：`railway connect` 走 SSH tunnel，公司網路防火牆常常會擋，這種情況下只能等回到本機電腦（沒有防火牆限制的環境）再查，不用勉強處理。

如果要用 GUI 工具連，需要先到 Railway 後台開 **Public Networking**，用完務必關閉。

**目前是店家測試階段，不是正式營運資料**——備份不用嚴格看待，測試資料壞了大不了照測試手冊重跑一次建起來。等真正上線變成正式營運資料後，這個判斷要重新收緊。

---

## 四、MySQL 原生 ENUM 欄位陷阱

Hibernate `ddl-auto=update` **不會**自動幫已存在的 ENUM 欄位擴充允許值清單，會報 `Data truncated for column 'xxx' at row 1`。

**確認方式**：`SHOW COLUMNS FROM <表名> LIKE '<欄位名>';`，`Type` 顯示 `enum(...)` 且缺少新值就是這個問題。

**修法**：改成 VARCHAR：`ALTER TABLE <表名> MODIFY COLUMN <欄位名> VARCHAR(30) NOT NULL;`

已處理過的欄位：`payment_method`、`source`、`status` 等。**之後新增欄位存列舉值，直接用 VARCHAR，不要讓 Hibernate 自動建成 ENUM。**

---

## 五、種子資料管理：已改用 Flyway（✅ 架構性解決，不再是陷阱）

### 陷阱回顧（歷史問題，已解決）

`DataInitializer` 用 `count()==0` 判斷「只在全新資料庫時建立」，正式站資料表早就不是空的，新增的種子資料永遠沒被種進去。這個陷阱至少踩過兩次。

### ✅ 根本解法：Flyway 只管「資料」，schema 仍交給 Hibernate

**關鍵坑：Flyway 預設執行時機在 Hibernate 建表之前**。Spring Boot 預設讓 Flyway 在應用程式啟動最早期執行，早於 `ddl-auto=update` 建表——全新資料庫上會因為表還不存在而直接失敗。

**解法**：`application.yml` 設 `spring.flyway.enabled=false` 關掉內建自動機制，自己寫 `ApplicationRunner`（`DataSeedMigrationRunner`）手動控制在 Hibernate 建完表之後才呼叫 `Flyway.migrate()`。

```yaml
spring:
  flyway:
    enabled: false   # 關掉 Spring Boot 內建的自動啟動機制
    locations: classpath:db/migration
```

**遷移檔案寫法要點**：
- 全部用 `INSERT IGNORE`（靠 UNIQUE 限制防重複），不管全新或既有資料庫都安全
- 涉及外鍵關聯用 `INSERT ... SELECT ... WHERE NOT EXISTS (...)`
- **防呆判斷鍵要下對範圍**：同一批次對同一個父資料連續插入好幾筆子資料時，`WHERE NOT EXISTS` 的判斷條件要精確到「這筆子資料本身」（例如「父ID+分類」），不能只判斷「父資料底下有沒有任何一筆」——後者會導致同批次插入第一筆之後，後面幾筆被誤判成「已經有了」而跳過（這是實際踩過的坑）
- 遷移檔案命名 `V1__xxx.sql`、`V2__xxx.sql`……版本號遞增，`flyway_schema_history` 表記錄執行過的版本，終身只執行一次

**以後新增服務項目種子資料，寫新的 `Vn__xxx.sql` 遷移檔案，不要再回頭改 `DataInitializer.java`**。

**大量資料轉譯的安全做法**：把既有 Java 種子資料轉成 SQL 遷移檔案時，寫腳本從原始碼機械抽取數值（不是手動重打），抽取後核對筆數是否跟原始碼手算的一致，當作基本驗證（這次遷移 91 筆服務項目+156 筆套餐組成都是這樣做，事後也真的抓到一個轉譯過程中自己加進去、原始邏輯沒有的多餘條件，靠核對機制發現並修正）。

**轉譯過程中意外發現的資料一致性問題**：「每次啟動都跑」的校正迴圈如果排除清單漏了某個項目代碼，會導致那個項目的欄位被非預期覆蓋（實際案例：`CAT028` 建立時設定不打折，但折扣校正迴圈的排除清單漏了它，導致每次重啟都被改回可打折，持續發生一段時間才發現）。**這類迴圈的排除清單，新增/修改時要格外小心逐一核對。**

---

## 六、LINE LIFF 多頁面架構（✅ 已全數完成）

**已確認結論**：LINE 官方**不支援**「單一 LIFF ID + Endpoint URL 路徑轉發」到不同頁面。

**正確做法**：每個要在 LINE 裡開啟的獨立頁面，各自申請一組 LIFF App，Endpoint URL 直接指向那個確切頁面，LIFF URL 使用時**不接任何路徑**。

**9 個頁面全數獨立**：index / new-customer / bind-line / my-profile / add-pet（同一頁靠 `?editPetId=` 判斷新增/編輯模式）/ my-pets / booking / my-appointments / wallet

**申請設定**：Size：Compact，Scopes：`openid`+`profile`，Add friend option：On (Normal)

**其他 LIFF 相關坑**：
1. `liff.login()` 務必帶 `{ redirectUri: window.location.href }`
2. `lineUserId` 資料庫是唯一值，同一支手機的LINE只能綁一個系統帳號
3. 新增給「未登入狀態」呼叫的 `/api/**` 端點，一定要手動加進 `WebConfig` 攔截器白名單

---

## 七、Thymeleaf 陷阱集錦

### 7.1 inline JavaScript 序列化陷阱

用 `/*[[${變數}]]*/` 塞資料進頁面內的 JS 變數，背後是 Jackson 序列化。**塞整個 JPA entity（尤其含 `LocalDateTime` 欄位）容易序列化失敗，導致整頁渲染中斷、瀏覽器顯示 `ERR_INCOMPLETE_CHUNKED_ENCODING`。**

**修法**：只塞畫面真正需要的欄位，用輕量 Map 或 DTO：
```java
model.addAttribute("retailProducts", retailProductService.listActive().stream()
        .map(p -> java.util.Map.of("id", p.getId(), "name", p.getName(), "price", p.getPrice()))
        .toList());
```

### 7.2 樣板裡的複雜 SpEL 運算式會讓畫面整個截斷（新）

**症狀**：畫面渲染到某個區塊突然停住，後面所有內容（包含按鈕、其他表單欄位）全部消失不見。

**原因**：Thymeleaf 預設是**串流輸出**，樣板運算式如果在渲染中途噴出例外（例如對 `#strings.arraySplit(...)` 產生的陣列做 `.?[...]` 選擇運算子，邊界情況處理不夠穩健），**已經輸出到瀏覽器的部分會保留，後面的內容永遠不會再輸出**——不會顯示錯誤訊息，畫面看起來就像「莫名其妙少了一截」，很難第一時間聯想到是樣板運算式的問題。

**修法**：避免在 Thymeleaf 樣板裡對字串/陣列做複雜的選擇（`.?[...]`）、投影（`.![...]`）運算，改成**在 Controller 先把資料算好成乾淨的 `List<String>` 或 `boolean`，樣板只做最單純的 `list.contains(x)` 判斷**。這種寫法更穩定，出問題時也好除錯（可以直接在 Controller 加中斷點/log 檢查，不用猜樣板引擎在做什麼）。

### 7.3 `onclick` 屬性裡不能用 `[[...]]` 內嵌語法（新）

**症狀**：按鈕點了完全沒反應，沒有任何錯誤訊息或提示。

**原因**：`onclick="unlockPet([[${pet.id}]])"` 這種寫法，**`[[...]]` 這種 Thymeleaf 內嵌表達式語法只有在 `<script th:inline="javascript">` 區塊裡才會被解析**，放在一般 HTML 屬性（`onclick="..."`）裡不會被處理，瀏覽器收到的是字面上壞掉的 JS 語法 `unlockPet([[${pet.id}]])`，點了自然沒反應，瀏覽器 console 通常只會有一個語法錯誤，容易被忽略。

**修法**：一律改用 `th:onclick`：
```html
<button th:onclick="'unlockPet(' + ${pet.id} + ')'">...</button>
```

**排查方式**：如果按鈕點了沒反應，先檢查一下產生出來的 HTML 原始碼（瀏覽器開發者工具「檢查元素」），看 `onclick` 屬性裡的值是不是真的被 Thymeleaf 替換成實際數值，還是原封不動留著 `${...}` 或 `[[...]]` 字樣。

---

## 八、前端錯誤訊息被吞掉

`fetch()` 呼叫 API，後端錯誤回應如果是純文字（不是JSON），前端寫死用 `res.json()` 解析會拋例外被 `.catch()` 接住，畫面永遠只顯示同一句寫死的備援錯誤文字。

**修法**：
- 後端統一回傳 JSON 格式錯誤（`ResponseEntity.badRequest().body(Map.of("message", e.getMessage()))`）
- 前端先讀 `res.text()`，能解析成JSON才當JSON用

---

## 九、資安體檢後的修補經驗

### 1. LIFF 頁面存放型 XSS

使用者輸入塞進 `innerHTML` 前，一律先跑過 `escapeHtml()` 函式（每個 LIFF 頁面各自複製一份，沒有共用模組）。純文字顯示優先用 `textContent` 取代 `innerHTML`。

### 2. Cookie 沒有 `SameSite` 屬性

`jakarta.servlet.http.Cookie` 不支援設定，改用 `CookieUtils.buildJwtCookieHeader()` 手動組 `Set-Cookie` 標頭，帶 `SameSite=Lax`。

### 3. 寫死在程式碼裡的預設帳密，repo 又是 Public

啟動時偵測密碼是否還是預設值，是的話印出警告 log。

### 4. 確認沒問題的部分

SQL Injection（全走 JPA）、IDOR（都用 JWT 解出的 username 查資料）、角色權限（`@RequireRole` 一致）、註冊流程（role 寫死 CUSTOMER）、LINE 登入（有驗證 idToken+aud）、檔案上傳（有檢查 content-type）。

---

## 十、Maven 依賴管理：版本號要查證，不能憑印象寫（新）

**這個開發沙盒環境連不到 Maven Central**，沒辦法實際跑 `mvn compile` 驗證依賴版本存不存在、彼此相不相容。新增 Google Calendar API 相關依賴時，憑印象寫的版本號（`google-api-services-calendar:v3-rev20240815-2.0.0`）**在 Maven Central 上根本不存在**，本機建置直接失敗。

**正確做法**：新增任何 Maven 依賴，尤其是不常用、版本號有特殊格式的套件（像 Google API 系列常見 `vX-revYYYYMMDD-Z.Y.Z` 這種格式），**先上網搜尋確認 Maven Central 上真實存在的版本號**，不要憑印象或用「合理猜測」的方式編。查證後的正確版本可以先寫在程式碼註解裡，方便之後追溯。

---

## 十一、其他經驗提醒

- **交付檔案務必給完整內容**，不要只給片段patch
- **每次改動後先做括號/標籤配對覆查**（Python script 數 `{`/`}`、`<div>`/`</div>` 等）
- **寫程式前先確認 DTO/entity 實際欄位存在**，不要憑印象假設
- **新增到既有資料表的 NOT NULL 欄位，一律搭配 `columnDefinition` 給資料庫層級預設值**
- **push前先跑敏感資訊檢查**：
```powershell
Select-String -Path .\src\main\resources\application.yml -Pattern "cloudinary|CLOUDINARY|api_secret|channel-access-token" -CaseSensitive:$false
```
- **新增資料庫欄位/表格不用手動下SQL**，`ddl-auto=update` 自動處理，enum 型別欄位優先用VARCHAR
- **服務項目種子資料一律走 Flyway**，不要回頭改 `DataInitializer.java`
- **Thymeleaf 樣板避免複雜 SpEL 運算式**，改成後端先算好資料
- **`onclick` 一律用 `th:onclick`**，不要在一般 HTML 屬性裡塞 `[[...]]`
- **新增 Maven 依賴版本號要先搜尋查證**，這個環境沒辦法實際編譯驗證