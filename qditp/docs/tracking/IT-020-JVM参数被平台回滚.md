# IT-020 红岛 JVM 堆参数未配置（默认 -Xmx500m），kubectl 注入的 env 被平台回收

> 对应 `docs/tracking/问题清单.md` 索引表 `IT-020` 一行。

## 1. 背景 / 现象
- 发现时间：2026-10-26
- 发现渠道：架构反馈「基础镜像已加 JVM 参数」，自查线上实际生效值
- 影响范围：红岛 `itpb` 21 个 Java 业务服务（不含 `web-admin`、`web-frontend`、`victoria`）
- 现象描述：
  - 容器规格经 2026-10-26 统一为 `requests=limits=4C/8G`，但容器内 JVM 实测启动参数为
    `-Xmx500m -Xms500m -XX:MaxDirectMemorySize=100m`（`account-server` 的 `/proc/<pid>/cmdline` 取证），
    即 8Gi 配额只用到 500MB 堆。
  - 按架构给定参数用 `kubectl patch` 注入 `max_memory=8000 / reserved_memory=2000 / max_direct_memory=100`
    与 downward API 的 `MY_POD_UUID` / `MY_POD_NAMESPACE` 后，试点 `blacklist-server` 实测变为
    `-Xmx5900m`；但数分钟内平台把 Deployment 模板恢复原样，Pod 被重建，env 全部消失（堆回到 500m）。

## 2. 根因分析
- 排查过程：
  1. 读基础镜像入口 `/startapp.sh`（`kubectl exec` 只读取证）确认参数语义：
     `java_max_memory = max_memory(默认800) - reserved_memory(默认200) - max_direct_memory(默认100)`，
     即三个 env 缺失时堆恒为 500m。
  2. 查平台侧写入口：`docs/reference/cloudos/CloudOS 5.0_PaaS_API_E7501.md` 中应用 env 只出现在
     「升级应用」`PATCH /apps/{uuid}/advanced-config`（必填 `version` / `version_id`）；
     `GET /apps/{uuid}/envs`、`/env`、`/advanced-config` 均无响应，平台无独立 env 写接口。
  3. ReplicaSet 历史留证：`blacklist-server-597554877c`（含 `max_memory=8000`）之后平台生成
     `blacklist-server-64b8f7755f`（无该 env），Deployment 的 `env` 列表回到原始 27 项（末尾仍是
     `SERVICE_NAME` / `NAMESPACE`）。
- 根因结论：
  - **基础镜像默认值面向小规格容器**，本项目未注入对应 env，堆参数停在 500m；
  - **平台是 Deployment 模板的真源**：应用 env 由平台的版本配置生成，kubectl 侧改动会被平台
    reconcile 覆盖（`automountServiceAccountToken` 因平台不管理该字段而得以保留，可作反证）。
- `advanced-config` PATCH 的隐藏契约（2026-10-26 实测，文档未标注，MUST 记住）：
  1. **`container_image` 是隐藏必填**——缺它一律返回 `application.5000010`（msg 为空，文档无此码），
     补上即成功；排查时先补这个字段，不要怀疑 env / resources；
  2. `version_id` 用 `upgrade_id`（32 位），不能用 `package_version_id`（镜像 URL，会 `value too long`）；
  3. `version` 不能等于当前值 → 第 4 段递增；
  4. `env` 是**全量替换**，必须回传原有全部 env 再追加；
  5. `resources` 里存的是**陈旧模板**（`replicas=1`、`1C/2C`），平台会按它重渲染 Deployment，
     照抄会把线上双实例 / 4C8G 一起回退——MUST 在同一次 PATCH 里一并修正。

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A 平台控制台/接口加 env | 在平台侧把 3 个静态 env 写进应用配置 | 平台真源一致，重部署/重启不丢 | 需逐应用操作 21 次；平台 env 多为静态 K/V，downward API 两项未必能表达 | 操作错会上线错配置（env 为全量替换语义） |
| B 镜像层 `ENV` 兜底 | `docker/Dockerfile` 增加 `ENV max_memory/reserved_memory/max_direct_memory` | 随镜像持久、平台不会覆盖；与「基础镜像参数化」设计一致 | 需重新构建 21 个镜像并发版；粒度粗（同一镜像共用一个值） | 与 Deployment env 重复时后者优先，易误判生效来源 |
| C 继续 `kubectl patch` | 每次平台回收后重打补丁 | 立即生效、零发版 | **已被证伪**：平台数分钟内回收，堆回落 500m | 静默回退，最危险 |

## 4. 决策与结论
- 选定方案：**平台侧 `advanced-config` PATCH**（改平台模型，不碰 kubectl），并在同一次 PATCH 里把陈旧的
  `resources` 模板一并修正为 2 实例 / 4C8G。
- 决策理由：C（kubectl）已证伪；B（镜像 ENV）需要重新构建 21 个镜像并发版，与「当前不发版」的前提冲突；
  A 变体立即生效、写入平台模型即持久，且顺手修掉模板里 `replicas=1` / `1C/2C` 的漂移。
- 落地结果：红岛 `itpb` **21 个 Java 服务**全部配置
  `max_memory=8000` / `reserved_memory=2000` / `max_direct_memory=100`（堆 5900m + direct 100m）。
  范围由用户裁决：不含 `web-admin`（admin）、`web-frontend`（nginx 无 JVM）、`victoria`（VictoriaLogs，非 Java）。
- 遗留（MUST 另行处理）：
  1. `MY_POD_UUID` / `MY_POD_NAMESPACE` 未落地——平台 env 只能存静态 K/V，无法表达 downward API
     （2026-10-26 用户裁决「尽力而为」）。`automountServiceAccountToken: false` 已单独生效并持久。
  2. ~~仓库模板未同步~~（2026-10-26 已补）：`deploy/k8s/10-deployments/*.yaml` 24 个 Java Deployment
     已按各自 memory limit 写入 3 个 env（口径：`max_memory` = limit 的 MB 千位取整、`reserved_memory` = 其 1/4、
     `max_direct_memory` = 100 → 2Gi 为 2000/500/100、4Gi 为 4000/1000/100，堆分别 1400m / 2900m），
     并补 `MY_POD_UUID` / `MY_POD_NAMESPACE` downward API 与 Pod 级 `automountServiceAccountToken: false`（含 web nginx）。
     `docker/Dockerfile` 只加契约注释、**不写 ENV**——同一镜像跨 2G/4G/8G 容器，写死会与 limit 脱节。
     红岛平台侧仍无法表达 downward API 两项，维持「尽力而为」裁决。

## 5. 验证与回滚
- 验证方式 / 结果（2026-10-26 逐个现查）：
  - 21 个应用 Deployment：`replicas=2`、`requests=limits=4C/8Gi`、`max_memory` 等 3 个 env 均在、每台 2 Pod ready；
  - JVM 实参（`jcmd <pid> VM.flags`）：`MaxHeapSize=6186598400`（=5900m）、`MaxDirectMemorySize=104857600`（=100m），
    抽查 `fep-app` / `recon-server` / `ticket-server` / `account-server` 四个一致；`blacklist-server` 另经
    `/proc/<pid>/cmdline` 看到 `-Xmx5900m -Xms5900m`；
  - `automountServiceAccountToken: false` 在 21 个上均保留（平台重渲染未冲掉该字段）；
  - 试点反向验证：kubectl patch 的 env 在数分钟内被平台回收（ReplicaSet 历史可见），证明 MUST 走平台侧。
- 回滚预案：对目标应用再发一次 `advanced-config` PATCH，`env` 去掉这 3 个键（`version` 继续第 4 段递增）；
  或平台「版本回滚」选上一版本。回滚后 JVM 回到基础镜像默认 500m 堆。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-020`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字已标注来源或「MUST 现查」（env 值、堆参数、副本数均取
      2026-10-26 现查值，后续 MUST 现查）
- 偏差说明：①首次按用户指示走 kubectl patch，被平台回收（已记入本文档，作为负结论保留）；
  ②`max_direct_memory` 用户原贴未给值，按默认 100 执行；③仓库模板与镜像层已于 2026-10-26 补齐（见 §4 遗留 2）。
