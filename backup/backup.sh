#!/bin/bash
# 每日資料庫備份。必填環境變數見 README.md；任何一步失敗都以非 0 結束，Railway 會把這次執行標成失敗。
set -euo pipefail

: "${MYSQLHOST:?未設定 MYSQLHOST}"
: "${MYSQLPORT:?未設定 MYSQLPORT}"
: "${MYSQLUSER:?未設定 MYSQLUSER}"
: "${MYSQLPASSWORD:?未設定 MYSQLPASSWORD}"
: "${MYSQLDATABASE:?未設定 MYSQLDATABASE}"
: "${BACKUP_PASSPHRASE:?未設定 BACKUP_PASSPHRASE}"
: "${RCLONE_CONFIG_GDRIVE_TOKEN:?未設定 RCLONE_CONFIG_GDRIVE_TOKEN}"

FOLDER="${BACKUP_FOLDER:-mulage-db-backup}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
STAMP="$(TZ=Asia/Taipei date +%Y%m%d-%H%M)"
WORK="$(mktemp -d)"
FILE="${WORK}/mulage-${STAMP}.sql.gz.enc"
trap 'rm -rf "${WORK}"' EXIT

# 密碼寫進暫存設定檔，不放在指令列參數（避免出現在程序清單）
umask 077
cat > "${WORK}/my.cnf" <<CNF
[client]
host=${MYSQLHOST}
port=${MYSQLPORT}
user=${MYSQLUSER}
password=${MYSQLPASSWORD}
CNF

echo "[備份] 開始匯出 ${MYSQLDATABASE}（${STAMP}）"
mysqldump --defaults-extra-file="${WORK}/my.cnf" \
    --single-transaction --quick --routines --triggers \
    --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 \
    "${MYSQLDATABASE}" \
  | gzip -9 \
  | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:BACKUP_PASSPHRASE -out "${FILE}"

SIZE="$(stat -c %s "${FILE}")"
if [ "${SIZE}" -lt 1024 ]; then
    echo "[備份] 失敗：檔案只有 ${SIZE} bytes，匯出內容不完整" >&2
    exit 1
fi
echo "[備份] 加密完成，${SIZE} bytes"

export RCLONE_CONFIG_GDRIVE_TYPE=drive
export RCLONE_CONFIG_GDRIVE_SCOPE=drive.file

rclone copy "${FILE}" "gdrive:${FOLDER}"
echo "[備份] 已上傳 gdrive:${FOLDER}/$(basename "${FILE}")"

rclone delete "gdrive:${FOLDER}" --min-age "${RETENTION_DAYS}d" --include "mulage-*.sql.gz.enc"
echo "[備份] 已刪除超過 ${RETENTION_DAYS} 天的舊備份，完成"
