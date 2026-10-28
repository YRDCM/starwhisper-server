# deploy —— 服务器运维脚本

> 这个目录**只放脚本和配置模板**。构建产物、数据库导出、接口样例、日志一律不放（见 `.gitignore`）。

## 首次使用

```bash
# 1) 装依赖
pip install paramiko

# 2) 复制环境变量模板并填写
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env

# 3) 把公钥装到服务器（之后所有命令零密码）
ssh-copy-id -i ~/.ssh/id_ed25519.pub deploy@your.server
```

### 必需的服务器前提

脚本默认走**密钥登录 + 严格主机指纹校验**，所以服务器侧要先准备好：

- 建一个普通账号（例如 `deploy`）并加入 sudo 组，**不要用 root 直接部署**
- `/etc/ssh/sshd_config` 里设置 `PasswordAuthentication no`、`PermitRootLogin no`
- 端口 8080 **不要对公网开放**（后端依赖 `X-Forwarded-For` 还原真实 IP，暴露后该头可伪造）

## 脚本一览

| 脚本 | 用途 |
|---|---|
| `ssh.py` | 通用 SSH 助手：`exec` 执行命令 / `put` 上传 / `get` 下载 |
| `put-dist.py` | `ssh.py put` 的便捷封装，用于上传构建产物 |
| `backup.sh` | 数据库每日备份（服务器上跑，由 crontab 触发） |
| `_sshutil.py` | 上面三个的公共模块，负责读环境变量、建连接、校验主机指纹 |

## 常用命令

```bash
# 看服务状态
python deploy/ssh.py exec "systemctl status starwhisper --no-pager"

# 上传新 jar（先本地 mvn package）
python deploy/put-dist.py target/starwhisper-server.jar /opt/starwhisper/starwhisper-server.jar

# 重启并看启动日志
python deploy/ssh.py exec "systemctl restart starwhisper && sleep 5 && journalctl -u starwhisper -n 50 --no-pager"
```

## 数据库备份（在服务器上配置）

`backup.sh` 从 `~/.my.cnf` 读凭据，**不在脚本里写密码**：

```bash
cat > ~/.my.cnf <<'EOF'
[mysqldump]
user=backup
password=这里填真实密码
host=127.0.0.1
EOF
chmod 600 ~/.my.cnf

# 建议用只读备份账号，不要用 root：
# GRANT SELECT, LOCK TABLES, SHOW VIEW, EVENT, TRIGGER ON starwhisper.* TO 'backup'@'localhost';

# crontab：每天 03:17
17 3 * * * /opt/starwhisper/backup.sh >> /opt/starwhisper/backup/backup.log 2>&1
```

脚本会在备份后做三项校验：`gzip -t` 能解压、文件不是空库（< 10KB 直接判失败）、
按保留天数轮转。任一步失败都会**删掉半成品并返回非零**，避免"看起来有备份、实际不可用"。

## 部署时必读

### 1. 环境变量（不设会有安全/功能后果）

| 变量 | 说明 | 不设的后果 |
|---|---|---|
| `ADMIN_KEY` | 后台管理密钥，**32 位以上随机串** | `/api/admin/**` 全部 403（预期行为，但你会打不开后台） |
| `APP_DEV_LOGIN` | 免微信登录通道 | 默认 `false`。设 `true` 还必须同时配 `APP_DEV_LOGIN_TOKEN` 否则仍不放行 |
| `DB_USERNAME` / `DB_PASSWORD` | 业务库账号 | 默认值是业务账号 `starwhisper`，不要用 root |
| `DB_URL` | 数据库连接串 | 默认连本机 `starwhisper` 库 |
| `WECHAT_APPID` / `WECHAT_SECRET` | 小程序凭据 | 微信登录不可用（会明确报"微信登录未配置"） |
| `SHOWAPI_APPKEY` | 运势数据源 | 不调外部接口，全部走本地生成 |
| `KIMI_API_KEY` | AI 解读 | AI 解牌关闭，不影响普通抽牌 |

完整清单见 `src/main/resources/application.properties`（那里是唯一权威来源，别在这里再抄一份）。

### 2. 首次上线会跑 Flyway 迁移

`spring.flyway.enabled` 默认 `true`，启动时自动按顺序执行 `src/main/resources/db/migration/`。
**发版前先备份数据库**：

```bash
python deploy/ssh.py exec "mysqldump --defaults-extra-file=~/.my.cnf --single-transaction starwhisper | gzip > /opt/starwhisper/backup/pre-deploy.sql.gz"
```

注意 `V2` 会补索引（其中包含给 `app_user.nickname` 加唯一约束，会先给重名用户改名），
`V3` 会清掉旧的明文会话 —— **上线后所有老用户需要重新登录一次**，这是预期行为。

### 3. 工作目录不要指向源码目录

`application.properties` 里有 `spring.config.import=optional:file:./application-local.properties`，
它按**进程工作目录**加载。如果启动目录是源码目录，本地密钥文件会被带进生产。
systemd 里请显式设置 `WorkingDirectory=/opt/starwhisper`。

## 不要放进这个目录的东西

- ❌ `starwhisper-server.jar`（52MB，走 `put-dist.py` 上传，不进版本库）
- ❌ 数据库导出（含 openid / 昵称等用户数据）
- ❌ 从 GitHub API 拉下来的配置文件历史快照（里面是 base64 的明文密钥）
- ❌ 接口响应样例、测试日志

`.gitignore` 里已经把这些模式挡住了，但新增文件时还是请自己确认一遍。
