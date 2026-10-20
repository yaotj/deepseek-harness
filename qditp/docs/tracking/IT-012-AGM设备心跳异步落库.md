# IT-012 AGM 设备心跳异步落库

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-012` 一行。

## 1. 背景 / 现象
- 发现时间：2026-10-16
- 发现渠道：用户主动提出
- 影响范围：`fep-dev-server`（AGM 闸机前置）
- 现象描述：`fep-dev-server` 的 IF1A-03 AGM 设备心跳接口（`/ci/agm/deviceHeartbeat`、`/ci/agm/notiDeviceHeard`）当前仅打印日志并返回成功，**不落库**。用户要求单独建表做异步持久化，且即使落库失败也不得影响接口返回成功，行为须与当前一致。

## 2. 根因分析
- `fep-dev-server` 作为「纯转发、无 DB、不验签」的前置模块，pom 中未引入 `sql-datasource` / `mybatis-adaptor` / `ojdbc`，Deployment 也未注入 DB 环境变量，因此历史上无数据库访问能力。
- AGM 心跳与 F2F 设备心跳（IF2A-04，由 `face-pay-server` / `collect-pay-server` 写入 `F2F_DEVICE_STATUS`）是不同接口、不同落点，**不能复用** `F2F_DEVICE_STATUS`。
- `COM_DEVICE_SYN_KEY` 仅用于 IF1A-02 AGM 密钥同步（由 `key-server` 写入），与 AGM 心跳无直接落库关系。

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A | 直接写入 `COM_DEVICE_SYN_KEY` | 复用现有表 | 语义混淆（密钥同步 vs 心跳）、字段不匹配、无法记录累计心跳次数与在线态 | 高 |
| 方案B | 单独建 `AGM_DEVICE_STATUS`，同步落库 | 结构清晰 | 同步写库异常会拖慢/中断接口响应，违背「失败不影响返回」要求 | 高 |
| 方案C | 单独建 `AGM_DEVICE_STATUS`，异步落库，异常吞掉 | 行为与当前一致，失败隔离 | 需要为 `fep-dev-server` 引入 DB 能力与异步线程池 | 低 |

## 4. 决策与结论
- 选定方案：方案C
- 决策理由：
  1. 单独建表避免与密钥同步表语义混淆；
  2. 异步写入保证调用方响应立即返回，数据库异常不抛回接口层；
  3. 与 F2F 设备心跳表结构对齐，便于后续统一监控。
- 影响范围 / 关联模块：`fep-dev-server`（pom、配置、代码、Deployment yaml）、AFCITPDB（新增 `AGM_DEVICE_STATUS` 表）。

## 5. 验证与回滚
- 验证方式 / 结果：
  1. `mvn -o clean test-compile` BUILD SUCCESS；
  2. 本地启动 `FepDevServer`，Druid 数据源初始化成功，`AgmDeviceStatusMapper` 加载无异常；
  3. 临时 Mapper 集成测试验证 `MERGE INTO` upsert 语义：首次插入 `HEARTBEAT_COUNT=1`，再次更新 `HEARTBEAT_COUNT=2`，`ONLINE_FLAG='1'`；
  4. 镜像构建推送成功：`os-harbor-svc.default.svc.cloudos:443/itp/fep-dev-server:2.0.71`，digest `sha256:787ec79d47e3aeb216bf7bab67a0c311c71ee54ea6cf087e0e0d318d91866644`；
  5. **K8s 部署与端到端验证（2026-10-16）**：
     - 应用 `deploy/k8s/10-deployments/fep-dev-server.yaml` 创建 Deployment；
     - 因测试环境 Harbor 暂不可达（Bad Gateway），使用本地构建的同名镜像在 orbstack 节点运行；
     - Pod `fep-dev-server-84ccdb8449-dr9sf` 启动成功，`/actuator/health` 返回 `UP`，`db=Oracle UP`；
     - 在 Pod 内执行 `POST /ci/agm/deviceHeartbeat?deviceId=01010001` → `{"retCode":"0000","retMsg":"成功"}`；
     - 数据库 `AGM_DEVICE_STATUS` 首次写入 `HEARTBEAT_COUNT=1`、`STATION_CODE='0101'`；再次请求后 `HEARTBEAT_COUNT=2`，确认 MERGE INTO upsert 生效；
     - 测试数据已清理。
- 回滚预案：
  - 代码回滚：`git/svn` 回退 `fep-dev-server` 相关改动；
  - 部署回滚：`kubectl set image deployment/fep-dev-server ...` 回退到上一版本镜像；
  - 数据回滚：新表 `AGM_DEVICE_STATUS` 无历史数据依赖，可直接 `DROP TABLE`（上线前已确认不存在同名表）。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-012`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 处理情况 / 更新」
- 偏差说明（如有）：无
