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

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A 平台控制台/接口加 env | 在平台侧把 3 个静态 env 写进应用配置 | 平台真源一致，重部署/重启不丢 | 需逐应用操作 21 次；平台 env 多为静态 K/V，downward API 两项未必能表达 | 操作错会上线错配置（env 为全量替换语义） |
| B 镜像层 `ENV` 兜底 | `docker/Dockerfile` 增加 `ENV max_memory/reserved_memory/max_direct_memory` | 随镜像持久、平台不会覆盖；与「基础镜像参数化」设计一致 | 需重新构建 21 个镜像并发版；粒度粗（同一镜像共用一个值） | 与 Deployment env 重复时后者优先，易误判生效来源 |
| C 继续 `kubectl patch` | 每次平台回收后重打补丁 | 立即生效、零发版 | **已被证伪**：平台数分钟内回收，堆回落 500m | 静默回退，最危险 |

## 4. 决策与结论
- 选定方案：**待裁决**（2026-10-26 用户先选「只改红岛线上 / kubectl patch」，实测被平台回收，
  需重新选择 A 或 B；B 更稳且无需平台侧逐应用操作）。
- 决策理由：C 已证伪；A 与 B 的取舍取决于「是否允许整体发版」与「平台是否支持 downward API env」。
- 当前已生效且持久的部分：21 个 Java 服务的 `automountServiceAccountToken: false`（平台不覆盖）。
- 影响范围 / 关联模块：红岛 `itpb` 全部 Java 业务服务；涉及发版流程（`qditp-deploy-release`）与部署模板
  `deploy/k8s/10-deployments/`（模板目前同样缺这三个 env）。

## 5. 验证与回滚
- 验证方式 / 结果：
  - 试点：`blacklist-server` patch 后 `/proc/<pid>/cmdline` 出现 `-Xmx5900m`，Pod 2/2 ready；
  - 反向验证：平台回收后新 ReplicaSet 无该 env，Deployment env 列表恢复 27 项；
  - 群集健康：回收后 `kubectl get pods -n itpb` 非 Running/未 ready 数 0。
- 回滚预案：`kubectl rollout undo deploy/<app> -n itpb`；或删除 5 个 env 与 `automountServiceAccountToken`
  字段（回到平台基线）。**注意平台会自行回到基线，手工回滚窗口很短。**

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-020`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字已标注来源或「MUST 现查」（镜像 tag、副本数、env 值均标注
      取证时间 2026-10-26，后续 MUST 现查）
- 偏差说明：本次为用户指定「只改线上」，未同步 `deploy/k8s/10-deployments/` 模板；模板缺陷已记入
  「影响范围」，待方案确定后一并回写。
