# Kimi AI 解牌

双端在抽牌接口返回 `drawId` 后启用 AI。游客可调用 `POST /api/tarot/ai-reading`，JSON 为 `{"drawId":"抽牌返回值","question":"选填，最多300字"}`。只读服务器暂存的牌阵，不接受客户端改写牌面。返回 `summary` / `detail` 纯文本。每组牌生成一次，成功后重试复用结果（不更换问题）。

## 配置与部署

仅在服务器环境变量设置 `KIMI_API_KEY`，或在未跟踪的 `application-local.properties` 设置 `kimi.api-key`。不要将密钥放到前端、Git 或日志。默认模型 `kimi-k2.6`，可通过 `KIMI_MODEL` 覆盖，使用非思考模式。密钥为空时抽牌仍可用，`drawId` 为空。

默认同一来源 IP 每天最多 3 次，全站每天 100 次，最多 2 个并发。通过 `KIMI_PER_IP_DAILY_LIMIT` / `KIMI_GLOBAL_DAILY_LIMIT` 调整，0 可暂停新请求。配额按上海日期计算，调用尝试（包含超时、失败）也计数，以限制实际成本。成功缓存命中不计数。服务重启清空配额和缓存；当前适合单实例，扩容前必须改用共享持久限流存储。

抽牌缓存最多 2000 组，2 小时失效；容量满时淘汰最老的非执行中牌阵。`drawId` 是不公开的随机访问凭证，请勿分享。进程重启后旧牌阵需要重新抽取。

调用地址固定为 `https://api.moonshot.cn/v1/chat/completions`。连接超时 5 秒、读取超时 45 秒；客户端等待 60 秒。网关 `/api/tarot/ai-reading` 读取超时建议 65 秒以上。部署时先更新后端，再发布 PC 构建和小程序。

限流使用 `request.getRemoteAddr()`，不直接信任客户端的 X-Forwarded-For。若使用 Nginx，部署方必须按实际网络配置受信代理和容器 RemoteIpValve，并阻止外部直连后端；否则所有访客会共享代理 IP 的 3 次配额。不要无条件信任任意 forwarded header。公共游客接口还应在网关设置请求速率限制。

问题仅在主动点击时随牌面发送给 Kimi，不存数据库或应用日志。页面提供发送说明。上线后可将已在聊天中提供的密钥轮换为独立项目密钥，在平台设置消费限额。

## 验证

`mvn -o '-Dtest=TarotControllerTests,TarotAiServiceTests,KimiTarotClientTests' test` 不连接数据库、不调用收费接口。实际密钥已用合成数据完成官方接口连通性测试；生产部署仍须验证代理来源 IP 与配额。

当前服务器：`root@60.205.228.96`。后端产物替换 `/opt/starwhisper/starwhisper-server.jar`，PC 构建发布到 `/opt/starwhisper/web/`。先备份旧产物；保留 `/opt/starwhisper/app.env` 的所有现有配置，只增补所需的 Kimi 环境变量，不用本地文件整体覆盖它。通过 `systemctl restart starwhisper` 重启后端，前端发布后先 `nginx -t`，成功后 `nginx -s reload`。部署需要实际 SSH 凭据，源代码推送并不等于生产上线。
