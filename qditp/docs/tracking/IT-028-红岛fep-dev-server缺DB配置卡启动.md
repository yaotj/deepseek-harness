# IT-028 红岛 fep-dev-server 缺 DB 配置卡启动致入口 503

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-028` 一行。

## 1. 背景 / 现象
- 发现时间：2026-10-28
- 发现渠道（线上告警 / 测试 / 用户反馈 / 自查）：用户提问题自查（红岛入向入口探活）
- 影响范围：红岛 `itpb` fep-dev-server（闸机 AGM 入口），Kong `http://172.120.2.26:48000/fep-dev/` 与 `/itpagm/` 两个前缀全部 503
- 现象描述：同一 Kong 入口下 `/fep-app/actuator/health` 返回 200、`/fep-dev/actuator/health` 返回 503。503 响应体为 istio-envoy 的 `upstream connect error ... Connection refused`（非应用自身返回）。Pod 2/2 Running 已 47h，但容器内 `ss -ltn` 只有 envoy 端口（15021/15090/15000），**8080 无监听**；`kubectl logs` 尾部无 `Starting/Started/Tomcat` 行，Spring 未走完 refresh。

## 2. 根因分析
- 排查过程：
  1. Kong 48000 两前缀对比探活：fep-app 200 / fep-dev 503；
  2. 503 响应头 `server: istio-envoy` + 响应体 `delayed connect error: Connection refused` ⇒ 是 envoy 连不上应用容器端口，**不是应用返回的 503**（修正 `红岛-对外访问说明.md` 2026-10-26「后端应用自身返回」的旧判断——当日是直连 Pod 测的，今日实测为 envoy 连接被拒）；
  3. 容器内 `ss -ltn` 确认 8080 未监听；日志无 Tomcat 启动行；
  4. 日志实证：Druid `CreateConnectionThread` 持续报 `jdbc:oracle:thin:@127.0.0.1:1521/orcl` `Connection refused`（至 2026-10-28 16:08 仍在刷）；
  5. `kubectl get deploy` 对比 env：**fep-dev-server 完全没有数据库配置键**（无 `other.sql.host/username/password/database`，也无 `DB_HOST/DB_NAME/DB_USERNAME/DB_PASSWORD`）；对照组 ticket-server 有完整 `other.sql.*` 四键（值形如 `${DB_HOST:172.120.110.25:1521}`，MUST 现查）；
  6. fep-app env 同样没有 DB 键，但 fep-app 是纯 RPC 转发入口、不初始化数据源，故正常启动监听 8080。
- 根因结论：fep-dev-server 在红岛 `itpb` 的 Deployment env **缺失全部数据库配置**，数据源回落到构件内置默认 `127.0.0.1:1521/orcl`（Pod 内无监听 ⇒ 必 Connection refused，机制同 `deploy/k8s/10-deployments/key-server.yaml` 中 DB_HOST 注释），Druid 初始化反复失败 ⇒ Spring 卡在 refresh 之前 ⇒ 8080 永不监听 ⇒ envoy connection refused ⇒ Kong 503。**即使 IT-023 的红岛 DB 认证恢复，本问题也不会自愈**（它连的根本不是红岛库地址）。
- **为什么 fep-dev 有数据源而 fep-app 没有**（2026-10-28 补查）：
  - fep-dev-server 历史上是「纯转发、无 DB、不验签」前置（IT-012 §2 原话）；**2026-10-16 IT-012 为 AGM 设备心跳（IF1A-03 `/ci/agm/deviceHeartbeat`）异步落库需求，给它引入了 DB 能力**（pom 加 sql-datasource/mybatis/ojdbc、新增 `AgmDeviceStatusMapper` + `MERGE INTO AGM_DEVICE_STATUS`、测试环境 Deployment 注入 DB env），产出镜像 `fep-dev-server:2.0.71`（测试环境验证 `db=Oracle UP`）。红岛部署的正是这个带 DB 能力的 2.0.71（红岛 Deployment 镜像版本 MUST 现查），但红岛侧平台部署未带 DB env ⇒ 本问题。
  - fep-app 至今仍是无 DB 纯转发（红岛 fep-app 日志 0 条 Druid 报错实证），故无需 DB 配置也能正常启动。
- **⚠️ 连带发现与裁决**：当前仓库（git@qd）`fep-dev-server` 源码无 `AgmDeviceStatusMapper`、`deploy/k8s/10-deployments/fep-dev-server.yaml` 模板无 DB env 键，`git log -S "AgmDeviceStatusMapper"` 全分支无记录 ⇒ IT-012 的 DB 能力改动未入本仓；但 pom 的持久层依赖（sql-datasource/mybatis-adaptor/ojdbc8/orai18n）**在 HEAD 中仍在**（2026-10-28 核 `git show HEAD:qditp/fep-dev-server/pom.xml`），主类还残留一行未使用的 `import ...EnableDefaultMybatisAutoConfig`——即「无 DB 模块带持久层依赖」本身就是仓库现状，这正是默认数据源被初始化的直接原因。**2026-10-28 用户裁决：仓库现状（无 DB 版本）即正确态，NEVER 找回 IT-012 代码**——即 AGM 心跳落库需求已放弃，fep-dev-server 应回归「纯转发、无 DB、不验签」前置定位。由此红岛部署的 2.0.71（带 DB 能力）本身就是错误产物。

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A | 从当前仓库源码重建**无 DB 版本** fep-dev-server（bump 版本、走红岛 cloudos 构建路线，见 IT-024）部署红岛 | 与裁决的正确态一致；不触碰 DB 配置；同时根治 503 | 需走一遍红岛构建-发布链路 | 低；无 DB 版本不初始化数据源，启动不依赖 DB，IT-023 恢复与否都能起 |
| 方案B | 走 `PATCH /apps/{uuid}/advanced-config` 给现有 2.0.71 注入 `other.sql.*` 四键 | 不用重建镜像 | 维持一个「源码里没有的功能」在线运行，与裁决冲突；schema/账号仍需裁决 | ~~已随用户裁决作废~~ |

## 4. 决策与结论
- 选定方案：方案A（2026-10-28 用户裁决「现在就是正确的，不需要找回代码」后反转；原「注入 DB env」方向作废）
- 决策理由：仓库现状（无 DB）即正确态，线上产物应与源码真源对齐；注入 DB env 会让线上跑一个仓库里不存在的功能
- 影响范围 / 关联模块：fep-dev-server（红岛 itpb）；红岛构建走 `cloudos image build` 路线（`k8s` 域 NEVER 用于红岛，端点现状见 IT-024）；发布为生产操作，MUST 二次确认 + 升版本末位 +0.0.1、NEVER 同 tag 覆盖
- 关联回写：IT-012 状态与结论需标注「后已裁决放弃，功能回退」

## 5. 验证与回滚
- 验证方式 / 结果（**2026-10-28 已发布并验证通过**）：
  1. 仓库侧修复：pom 删除 sql-datasource/mybatis-adaptor/ojdbc8/orai18n 四个持久层依赖与版本属性、主类删残留 import；`mvn -o clean test-compile` BUILD SUCCESS；
  2. bump 2.0.71→**2.0.72** 出 jar → `itp-ops.sh buildx` 平台 buildx → `itpb/fep-dev-server:2.0.72`（digest `sha256:74024e62eefe1e46ef59ad71755091e4811185aa7ef0b5c9d6ddbf6f887bb6f0`，linux/amd64 已核；镜像较 2.0.71 小 12MB，与删依赖相符）；
  3. `advanced-config` PATCH 升级：version 2.0.71.7→2.0.71.8，version_id=hcr 镜像记录 uuid `zftnahc9`，env 19 项全量保留（含 VLOGS_URL），replicas=2 / 4C8G / app-logs PVC 挂载不变，release_id `3df005851cf44e29850b0af58d5306d0`；
  4. rollout 成功，新 Pod imageID = 推送 digest（硬证据一致）；两副本容器内 `curl 127.0.0.1:8080` 均应答（403=框架 FirstFilter 对根路径正常拦截）；日志 `Started FepDevServer in 5.436 seconds`（此前 47h 起不来）、log4j2-linux.xml 正常加载（日志盘 + VL 恢复）、**无 Druid 重试日志**；
  5. NodePort 31938 经 istio-envoy 探活：503 Connection refused → 403（`x-envoy-upstream-service-time` 有值=真实打到上游）；Kong `/fep-dev/`、`/itpagm/` 前缀同步恢复。
- 踩坑沉淀：buildx 的 tar 构建上下文已失效（一律 img.5），`itp-ops.sh buildx` 已改为直传 jar 文件（2026-10-28，详见 IT-024 §7）。
- 回滚预案：Harbor 旧 tag `itpb/fep-dev-server:2.0.71`（digest `sha256:880efba2…`）未删——回滚=再发 `advanced-config` PATCH 把 `container_image` 指回 `:2.0.71`、`version` 第 4 段继续递增（NEVER 复用已用值）；注意回退即回到带 DB 能力的错误产物，会重新卡启动，仅作临时手段。
- 已完成：`红岛-对外访问说明.md`「fep-dev 503 系后端应用自身返回」旧结论已修订为 envoy Connection refused（2026-10-28）。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-028`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述
- [x] 版本号 / 端口等数字均来自 2026-10-28 实时查询；ticket-server 对照值标注「MUST 现查」
- 偏差说明：生产发布已经用户二次确认后于 2026-10-28 执行完成；红岛镜像构建走 buildx 兜底（流水线未建成，IT-024 §4.1 裁决的流水线仍在推进）
