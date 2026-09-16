# ITP 与 ACC 交互测试 · INDEX

原始资料：用户提供的《ITP与ACC交互测试案例》（3 例，ACLC_ACC_Test_001~003）。
处理方式同 [用户卡管理](../user-card/INDEX.md)：保留用例编号，预期结果改为**当前代码可验证的断言**，冲突处标 ⚠️ 并汇总到 `03`。

最后更新：2026-09-09 ｜ 依据代码：`main` @ `8e8b7bd` + SVN r420 合并后的工作副本（para-server `2.0.18`、card-pool-server `1.0.14`、web-admin `1.1.15`）

## 页面

- [00-链路事实与配置字典](00-链路事实与配置字典.md) — 参数文件格式、`TBL_PARA_VERSION`、FTP 配置键、卡池表
- [01-参数管理](01-参数管理.md) — Test_001 参数下载、Test_002 参数自动同步
- [02-逻辑卡号获取](02-逻辑卡号获取.md) — Test_003
- [03-阻塞项与缺陷候选](03-阻塞项与缺陷候选.md) — 冲突汇总

## 用例可执行性一览

- Test_001 参数下载：☑ **通过**（2026-09-09）— FTP 拉取（`POST /para/import/ftp`）与目录导入均已实测；定时触发部分并入 Test_002 的 T002-8；⚠️ 遗留 `0002`/`0003` 不走 FTP（待业务确认）
- Test_002 参数自动同步：☑ **主链路通过**（2026-09-09）— 已过 T002-1 幂等、T002-2 Quartz `0000` 分支、T002-3 新版本入库、T002-3b MD5 判据、T002-8 `sys_job` 定时触发；☐ 未执行：`9999` 分支、T002-4~T002-7；⚠️ 「回滚至上一版本」与「同步日志完整」仍无实现（A3 / A4）
- Test_003 逻辑卡号获取：⚠️ **手册原链路已废弃（ESLOGIC FTP 下发 → acc-es-server 解析 → 导入卡池 从未落地），方案整体改为 `card-pool-server` 新模块**（IF7B-01 直连 ACC 申请批次 + FTP 下载卡号文件 + 入库卡池），详见 `docs/business/card-pool.md`。
  **2026-09-09 追加：`card-pool-server` 是另一条已落地的链路**，其定时维护与链路追踪已补 5 条用例：T003-4 定时触发（☑ 通过）、T003-5 异步线程 traceId 一致（☑ 通过）、T003-6 受理式限流 / T003-7 tracing 关闭降级 / T003-8 失败分支 / T003-9 多副本（☐ 未执行）

## 一句话结论

手册假设的链路是「ACC 发布 → FTP → ITP 定时拉取 → 自动导入 → 热加载」。
**2026-09-08 起这条链路已全部落地**：`ParaFtpScanService` 拉 FTP、web-admin Quartz 定时触发、`ParaFileImportService` 解析入库、下游按 `CURRENT_VER_NO` 子查询自然读到新版本。
**2026-09-09 起 `sys_job` 记录已创建、定时链路实测跑通**，Test_001 / Test_002 主链路判定通过。
剩下的差距不在链路本身，而在两处：**「回滚至上一版本」无实现**（只有事务级不生效）、**无导入流水表**（定时场景下明细完全丢失）；另有 T002-4~T002-7 四条异常分支用例尚未执行。

## 编写约束

- 只记录代码中确实存在的事实，均带 `文件:行号`；找不到写「未找到」，不推断。
- 矛盾显式标 ⚠️，不擅自裁决，收敛到 `03`。
- 行号绑定 `8e8b7bd`；参数域（`01`）的行号已按 SVN r420 合并后的工作副本重新核对，代码变更后需重新核对。

## 更新记录

- 2026-09-07 首次建立。来源：用户提供的测试案例 + 仓库代码实测（para-server / acc-es-server / account-server / acc-secure-server）。
  主要发现：参数下载无 FTP 无定时（Test_001）、无版本回滚与导入日志表（Test_002）、逻辑卡号链路完全缺失且卡池卡号本地伪造（Test_003）、`account.card-pool.debug-manual-allocate=true` 调试开关默认开启。
- 2026-09-08 补充 Test_002 执行用例并重写 `01` 与 `03` A2/A3。
  变更原因：FTP 拉取 + web-admin Quartz 定时任务已实现（SVN r420 骨架 + 三处修正），`para-server:2.0.17` 部署到 `172.20.211.23:30026` 实测通过。
  新增 7 个可执行用例 T002-1~T002-7（其中 T002-1 幂等、T002-2 Quartz 契约已实测通过）。
  新增阻塞项：A2b `/para/import/**` 全部无鉴权、A3b `total=0` 两种含义无法从响应区分。
  ⚠️ 环境属性冲突未裁决：`172.20.211.23` 网段在 `AGENTS.md` §8 记为生产 NodePort，用户口头称测试环境。
- 2026-09-08（同日稍后）**入库判据由「仅版本号」改为「版本号 + MD5」**，`para-server:2.0.18` 已部署实测。
  变更原因：T002-3 发现 `0001` 同版本号存在两份不同内容的文件，旧判据下永远无法收敛（A3c）。
  代码改动：`ParaFileReadUtils.md5Hex` 新增、`ParaFileImportService.importLocalFile` 三分支判据、`ParaFtpScanService.findCandidates` 放宽为等版本也下载。
  新增用例 T002-3b（同版本换内容触发导入，已实测通过）；T002-1 / T002-2 响应形态随之变化并已重测。
  闭合：A3b（`total=0` 歧义已消除）、A3c（判据侧已修复，ACC 侧成因待确认）。
  ⚠️ **回归提醒**：旧脚本断言 `total=0` 的地方需改成 `imported=0 && skipped=total`。
  环境属性冲突已由用户裁决为**测试环境**（`AGENTS.md` §8 已更新）。
- 2026-09-09 **回填 Test_001 / Test_002 测试结论**。变更原因：用户确认两条用例已测试通过。
  Test_001 判定通过（人工 FTP 拉取 + 目录导入），其步骤 1「定时检查」的验证合并到 Test_002。
  新增用例 **T002-8 `sys_job` 定时触发**（☑ 通过），前置条件第 4 项「`sys_job` 未创建」随之闭合。
  Test_002 判定为**主链路通过**（T002-1 / T002-2 `0000` 分支 / T002-3 / T002-3b / T002-8 共 5 条），T002-2 的 `9999` 分支与 T002-4~T002-7 仍为 ☐ 未执行，按「先记录测试通过的部分」处理。
  ⚠️ **待补录**：T002-8 的 job id、cron 表达式、`SYS_JOB_LOG` 实际执行时间本次未记录。
  ⚠️ **两处文字未同步**（按 README 约定结果回填不改写链路事实段落，留待下次代码/环境核对）：`01-参数管理.md` 链路事实段的「`sys_job` 记录尚未创建」、`03-阻塞项与缺陷候选.md` A2 的同一表述。
- 2026-09-09（同日稍后）**补充 card-pool-server 定时维护与链路追踪用例**（`02` 新增「事实基线变更」段 + T003-4~T003-9）。
  变更原因：`card-pool-server` 的 5 分钟卡池维护定时任务与 trace 链路本日上线并实测。
  链路：web-admin Quartz（`sys_job` job_id=107，cron `0 0/5 * * * ?`）→ `CardPoolClient.runMaintenance` → `POST /internal/card-pools/maintenance`（受理式）→ 平台线程池 `card-pool-maintenance` 异步执行。
  已通过：T003-4 定时准点触发、T003-5 异步线程 traceId 与调度端一致（定时路径 `f4638949…`；手工注入 traceparent 路径 `1111222233334444aaaabbbbccccdddd`）。
  未执行：T003-6 受理式限流、T003-7 tracing 关闭降级、T003-8 失败分支、T003-9 多副本。
  ⚠️ **回归提醒**：`itp/card-pool-server:1.0.13` 曾把异步交接改成「只拷 MDC」（pay-sign 口径），**导致 T003-5 失败**；1.0.14 回退为建子 span。改动 `CardPoolServiceImpl.withTraceContext` 后必须重跑 T003-5。
  ⚠️ **自动化缺口**：`web-server/web-admin` 无 `src/test` 也无测试依赖，`CardPoolQuartzTask.invokeOnce` 的三个分支（null / `!isSuccess` / `accepted=false`）目前**只能手工验**；要补单测需先给该模块加 `spring-boot-starter-test`（scope=test），属独立改动，未擅自引入。
  上文 Test_003 的原有取证（`ESLOGIC` 零命中、`account-server` 本地伪造卡号）**未推翻**，两条链路不同。
