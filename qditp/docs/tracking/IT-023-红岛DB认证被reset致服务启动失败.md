# IT-023 红岛 hditpdb 认证阶段被 reset，itpb 全部 Java 服务启动失败

> 对应 `docs/tracking/问题清单.md` 索引表 `IT-023` 一行。

## 1. 背景 / 现象
- 发现时间：2026-10-26 12:40 前后
- 发现渠道：为 `web-admin` 补 JVM 参数后核验启动，发现端口不监听
- 影响范围：红岛 `itpb` 全部 22 个 Java 服务（21 个业务服务 + `web-admin`）
- 现象描述：
  - 所有 Java 服务日志均有 `ERROR org.springframework.boot.SpringApplication - Application run failed`，
    失败时间 = Pod 启动后约 19s（如 `account-server` Pod 12:13:34 启动 → 12:13:53 失败）；
  - 容器状态 `Running`、`restartCount=0`、无 `OOMKilled`（Spring 上下文关闭后 JVM 因非守护线程仍在，
    容器不退出，形成「Running 但业务未启动」的僵尸态）；
  - 容器内 8080 无 LISTEN（`/proc/net/tcp` 中无 `:1F90`），入向必然 503；
  - 根异常：`org.springframework.beans.factory.UnsatisfiedDependencyException` →
    `java.io.IOException: Connection reset, Authentication lapse 0 ms`。

## 2. 根因分析
- 排查过程：
  1. 排除本次改动：容器无 OOMKilled、堆参数正常（`jcmd VM.flags` 见 `-XX:MaxHeapSize=6186598400`）；
  2. 在 Pod 内用 `/dev/tcp` 测 `172.120.110.25:1521` → **TCP 可连**（不是网络不通）；
  3. 用 Pod 内 JDK 打独立 JDBC 探针（`jar xf /app.jar BOOT-INF/lib/ojdbc8-19.18.0.0.jar` + `jshell`），
     **不经过应用代码、不经过平台 env 解析**，结果：
     ```
     CONNECT_FAIL :: SQLRecoverableException :: IO 错误: Connection reset, Authentication lapse 0 ms.
     ```
  4. 换 5 个 service 名（`hditpdb` / `HDITPDB` / `HDTPDB` / `itpdb` / `ORCLPDB1`）全部同样 reset。
- 根因结论：**故障在数据库/认证链路，不在应用侧**。判据：
  - 若密码错，应为 `ORA-01017`；若 service 名错，应为 `ORA-12514`——两者都是规范的 Oracle 错误；
  - 实际是认证阶段被 **RST（连接重置）**，说明监听接受 TCP 后无法完成到实例/PDB 的会话建立，
    可能成因：PDB 未 open / 监听异常 / 实例重启中 / 迁移后地址或 service 变更 / 中间设备重置长连接。
  - 需 DBA 在 `172.120.110.25` 上核 `lsnrctl status`、`select name, open_mode from v$pdbs`、
    `select count(*) from v$session`（会话上限）。
- **与 IT-020 的关系**：本次 JVM 参数变更触发了 22 个应用的滚动重启，重启使「启动期连库」这一步
  反复执行并失败，因此**暴露**了该故障；独立探针证明与 JVM 参数、规格、副本数无关。

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A 等 DBA 修复 DB 后统一滚动重启 | 不动配置，先修根因 | 唯一治本；不会重复无效重启 | 依赖外部排期，期间服务不可用 | 低 |
| B 立即再重启一轮 | 赌 DB 已恢复 | 若已恢复可最快拉起 | 若未恢复只是重复失败，日志噪音，浪费时间 | 中 |
| C 回退本次 JVM 参数 | 怀疑堆参数导致 | — | **已证伪**：独立 JDBC 探针不经应用配置同样失败 | 高（掩盖真因） |

## 4. 决策与结论
- 选定方案：**A**（等 DB 侧确认后统一重启）。已完成的事实固定：JVM 参数、规格、副本配置本身正确且已持久化到平台模型。
- 决策理由：证据指向 DB 认证链路，重启与回退都无法改变结果；再重启只会产生重复失败日志。
- 待外部确认：①DB 是否在迁移/重启窗口；②`172.120.110.25:1521` 是否仍是迁移后的正确地址；
  ③PDB `hditpdb`/`HDTPDB` 当前 open 状态与会话上限。

## 5. 验证与回滚
- 验证方式（DB 恢复后用这条一键判定，不依赖应用）：
  在任一 Java Pod 内跑独立 JDBC 探针，出现 `CONNECT_OK` 即说明库恢复；
  随后对 22 个应用执行 `kubectl rollout restart deploy/<app> -n itpb`，并以「`Started ... Application in`
  + 8080 有 LISTEN」为启动成功判据（**不要只用 Pod Ready 判定**：这些应用无 readinessProbe，
  Spring 启动失败时 Pod 仍显示 2/2 Running）。
- 回滚预案：本单不涉及配置回滚（IT-020 的 JVM 参数保留）；若需回到 500m 堆，按 IT-020 §5 回滚。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-023`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述
- [x] 本文档内的地址 / 端口 / 参数均标注 2026-10-26 现查来源，后续 MUST 现查
- 偏差说明：故障在本次会话内由「补配 JVM 参数」触发重启而暴露，非本会话配置变更所致；
  未联系 DBA（当前仅本机/跳板视角，需业主方推动）。
