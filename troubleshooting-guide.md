# 疑難排解與環境設定指南（更新版）

**更新日期：2026-08-22**（本次更新：LIFF 9 頁面全數獨立完成、新增資安修補經驗、新增資料庫初始化「一次性 vs 每次啟動」陷阱）

---

## 一、雙電腦交替開發：換行符號問題

**症狀**：切換電腦後 `git status` 顯示幾乎所有檔案都是「已修改」，但看不出實際改了什麼。

**原因**：CRLF（Windows）跟 LF（倉庫既有格式）不一致，Git 把每一行都當成「刪除+新增」。

**確認方式**：
```
git diff --stat <某個檔案>
git diff <某個檔案>
```
如果看到整個檔案逐行刪除又新增，但文字內容一樣，就是這個問題，不是真的改了內容。

**清除假性差異**：
```
git checkout -- .
```

**長期建議**：在專案根目錄加 `.gitattributes`，內容 `* text=auto`，讓 Git 自動處理換行符號正規化，兩台電腦都設定 `git config core.autocrlf true`（Windows 慣例）。

---

## 二、本機 MySQL 有兩個實例，容易連錯

這台電腦（不管是主機或公司電腦）常見會同時存在：
- **原生安裝的 MySQL**：預設 port **3306**
- **Docker 容器 `pet-mysql`**：對外開 port **3307**（容器內部仍是3306）

**App 實際使用的是 Docker 容器（3307）**，本機測試連資料庫務必指定正確 port：

```
mysql -h 127.0.0.1 -P 3307 -u root -p petdb
```

**確認連對地方**的方法：查詢一個最近才新增的表格是否存在（例如 `retail_products`、`store_supplies`、`grooming_item_components`、`pricing_settings`），如果查不到，代表連錯了。單純查 `SHOW VARIABLES LIKE 'port'` 不可靠——Docker 埠轉發會讓容器內部回報的還是 3306，不會顯示 3307。

---

## 三、正式環境（Railway）資料庫連線

**本機資料庫跟 Railway 正式環境資料庫完全獨立、不同步**——本機測試的資料不會出現在正式站，反之亦然。

**連線方式**：安裝並使用 Railway CLI（不用開 Public Networking 對外暴露資料庫）：

```bash
npm i -g @railway/cli
railway login
cd <專案資料夾>
railway link          # 選 empowering-commitment 專案 → production 環境 → MySQL 服務
railway connect MySQL
```

連進去後跟一般 MySQL 操作一樣，`USE railway;`（正式環境資料庫名稱是 `railway`，不是本機的 `petdb`）。

如果一定要用 GUI 工具連（例如 MySQL Workbench）連正式環境，需要先到 Railway 後台 MySQL 服務 → Settings → Networking → 啟用 **Public Networking**，拿到對外的 Host/Port，**用完務必關閉**，避免長期對外暴露。

---

## 四、MySQL 原生 ENUM 欄位陷阱

Hibernate `ddl-auto=update` **不會**自動幫已存在的 ENUM 欄位擴充允許值清單。Java enum 加新值後，插入資料庫會報：

```
Data truncated for column 'xxx' at row 1
```

**確認方式**：
```sql
SHOW COLUMNS FROM <表名> LIKE '<欄位名>';
```
如果 `Type` 顯示 `enum(...)` 且缺少新值，就是這個問題。

**修法**：改成 VARCHAR，不要依賴 MySQL 原生 ENUM：
```sql
ALTER TABLE <表名> MODIFY COLUMN <欄位名> VARCHAR(30) NOT NULL;
```

已經處理過的欄位：`transactions.payment_method`、`walk_in_orders.payment_method`、`topup_requests.status`、`users.source`。**之後新增欄位如果要存列舉值，直接用 VARCHAR，不要讓 Hibernate 自動建成 ENUM。**

---

## 五、`DataInitializer` 種子資料：「只跑一次」vs「每次啟動都跑」陷阱（新增）

**症狀**：在 `DataInitializer.run()` 裡新增了種子資料的程式碼，本機全新資料庫測試正常，但部署到正式站（Railway）之後，新資料完全沒有被建立進去。

**原因**：`DataInitializer` 裡很多區塊是用 `if (xxxRepository.count() == 0)` 包起來的，**只有在整張表完全是空的時候才會執行**。正式站的 `grooming_items` 表早就有資料了（`count()` 不是 0），這個判斷式直接跳過，新增的種子資料永遠不會被種進去——本機測試看不出這個問題，是因為本機常常用的是全新的資料庫。

**正確做法**：新增的種子資料如果是要「補進既有資料庫」（不管本機還是正式站都要生效），不能包在 `count()==0` 判斷式裡，要改成**逐筆檢查、不存在才新增**：

```java
private void addItemIfNotExists(String code, ...) {
    if (groomingItemRepository.existsByItemCode(code)) {
        return; // 已存在就跳過，不會重複建立或覆蓋店家後續改過的資料
    }
    // ... 建立邏輯
}
```

**同樣的道理也適用於「回填/校正既有資料」**（例如某個欄位新增時所有既有列的值都不對，要一次性修正）：不要只包在 `count()==0` 裡，要寫成「每次啟動都跑一次的校正迴圈」，不管資料庫現況為何，重複執行也不會有副作用：

```java
for (GroomingItem item : groomingItemRepository.findAll()) {
    boolean shouldBeX = ...; // 算出這筆資料「應該」是什麼值
    if (item.isX() != shouldBeX) {
        item.setX(shouldBeX);
        groomingItemRepository.save(item);
    }
}
```

**判斷原則**：
- 「這筆資料如果不存在才要建立」→ 用逐筆 `existsByXxx()` 檢查
- 「這個欄位所有既有資料都要修正成正確值」→ 用每次啟動都跑的校正迴圈
- 只有「整個系統從來沒初始化過」這種情境（例如預設帳號、獎勵金級距這種一次性種子資料）才適合用 `count()==0` 包起來

**這個陷阱目前在專案裡至少發生過兩次**：一次是「已完成需求清單」的貓咪服務項目（GS013 系列），一次是後來重做的 CAT/DOG 套餐化項目——都是靠改成 `addItemIfNotExists()` 這種模式才解決。

---

## 六、LINE LIFF 多頁面架構（✅ 已全數完成）

**已確認結論**：LINE 官方**不支援**「單一 LIFF ID + Endpoint URL 路徑轉發」到不同頁面。LIFF ID 後面接的路徑會被包進 `liff.state` 參數，瀏覽器只會停在 Endpoint URL 本身，不會真的轉發過去。

**正確做法**：每個要在 LINE 裡開啟的獨立頁面，各自申請一組 LIFF App，Endpoint URL 直接指向那個確切頁面，LIFF URL 使用時**不接任何路徑**。

**目前已申請的 LIFF App（9 個頁面全數獨立，之前記錄的「6 個頁面待補」已完成）**：

| 頁面 | 檔案 |
|---|---|
| 首頁 | index.html |
| 新客報到 | new-customer.html |
| 綁定LINE | bind-line.html |
| 編輯個人資料 | my-profile.html |
| 新增毛孩 | add-pet.html |
| 我的寵物 | my-pets.html |
| 預約 | booking.html |
| 我的預約 | my-appointments.html |
| 錢包 | wallet.html |

**申請新 LIFF App 的設定**：
- Size：Compact
- Scopes：勾 `openid` + `profile`
- Add friend option：On (Normal)

**其他 LIFF 相關坑**：
1. `liff.login()` 務必帶 `{ redirectUri: window.location.href }`，否則可能造成登入循環或跳轉錯誤畫面
2. `lineUserId` 資料庫是唯一值，同一支手機的LINE只能綁一個系統帳號（顧客或店員擇一），測試時容易撞到自己先前測試會員功能時自動建立的顧客帳號
3. 新增任何 `/api/**` 端點，如果要給「未登入狀態」呼叫（例如LINE登入/綁定），一定要手動加進 `WebConfig` 攔截器白名單，否則會被誤擋

---

## 七、Thymeleaf inline JavaScript 序列化陷阱

用 `/*[[${變數}]]*/` 把後端資料塞進頁面內的 JS 變數時，背後是用 Jackson 做序列化。**如果直接塞整個 JPA entity（尤其含 `LocalDateTime` 欄位），容易序列化失敗，導致整頁渲染中斷、瀏覽器顯示連線中斷（`ERR_INCOMPLETE_CHUNKED_ENCODING`）。**

**修法**：只塞畫面真正需要的欄位，用輕量 Map 或 DTO，不要整個 entity：
```java
model.addAttribute("retailProducts", retailProductService.listActive().stream()
        .map(p -> java.util.Map.of("id", p.getId(), "name", p.getName(), "price", p.getPrice()))
        .toList());
```

**排查方式**：後端終端機的錯誤堆疊裡找 `InvalidDefinitionException`、`Jackson` 相關字樣。

---

## 八、前端錯誤訊息被吞掉

`fetch()` 呼叫 API，如果後端錯誤回應是純文字（不是JSON），前端寫死用 `res.json()` 解析會拋例外被 `.catch()` 接住，導致畫面永遠只顯示同一句寫死的備援錯誤文字，看不到真正原因。

**修法**：
- 後端統一回傳 JSON 格式的錯誤（`ResponseEntity.badRequest().body(Map.of("message", e.getMessage()))`）
- 前端先讀 `res.text()`，能解析成JSON才當JSON用：
```js
const rawText = await res.text();
let data = null;
try { data = JSON.parse(rawText); } catch (e) { data = null; }
```

---

## 九、資安體檢後的修補經驗（新增，2026-08-22）

專案做過一次全面資安體檢，發現並修補了以下幾類問題，記錄下來避免之後重蹈覆轍：

### 1. LIFF 頁面存放型 XSS（Stored XSS）

**症狀**：LIFF 頁面（純 HTML + 原生 JS，沒有框架）大量用樣板字串 + `innerHTML` 組畫面，例如：
```js
document.getElementById("app").innerHTML = pets.map(p => `<div>${p.name}</div>`).join("");
```
`p.name`（寵物名稱）是顧客自己填的，如果沒有跳脫直接塞進 `innerHTML`，顧客可以把寵物名字設成 `<img src=x onerror=alert(1)>` 之類的內容，之後只要有人（甚至是顧客自己重新整理頁面）打開這個畫面，惡意 JS 就會執行。

**修法**：所有會被塞進 `innerHTML` 的使用者輸入（寵物名稱/品種/注意事項、服務項目名稱/描述等），顯示前一律先跑過一個 `escapeHtml()` 函式：
```js
function escapeHtml(str) {
  if (str === null || str === undefined) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
```
**注意**：這幾個 LIFF 頁面是各自獨立的靜態 HTML 檔案，沒有共用 JS 模組機制，所以 `escapeHtml()` 目前是**每個檔案各自複製一份**。新增或修改 LIFF 頁面時，只要畫面會用 JS 把使用者輸入塞進 `innerHTML`，記得也要複製這個函式並套用，不能假設其他頁面有跳脫就代表這裡也安全。

如果之後某個地方只是顯示純文字、沒有要插入 HTML 標籤，優先用 `el.textContent = msg` 取代 `el.innerHTML = msg`（`textContent` 天生安全，不用額外跳脫），像專案裡 `showToast()` 這個函式本來就是這樣寫。

### 2. Cookie 沒有 `SameSite` 屬性

`jakarta.servlet.http.Cookie` 這個舊版 Servlet API **不支援**設定 `SameSite`，只用 `cookie.setHttpOnly()` / `setSecure()` 沒辦法補上這個屬性。要嘛換成 Spring 的 `ResponseCookie`，要嘛手動組 `Set-Cookie` 標頭字串：
```java
response.addHeader("Set-Cookie", "JWT_TOKEN=" + token + "; Path=/; Max-Age=86400; HttpOnly; SameSite=Lax");
```
專案裡已經抽成 `CookieUtils.buildJwtCookieHeader()` 共用方法，登入/登出/LINE 登入三個地方都改用這個。

### 3. 寫死在程式碼裡的預設帳密，repo 又是 Public

`DataInitializer` 裡建立預設帳號（`admin@pet.com` 等）時密碼是明文寫在程式碼裡，而這個 GitHub repo 是 Public——等於帳密直接公開給所有人看。就算密碼本身有用 `PasswordEncoder`（bcrypt）雜湊儲存進資料庫，**原始密碼本身洩漏出去，還是能直接拿去登入**，雜湊儲存防的是資料庫外洩，防不了原始碼外洩。

**修法**：沒辦法完全避免種子帳號需要一組初始密碼，但可以在啟動時偵測「目前密碼是否還等於這組已公開的預設值」，是的話印出顯眼警告：
```java
if (passwordEncoder.matches(defaultPassword, user.getPassword())) {
    log.warn("⚠️⚠️⚠️ 帳號 {} 目前密碼仍是原始碼裡的預設值，這組帳密已經公開在 GitHub repo 上！", username);
}
```
**部署後的檢查方式**：看系統啟動 log 有沒有這幾行警告，沒有就代表密碼都已經改過了。

### 4. 資安體檢時順便確認過、沒發現問題的部分

記錄下來避免之後又花時間重查：
- SQL Injection：全部走 JPA/Hibernate，沒有字串拼接組查詢
- IDOR：錢包/寵物/預約相關 API 都是從 JWT 解出來的 username 去查資料，不是信任前端傳的 ID
- 角色權限：後台 controller 一致有 `@RequireRole` 或手動 `isAdmin()` 檢查
- 註冊流程：`role` 欄位沒有開放給前端填，寫死 `CUSTOMER`，沒有提權漏洞
- LINE 登入：有確實打 LINE 官方 API 驗證 idToken，還檢查 `aud`，沒有信任前端自稱的身分
- 檔案上傳：有檢查 content-type 必須是 `image/*`

---

## 十、其他經驗提醒

- **交付檔案務必給完整內容**，不要只給片段patch，容易漏套用（曾發生 `slots-manage.html` 漏套用，導致公休日功能做好了但畫面看不到）
- **每次改動後先做括號/標籤配對覆查**（開發環境連不到 Maven Central，沒辦法實際編譯驗證）
- **寫程式前先確認 DTO/entity 實際欄位存在**，不要憑印象假設（曾誤用不存在的 `RetailProduct.getUnitCost()` 導致編譯失敗）
- **新增到既有資料表的 NOT NULL 欄位，一律搭配 `columnDefinition` 給資料庫層級預設值**，否則 `ALTER TABLE` 會因既有列無值可填而失敗
- **push前先跑敏感資訊檢查**：
```powershell
Select-String -Path .\src\main\resources\application.yml -Pattern "cloudinary|CLOUDINARY|api_secret|channel-access-token" -CaseSensitive:$false
```
確認都是 `${...}` 環境變數寫法，沒有寫死的密鑰
- **新增資料庫欄位/表格不用手動下SQL**，`ddl-auto=update` 自動處理，但 enum 型別欄位要優先用VARCHAR（見第四點）
- **種子資料/回填邏輯記得分清楚「只跑一次」還是「每次啟動都跑」**（見第五點），這是這次開發週期踩得最多次的坑
