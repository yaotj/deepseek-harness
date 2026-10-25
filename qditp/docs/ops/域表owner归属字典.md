# 域表 owner 归属字典

> **本文件目的**：把「哪张业务表归哪个域 / 哪个模块」这件事收敛到一个可复核的来源。
> **唯一真源**：`AFCITPDB`（schema `QDITP`）的数据字典（`ALL_TABLES` / `ALL_TAB_COMMENTS` / `ALL_INDEXES` / `ALL_TAB_PARTITIONS`）。
> **回填状态**：下表「命名约定推导」列已据代码与既有文档核实；「实时归属」列 **待在集群内对 AFCITPDB 执行域归属查询后回填**（原脚本 `migration/tools/query-domain-tables.sql`（脚本已于 2026-10-26 合理性审计删除），须按同一口径重建：全量表清单 + 分区表 + RANGE 分区上限 + GLOBAL 索引识别），当前 **NEVER 当作已核实的 DB 事实**。

---

## 一、唯一真源与执行方式

1. 连接：`jdbc:oracle:thin:@//172.20.222.3:1521/AFCITPDB`，schema `QDITP`，口令 `qditp`（取自 Deployment env `other.sql.password`）。
2. **本机（办公网）TCP 被红岛防火墙拦截**，须在集群内可达网段或经堡垒机执行；真 DML 通道为 `oracledb` thin 直连，MCP `mcp_database_qd` 无只读保护、变更前 MUST 建 `BAK_<表>_<日期>` CTAS。
3. 实时归属查询脚本：原 `docs/ops/environments/scripts/migration/tools/query-domain-tables.sql`（脚本已于 2026-10-26 合理性审计删除）（含全量表清单、分区表、RANGE 分区上限、GLOBAL 索引识别）——需要回填时 MUST 按此口径对库重建查询。

---

## 二、命名前缀 → 业务域 约定（已据代码/文档核实）

| 表名前缀 | 业务域 | owner 模块（代码侧） | 备注 |
|---|---|---|---|
| `F2F_*` | 当面付 / 扫码取票（新表） | `face-pay-server` | 带 **GLOBAL 唯一索引**，`DROP PARTITION` 致其 `UNUSABLE`；新表由 sys_job 200/205/210 覆盖 |
| `TBL_TVM_APP_ORDER` / `TBL_TVM_ORDER_TOPUP` / `TBL_BOM_ORDER_PAY` | 旧 TVM/BOM 订单 | `collect-pay-server`（**已 2026-10-13 下线，0 pod**） | 旧表存量；collect-pay 的 4 个 `@Scheduled` 仅覆盖此三表，与 F2F_* 覆盖面互不重叠 |
| `PAY_*` | 支付 | `pay-sign-server` / `gate-txn-pay-server` | `PAY_TXN_DETAIL.PAY_TIME` 为 VARCHAR2，勿 `TO_CHAR` |
| `QRCODE_*` | 二维码 | `fep-app` / `face-pay-server` | `QRCODE_STATUS` / `QRCODE_TXN_DETAIL` 的 `CREATE_TIME/UPDATE_TIME` 2026-09-23 12:00 前 UTC、之后北京时间 |
| `DAILY_TICKET_*` | 日次票 | `daily-ticket-server` | `DAILY_TICKET_INSTANCE.COUNTING_*` 为 VARCHAR2，勿 `TO_CHAR` |
| `LOGIC_CARD_*` | 卡池 | `card-pool-server` | `LOGIC_CARD_POOL_CARD`；清库重测换号须 `UPDATE ... SET STATUS='AVAILABLE'`，勿 DELETE |
| `USER_ITP_REG_INFO` | 用户分流判定 | `route-sentinel` | 有记录=新 ITP，无记录/DB 故障 fail-safe 走旧 ITP |
| `GATEWAY_ROUTE_CONFIG*` | 分流路由配置/审计 | `route-sentinel` | 路由 URI 以库表 + Actuator `gatewayroutes` **实时值**为准 |
| `SYS_*` | RuoYi 平台 | `web-admin` | `sys_menu`（**无进程内缓存，增删即时生效**）/ `sys_job`（Quartz 内存 JobStore，改须重启）/ `sys_config`（**有 `LocalCache`，改库须控制台刷新或重启**） |
| `AGM_*` | AGM 设备 | `fep-dev-server` + `key-server` | `AGM_DEVICE_STATUS` 心跳异步落库（失败不得影响接口返回）；`COM_DEVICE_SYN_KEY` 仅 IF1A-02 密钥同步，由 `key-server` 写，与心跳无直接落库关系 |
| `STATION_*` / `TBL_STATION_INFO` | 站点参数 | `para-server` | `STATION_INFO` 覆盖不全；`TBL_STATION_INFO` 才是全的，取站名 MUST 带 `PARA_VER_NO = MAX(PARA_VER_NO)` |
| `TICKET_COLLECT_*` | APP 扫码取票 | `collect-ticket-server`（**未部署**） | `Ticket_Collect_Info` / `Ticket_Collect_Logs` / `Ticket_Collect_Log_Detail`；仓库无 DDL，`ORDER_NO` 是否唯一约束不可知 |

---

## 三、待集群执行回填的实时项（来自 P0 事实，非本机可核）

| 项 | 结论（待回填） | 来源 |
|---|---|---|
| 8 张 RANGE 分区表最大分区 | 只到 `P202612`；2027-01-01 起裁剪/归档失效，**须 2026-12-15 前处理** | 分区表清单（SQL #2/#3） |
| F2F_* GLOBAL 唯一索引 | DROP PARTITION 会致 `UNUSABLE` | GLOBAL 索引识别（SQL #4） |
| 各表 `NUM_ROWS` / `LAST_ANALYZED` | 运行期统计，须现查 | SQL #1/#5 |

---

## 四、使用纪律

- 任何「某域用哪张表」的断言，优先以本字典 + 对 AFCITPDB 的实时查询结果为准（原 `query-domain-tables.sql`（脚本已于 2026-10-26 合理性审计删除））；**NEVER 仅据前缀约定下结论**（前缀可能漂移、跨域共用表存在）。
- 码表（支付渠道 / 卡种 / F2F 渠道 / 发卡渠道）各码值空间 **MUST 独立**，互不合并。
- 改表结构 / 索引前 MUST 先执行 SQL 确认真实归属与分区形态，再走变更流程。
