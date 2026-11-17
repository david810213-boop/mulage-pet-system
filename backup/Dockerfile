# 每日資料庫備份：mysqldump → gzip → AES-256 加密 → rclone 上傳店家 Google 雲端硬碟
# 在 Railway 以排程服務（Cron Schedule）執行，跑完即結束，設定方式見 README.md
FROM rclone/rclone:1 AS rclone

# 用戶端版本要不低於 Railway 上的 MySQL 伺服器，否則 mysqldump 會拒絕連線
FROM mysql:9

RUN microdnf install -y gzip openssl && microdnf clean all

COPY --from=rclone /usr/local/bin/rclone /usr/local/bin/rclone
COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh

# 覆蓋 mysql 映像檔原本會啟動資料庫伺服器的進入點
ENTRYPOINT ["/usr/local/bin/backup.sh"]
