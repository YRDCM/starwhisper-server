#!/bin/bash
# 星语数据库每日备份：mysqldump + gzip，保留最近 7 天。
#
# 凭据不再写在本文件里。请先把账号密码写进 ~/.my.cnf（权限 600）：
#
#   cat > ~/.my.cnf <<'EOF'
#   [mysqldump]
#   user=backup
#   password=这里填真实密码
#   host=127.0.0.1
#   EOF
#   chmod 600 ~/.my.cnf
#
# 建议用只读备份账号（GRANT SELECT, LOCK TABLES, SHOW VIEW, EVENT, TRIGGER），不要用 root。
#
# 由 crontab 每天 03:17 触发：
#   17 3 * * * /opt/starwhisper/backup.sh >> /opt/starwhisper/backup/backup.log 2>&1

set -uo pipefail

DIR=${BACKUP_DIR:-/opt/starwhisper/backup}
DB=${BACKUP_DB:-starwhisper}
KEEP_DAYS=${BACKUP_KEEP_DAYS:-7}
CNF=${MYSQL_DEFAULTS_FILE:-$HOME/.my.cnf}
TS=$(date +%Y%m%d_%H%M%S)
OUT="$DIR/${DB}_${TS}.sql.gz"

log() { echo "$(date '+%F %T') $*"; }

fail() { log "ERROR: $*"; exit 1; }

# ---- 前置检查：宁可失败，也不要静默生成一个空备份 ----
[ -r "$CNF" ] || fail "$CNF 不存在或不可读。请先按脚本头部说明创建它（chmod 600）。"

if [ "$(stat -c %a "$CNF" 2>/dev/null)" != "600" ]; then
  fail "$CNF 权限不是 600（当前 $(stat -c %a "$CNF" 2>/dev/null)）。里面有密码，必须收紧权限。"
fi

command -v mysqldump >/dev/null 2>&1 || fail "找不到 mysqldump"

mkdir -p "$DIR" || fail "无法创建备份目录 $DIR"

# ---- 备份（--single-transaction 保证 InnoDB 一致性且不锁表）----
log "开始备份 $DB -> $OUT"
if ! mysqldump --defaults-extra-file="$CNF" \
      --single-transaction --quick --routines --triggers --events \
      --default-character-set=utf8mb4 \
      "$DB" 2>"$DIR/.dump.err" | gzip > "$OUT"; then
  log "mysqldump 失败：$(cat "$DIR/.dump.err")"
  rm -f "$OUT"
  fail "备份失败，已删除半成品文件"
fi

# ---- 校验：能解压、不是空文件 ----
if ! gzip -t "$OUT" 2>/dev/null; then
  rm -f "$OUT"
  fail "备份文件 gzip 校验失败，已删除"
fi

SIZE_BYTES=$(stat -c %s "$OUT")
if [ "$SIZE_BYTES" -lt 10240 ]; then
  fail "备份文件只有 ${SIZE_BYTES} 字节，疑似空库导出，请人工确认（文件保留在 $OUT）"
fi

log "备份完成：$(basename "$OUT") ($(du -h "$OUT" | cut -f1))"

# ---- 轮转 ----
find "$DIR" -name "${DB}_*.sql.gz" -type f -mtime +"$KEEP_DAYS" -delete
log "已清理 ${KEEP_DAYS} 天前的备份，当前保留 $(find "$DIR" -name "${DB}_*.sql.gz" -type f | wc -l) 份"
