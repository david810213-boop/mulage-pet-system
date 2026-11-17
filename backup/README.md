# 資料庫每日自動備份

Railway 目前的方案沒有內建資料庫備份，所以另外跑一個排程服務：每天台北時間 03:00 用 `mysqldump` 匯出正式資料庫，gzip 壓縮、AES-256 加密後，上傳到店家 Google 雲端硬碟的 `mulage-db-backup` 資料夾，並刪除超過 30 天的舊檔。

- 加密密碼（`BACKUP_PASSPHRASE`）**遺失就解不開任何一份備份**，設定時同時存進密碼管理器、並交一份給店家保管。
- 只走 Railway 內部網路（`mysql.railway.internal`），資料庫不用開公開連線。
- 雲端硬碟授權用 `drive.file` 範圍：只能存取 rclone 自己建立的檔案，看不到店家雲端硬碟裡其他東西。

## 一、Google 端（店家 Google Cloud 專案 `mulage-pet`，用店家帳號登入）

Google 不允許服務帳戶寫入個人 Gmail 的雲端硬碟，所以改用店家帳號 OAuth 授權一次。

1. 「API 和服務 → 程式庫」啟用 **Google Drive API**
2. 「OAuth 同意畫面」：使用者類型選「外部」，填應用程式名稱與聯絡信箱，**發布狀態改為「正式版」**。停在「測試中」的話，授權 7 天後就失效，備份會開始失敗
3. 「憑證 → 建立憑證 → OAuth 用戶端 ID」，類型選「電腦版應用程式」，記下**用戶端 ID** 與**用戶端密鑰**

## 二、自己電腦（Windows PowerShell）取得授權

```powershell
winget install Rclone.Rclone
# 重開 PowerShell 後
rclone config
```

依序回答：`n`（新增）→ 名稱 `gdrive` → 儲存類型選 `drive`（Google Drive）→ `client_id`、`client_secret` 貼上一步的值 → `scope` 選 **drive.file** → `service_account_file` 留空 → 進階設定 `n` → 自動設定 `y`（會開瀏覽器，**用店家帳號登入並同意**）→ 共用雲端硬碟 `n` → `y` 確認。

```powershell
rclone mkdir gdrive:mulage-db-backup
rclone config show gdrive
```

最後一個指令會印出 `token = {...}`，把大括號整段（一行）複製下來，下一步要用。

## 三、Railway 新增排程服務

1. 專案畫布「+ Create → GitHub Repo」，選 `mulage-pet-system`
2. 新服務 Settings：
   - **Source → Root Directory**：`/backup`
   - **Source → Branch**：`main`
   - **Deploy → Cron Schedule**：`0 19 * * *`（Railway 用 UTC，等於台北 03:00）
   - **Deploy → Restart Policy**：Never（失敗不要一直重試）
   - 服務名稱改成 `db-backup`
3. Variables（前五個用 Railway 的參照變數，從 MySQL 服務帶入）：

| 變數 | 值 |
| :---- | :---- |
| `MYSQLHOST` | `${{MySQL.MYSQLHOST}}` |
| `MYSQLPORT` | `${{MySQL.MYSQLPORT}}` |
| `MYSQLUSER` | `${{MySQL.MYSQLUSER}}` |
| `MYSQLPASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `MYSQLDATABASE` | `${{MySQL.MYSQLDATABASE}}` |
| `BACKUP_PASSPHRASE` | 自己產生的長密碼（建議 32 字元以上） |
| `RCLONE_CONFIG_GDRIVE_CLIENT_ID` | 第一節的用戶端 ID |
| `RCLONE_CONFIG_GDRIVE_CLIENT_SECRET` | 第一節的用戶端密鑰 |
| `RCLONE_CONFIG_GDRIVE_TOKEN` | 第二節複製的 `{...}` 整段 |
| `RETENTION_DAYS`（選填） | 保留天數，預設 30 |

4. 部署完成後，到服務的 **Cron Runs** 按 **Run now**（或在已 `railway link` 的資料夾執行 `railway cron run --service db-backup`）手動跑一次。Log 最後出現「完成」，雲端硬碟 `mulage-db-backup` 出現 `mulage-日期-時間.sql.gz.enc` 就成功

## 四、還原測試（第一次設定完一定要做一次）

從雲端硬碟下載一份備份，用 Git Bash：

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass 'pass:你的BACKUP_PASSPHRASE' \
  -in mulage-20261120-0300.sql.gz.enc | gunzip > restore.sql

# 還原到本機 Docker 的另一個資料庫，不要蓋掉 petdb
docker exec -i pet-mysql mysql -uroot -p你的本機密碼 -e "CREATE DATABASE restore_test CHARACTER SET utf8mb4"
docker exec -i pet-mysql mysql -uroot -p你的本機密碼 restore_test < restore.sql
docker exec -i pet-mysql mysql -uroot -p你的本機密碼 restore_test -e "SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM grooming_items;"
```

筆數跟正式站一致就代表備份可用。測完 `DROP DATABASE restore_test;`，並刪掉電腦上解密出來的 `restore.sql`（裡面有顧客個資）。

## 五、日常確認

- 每週看一次雲端硬碟資料夾，最新檔案的日期應該是昨天或今天
- Railway `db-backup` 服務的 Cron Runs 出現失敗（紅色）時，看 log 最後一行的原因：多半是 OAuth 授權失效（同意畫面沒有發布為正式版，或店家帳號撤銷了授權），重做第二節取得新的 token 再更新變數
