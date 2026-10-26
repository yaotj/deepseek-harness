# IT-017 红岛 `web-admin` 部署 2 副本，会话层为进程内缓存，登录报「验证码已失效」

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-017` 一行。

## 1. 背景 / 现象

- 发现时间：2026-10-23 15:00
- 发现渠道：用户反馈（红岛生产，`http://172.120.2.28:8081/login?redirect=/index`）
- 影响范围：红岛 `itpb` 命名空间综管台（`web-admin` + `web-frontend`），**全部使用者无法登录**
- 现象描述：打开登录页正常，输入账号密码后提交，稳定报「验证码已失效」；偶尔能过验证码，但登录成功后页面立刻弹回登录页。

## 2. 根因分析

- **复现步骤**（脚本化，10 次循环「取验证码 → 立即用同一 uuid 提交错误码」）：

```bash
# 关键点：同一个 uuid 必须紧跟其后提交，中间不插入任何请求
curl -s .../web-server/captchaImage   # 取 uuid
curl -s -X POST .../web-server/login -d '{"username":"...","password":"...","code":"zzzz","uuid":"<上一步 uuid>"}'
```

| 阶段 | 10 次结果 | 判读 |
|---|---|---|
| `web-admin` 2 副本 | 失效 7 / 验证码错误 3 | 随机分布 ⇒ 请求在两个 Pod 间轮转 |
| `web-admin` 1 副本 | 失效 0 / 验证码错误 10 | 每次都命中同一份缓存 |

- **根因结论**：`web-admin` 不使用 Redis（项目纪律见 `AGENTS.md` §4.1），会话统一走 `LocalCache`
  （`web-common/src/main/java/com/chinasofti/huateng/common/core/cache/LocalCache.java`，类注释原文
  **“JVM local cache for single-instance deployments”**，底层 `ConcurrentHashMap`）。两处受影响的键：

| 键 | 写入点 | 读取点 | 2 副本下的表现 |
|---|---|---|---|
| `captcha_codes:<uuid>` | 验证码接口 | `SysLoginService.validateCaptcha` | 取码落 Pod A、提交落 Pod B ⇒ **验证码已失效** |
| `login_tokens:<uuid>` | `TokenService`（`setCacheObject`） | `TokenService.getLoginUser` | JWT 里的 uuid 在，但 `LoginUser` 只在 Pod A 内存 ⇒ 落 Pod B **401**、前端弹回登录页 |

- **放大因素**：平台默认把 `web-admin` 部署成 `2` 副本，而代码的设计前提是单实例 —— 两者矛盾未被发现，直到 8081 入口打通、第一次有人真正登录才暴露。
- **排除项**：
  - 不是验证码开关问题（`sys.account.captchaEnabled=true` 时才会校验，关掉只绕过第一层，登录态照样跨 Pod 丢）；
  - 不是网关路由问题（`manage-vr` 的 `/web-server/` 路由实测返回 403/200，已到达后端）；
  - 不是时钟/过期时间问题（失效是「查不到」而非「已过期」，`getCacheObject` 返回 `null` 直接抛 `CaptchaExpireException`）。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A（选定） | `web-admin` 副本数固定为 1 | 零代码改动，符合 `LocalCache` 的设计前提；一条命令即时生效 | 综管台无多副本高可用；发布时秒级中断 | 低（内部管理后台，可接受；平台重新部署需复查副本数） |
| 方案B | 保留多副本，`LocalCache` 外部化 | 真正支持多副本 | 项目纪律 NEVER 引入 Redis ⇒ 只能落 Oracle 表或改 JWT 自包含，工作量大、涉及登录态序列化与安全复核 | 中高，需业主裁决 |
| 方案C | Istio `DestinationRule` 会话保持（`consistentHash`） | 不动副本数 | 验证码请求发生在「拿到 token」之前，此时既无 cookie 也无稳定 header；`useSourceIp` 又因网关转发后源 IP 相同会把全部用户黏到同一 Pod（等于假多副本） | 不解决问题 |

## 4. 决策与结论

- **选定方案**：方案A —— `web-admin` 单副本。
- **决策理由**：`LocalCache` 的存在本身就宣告了「单实例」这一设计前提；方案B 是为一个内部管理后台的登录问题引入架构改造，性价比低；方案C 在技术上不成立。
- **执行方式**：走云管台 API（**MUST** 走平台，NEVER 只用 `kubectl scale`——`kubectl` 改的是集群事实、平台 DB 里仍是旧值，下次平台操作会把副本数改回去）：

```bash
# 2026-10-25 重构后：旧 cloudos-hongdao.sh 已并入 itp-ops.sh（单一入口）
cd docs/ops/environments/scripts
./itp-ops.sh cloudos scale web-admin 1 --yes
```

- **影响范围 / 关联模块**：仅 `web-admin`。`web-frontend`（nginx，静态资源）无状态，保持 2 副本不受影响。
- **连带修复**：`docs/ops/environments/scripts/itp-ops.sh cloudos`（原 `cloudos-hongdao.sh`，已并入）的 `app_scale` 原按官方文档发 `{"replicas":N}`，平台实际返回 500
  （`Cannot deserialize ArrayList<McpPlaintext> from Object`）；实测正确写法为 **body `[]` + query `?replicas=N`**，已改并回写 `docs/ops/environments/scripts/README.md`。

## 5. 验证与回滚

- **验证结果**（2026-10-23 15:1x，已执行）：
  1. `kubectl get deploy web-admin -n itpb` → `1/1`，旧 Pod 已 `Terminating` 完毕；
  2. 同一组 10 次「取码 → 提交」→ **验证码已失效 0 次**（修复前 7 次）；
  3. 云管台侧 `./itp-ops.sh cloudos scale web-admin 1` 再跑一遍 → 「已是 1 实例，跳过」（幂等成立）。
- **端到端待办**：需人工用真实账号完成一次浏览器登录 + 菜单切换 + 任一列表页查询，确认不再弹回登录页（脚本侧无有效口令，未代跑）。
- **回滚预案**：`./itp-ops.sh cloudos scale web-admin 2 --yes`。**回滚即复现故障**，仅在需要多副本做其它验证时短时使用。
- **复发防控**：平台重新部署 `web-admin` 后副本数可能被重置为 2，上线核验 MUST 检查 READY 为 `1/1`。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-017`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的副本数 / 行号等数字已标注为 2026-10-23 实测快照，并给出「MUST 现查」口径
