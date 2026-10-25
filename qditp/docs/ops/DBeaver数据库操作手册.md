# DBeaver 连接 qditp 数据库操作手册（Windows PC）

> **v2.0 · 2026-10-17 改版**：基于《红岛 Oracle RAC 19c 部署方案》（v1.1，2026-10-13，胡辰；本机文件 `~/workspace/company/chinasofti/红岛部署19c集群.docx`，微信转储）重写。
> **主场景改为红岛生产库 HDTPDB**（Oracle RAC 19c，CDB+PDB 架构）；测试库 `AFCITPDB` 降为 §2.3 对照。
> 地址 / 口令 / 端口 **MUST 现查**；源文档换版后 MUST 同步更新本手册 §2.1，NEVER 追加补丁句。

## 一、适用与前提

| 前提 | 说明 |
|---|---|
| 网络 | PC 到数据库集群 VIP `1521` 可达（红岛生产网段路由 / 防火墙策略已放通），先做 §5 预检 |
| 账号 | 有库账号口令（§2.1） |
| 客户端 | DBeaver 任意较新版本（24.x / 25.x）；**Windows 上不需要装 Oracle Instant Client**，DBeaver 内置 Oracle thin 驱动（ojdbc） |
| 认知 | DBeaver 直连**没有任何保护**；生产库另有数据库审计 / 堡垒机，操作全程留痕，MUST 遵守 §7 安全红线 |

## 二、连接参数

### 2.1 红岛生产库 HDTPDB（ITP 业务库，主场景）

> 连接参数已迁至 [`environments/红岛.md`](../environments/红岛.md)「生产库 HDTPDB」节，本手册只保留 DBeaver 操作细节。
### 2.3 测试库 AFCITPDB（对照，NEVER 与生产混用）

| 项 | 值 |
|---|---|
| 地址 | `172.20.222.3:1521/AFCITPDB`（测试网段 `172.20.x.x`） |
| 用户 / 口令 | `qditp` / `qditp`（同 K8s Deployment env `other.sql.password`） |
| JDBC URL | `jdbc:oracle:thin:@172.20.222.3:1521/AFCITPDB` |

**NEVER 拿生产口令连测试、也 NEVER 拿测试口令连生产**——两套环境网段（`172.120.x.x` vs `172.20.x.x`）、账号体系、审计策略完全独立；生产连错一次即留审计记录。

## 三、安装与驱动

1. 官网 `dbeaver.io` 下载 Windows 版（社区版即可）。
2. DBeaver 内置 Oracle 驱动；首次建 Oracle 连接若提示下载驱动，点 Download（PC 需能访问外网或已有驱动缓存）。
3. 无外网的 PC：从有网机器把 `%APPDATA%\DBeaverData\drivers\` 下 Oracle 驱动目录拷到本机同路径，或用便携版整体拷贝。
4. 内置驱动过老时：下载新版 `ojdbc*.jar`（11g+ thin 均可连 19c），在连接设置的「Driver」里手动指定。

## 四、新建连接（红岛生产示例）

### 4.1 步骤

1. 「数据库 → 新建数据库连接」→ 选 **Oracle** → 内置 **Oracle** 驱动（非 legacy）。
2. 连接方式选 **Basic**：
   - 主机：`172.120.110.25`
   - 端口：`1521`
   - 数据库：`hditpdb`，单选按钮确认是 **Service name**（§2.2）
3. 认证：生产库用 4 个分域账号之一（`QDITP_USER` / `QDITP_TXN` / `QDITP_RIDE` / `QDITP_PLAT`，各有独立表空间与临时表空间，按要访问的域选）。
   ⚠️ **口令 MUST 找 DBA 现取，NEVER 写进任何文档/脚本/群聊**（2026-10-26：本手册原内联了 4 个明文口令，
   已随提交进入 git 历史并推送远端；字面量已移除，但**旧口令 MUST 视为已泄露并轮换**）。
   测试库对照才用 `qditp`（`AFCITPDB`，`qditp`/`qditp`，同 K8s Deployment env `other.sql.password`）。
   共享电脑 NEVER 保存口令。
4. **Test Connection**：成功 → Finish；失败 → §六 对错号。

### 4.2 URL / TNS 备选（Basic 填不进去时）

- URL 方式（连接设置直接填 JDBC URL）：`jdbc:oracle:thin:@//172.120.110.25:1521/hditpdb`
- TNS 方式：粘贴连接描述符

```text
HDTPDB =
  (DESCRIPTION =
    (ADDRESS = (PROTOCOL = TCP)(HOST = 172.120.110.25)(PORT = 1521))
    (CONNECT_DATA = (SERVICE_NAME = hditpdb))
  )
```

### 4.3 ZHS16GBK 字符集下的中文显示

库字符集是 `ZHS16GBK`（不是 AL32UTF8）：

- DBeaver + ojdbc 默认走 JDBC 字符转换，**一般无需特殊设置**；若中文变 `?` 或乱码：
  - 检查 Windows 系统区域设置，或本机 `NLS_LANG`（若设过）改为 `SIMPLIFIED CHINESE_CHINA.ZHS16GBK`，**NEVER 设成 `.AL32UTF8`**（与库字符集不符反而乱码）；
  - DBeaver 连接 → 编辑 → 驱动属性可加 `oracle.jdbc.defaultNChar=true` 试一下；
- 库内 `NLS_DATABASE_PARAMETERS` 自证见 §九。
- 部署方案本身在数据库服务器上的口径是 `NLS_LANG=American_America.ZHS16GBK`——客户端设 `SIMPLIFIED CHINESE_CHINA.ZHS16GBK` 与它等价（字符集部分一致即可）。

## 五、网络预检（先于 DBeaver，30 秒定位一半问题）

PowerShell：

```powershell
Test-NetConnection -ComputerName 172.120.110.25 -Port 1521
```

- `TcpTestSucceeded : True` → 网络通，问题在账号 / 服务名 / 驱动，看 §六；
- `False` → PC 不在红岛生产可达网段或防火墙未放通 `172.120.110.25:1521`，找网络管理员开通，**NEVER 反复重试**（连续失败会锁账号，见 ORA-28000）。

> 测试库预检同理：`Test-NetConnection -ComputerName 172.20.222.3 -Port 1521`。

## 六、常见错误与处置

| 报错 | 含义 | 处置 |
|---|---|---|
| `ORA-12514` | 监听不认识请求的服务名 | ① 核对填的是 **Service name = `hditpdb`**，NEVER 用 SID `itpcdb1/2`；② 确认 URL 写法带 `//`；③ 确认连的是 VIP `172.120.110.25` 而非跳板机 / 其他库；④ 由 DBA 在集群上 `lsnrctl status` 确认 `hditpdb` 已注册（PDB 服务由监听动态注册，PDB 未 open 也会 12514） |
| `ORA-12541` / `ORA-12543` | 监听不可达 | 先跑 §5 预检；路由 / 防火墙问题找网络管理员 |
| `ORA-01017` | 用户名 / 口令无效 | 口令现取后仍不过时：① **重敲不要复制**——中文输入法的 `！`/`＃` 是全角（口令含 `!`/`#` 这类半角符号时尤其易踩），复制粘贴可能带首尾空格；② 命令行验证时 MUST 单引号包裹（`sqlplus 'QDITP_USER/<口令>@...'`），bash 的 `!` 会触发历史扩展把口令吃掉；③ 口令区分大小写；④ 口令本身找 DBA 核对是否刚轮换过（旧值 2026-10-26 起视为已泄露）；⑤ 见 §6.2 |
| `ORA-28000` 账号锁定 | 连续认证失败触发锁定 | 找 DBA `ALTER USER qditp ACCOUNT UNLOCK;`；业务侧 **NEVER 自行解锁生产账号** |
| `ORA-00942` 表不存在 | 表名错或账号无权限 | 表名前加 schema 前缀 `QDITP.`；若连的是 CDB 根也会看不到 PDB 对象——先按 §2.2 核对服务名 |
| 中文乱码 | 字符集不匹配 | 见 §4.3 |

### 6.1 ORA-12514 客户端填法确认无误后：集群侧排查

DBeaver 侧已按 §4.1 核对（Service name = `hditpdb`、VIP 正确）仍报 12514，说明监听未注册该服务。按概率排序：

1. **PDB 未 OPEN**（新部署 / 重启后最常见）：19c 中 PDB 只有 OPEN 才向监听注册服务。在任一数据库节点执行：

```bash
su - oracle
export ORACLE_SID=itpcdb1
export ORACLE_HOME=/u01/app/oracle/product/19.0.0.0/dbhome_1
sqlplus / as sysdba
show pdbs                    -- hditpdb 应为 READ WRITE；若为 MOUNTED：
-- alter pluggable database hditpdb open;
-- alter pluggable database hditpdb save state;
```

2. **监听侧确认**：`su - grid` 后 `lsnrctl status`，在 Services Summary 里找 `hditpdb`；服务在却连不上时 `lsnrctl reload` 刷新注册。
3. **VIP 对错判定**：DBeaver 里只改主机名，分别试 `172.120.110.5`（mlc）/ `172.120.110.15`（acc）——三个都 12514 = 服务没注册；某个反而通 = IP 规划与部署文档不一致，找 DBA 核对。
4. 修复后重连若报 `ORA-28000`，是此前反复失败触发锁定，找 DBA 解锁，NEVER 自行解锁生产账号。

### 6.2 ORA-01017（用户名/口令无效）客户端排查与集群侧确认

生产库分域账号口令找 DBA 现取后逐字节核对（纯 ASCII，无全角 / 无隐藏空格；**NEVER 写进文档**，2026-10-26 旧值视为已泄露）。测试库 `qditp/qditp` 同 K8s Deployment env `other.sql.password`。按序排查：

1. **重敲，不要复制**：中文输入法下 `!` `#` 易成全角 `！` `＃`；复制粘贴可能带首尾空格。在 DBeaver 密码框里全选删除后手工输入一遍。
2. **命令行验证时 MUST 单引号**：`sqlplus 'qditp/qditp@172.20.222.3:1521/afcitpdb'`（测试库）—— bash/zsh 的 `!` 会触发历史扩展，不加引号口令会被改写。生产库则用分域账号：`sqlplus 'QDITP_USER/<找DBA现取>@172.120.110.25:1521/hditpdb'`。
3. **区分确切错误码**：DBeaver 报 `ORA-28000` = 账号被锁定（此前 12514 阶段反复重试凑满失败次数），`ORA-28001` = 口令过期，二者都不是口令错，处理见下。
4. 以上都排除后，集群侧确认（DBA，在 PDB 容器内）：

```sql
sqlplus / as sysdba
alter session set container=hditpdb;
select username, account_status, lock_date, profile from dba_users where username='QDITP';
-- LOCKED   → alter user qditp account unlock;
-- EXPIRED  → 重新设置口令
-- OPEN 且口令仍不过 → 部署方案载明「交付以现场实际配置为准」，口令可能已被甲方 DBA 改密，MUST 找 DBA 要新口令
```

## 七、安全红线（NEVER，生产必读）

1. **NEVER 在 DBeaver 里对生产库直接执行 `UPDATE` / `DELETE` / `DDL`** → 数据类变更 MUST 走变更流程：先 `CREATE TABLE BAK_<表>_<日期> AS SELECT ...` 备份、写好还原 SQL、测试库（AFCITPDB）演练后再执行（与 `docs/ops/数据库按域分库计划.md` 同口径）。
2. 查询优先只读：SQL 编辑器首行 `SET TRANSACTION READ ONLY;`（DBeaver 无全局只读开关，需每会话执行）。
3. 长期 / 高频查询 **MUST 向 DBA 申请只读账号**（分库方案规划 `itp_ro` 只读网关账号，落地前以 DBA 实际分配为准），NEVER 长期用 dba 账号裸奔。
4. 口令纪律：口令唯一权威源是部署方案文档与 DBA 下发；**NEVER** 贴群 / 截图（截图 MUST 打码）/ 写进会外发的文件；怀疑泄露 MUST 立即报 DBA 改密。
5. 生产全程有**数据库审计**（`172.120.250.232`）与**堡垒机**（`172.120.250.235`）留痕，NEVER 心存侥幸。
6. 批量导出大数据表前先 `SELECT COUNT(*)` 估算，NEVER 直接 `SELECT *` 拖死会话。
7. 时间列口径：生产库时间列为北京时间（服务器 `TZ=Asia/Shanghai`）；`PAY_TXN_DETAIL.PAY_TIME`、`DAILY_TICKET_INSTANCE.COUNTING_*` 是 **VARCHAR2**，**NEVER** 包 `TO_CHAR`（列类型 MUST 现查：查 `USER_TAB_COLS.DATA_TYPE`；容器时区坑见 `docs/ops/定位排查手册.md` §1.6「时间窗类判定整体失效（容器时区 ≠ 业务时区）」）。

## 八、与既有工具链的关系

| 通道 | 用途 | 说明 |
|---|---|---|
| DBeaver（本手册） | 人工查询 / 排障 / 数据抽样 | 直连 Oracle，无保护，按 §七 自律 |
| EM（DBA 侧） | 生产库统一管理 | `https://172.120.3.131:7803/em`（oracle-EM 节点），日常监控 / 性能以 EM 为准 |
| 业务模块数据源 | 服务侧 | 从 K8s ITP 生产集群（`kubernetes.itp` VIP `172.120.2.10`）连 `172.120.110.25:1521/hditpdb`，口径与本手册一致 |
| MCP 数据库工具（AI 侧） | AI 会话内查库 | 走测试环境 `http://172.20.211.23:30031/mcp` 的 BYOK database MCP，**只覆盖测试库**，与生产库无关 |

## 九、附录：连上后 30 秒自证（PDB 版）

```sql
-- 1. 确认连的是 PDB hditpdb 而不是 CDB 根
SHOW con_name;                                  -- 期望 HDTPDB
SELECT name, open_mode FROM v$pdbs;             -- HDTPDB 应为 READ WRITE

-- 2. 确认字符集（中文显示排查用）
SELECT * FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER IN ('NLS_CHARACTERSETNAME','NLS_NCHAR_CHARACTERSETNAME');   -- 期望 ZHS16GBK / AL16UTF16

-- 3. 确认业务 schema 与表规模
SELECT COUNT(*) FROM all_tables WHERE owner = 'QDITP';

-- 4. 确认是生产还是测试库（看数据时间分布）
SELECT MAX(create_time) FROM qrcode_txn_detail;
```

## 十、附录：DBeaver 常用操作速查

| 想干什么 | 怎么做 |
|---|---|
| 执行 SQL | 新建 SQL 编辑器（选中连接右键 → SQL 编辑器），`Ctrl+Enter` 执行当前句 |
| 看表结构 | 左侧数据库树展开 `QDITP → 表`，双击表 → 「结构」页 |
| 按条件翻数据 | 双击表 → 「数据」页 → 过滤器图标填 `column = value` |
| 导出 INSERT / CSV | 结果集右键 → Export Data → 选格式（灌测试数据 / 留证用） |
| 看表关系 | 选中多表右键 → View Diagram（ER 图） |
| 事务只读 | 编辑器首行执行 `SET TRANSACTION READ ONLY;` |

## 附：源文档与关联

- 源文档：《红岛 Oracle RAC 19c 部署方案》v1.1（2026-10-13，胡辰），本机 `~/workspace/company/chinasofti/红岛部署19c集群.docx`（微信转储）。配套《ACLC红岛数据中心设备管理表-2026年10月15日.xlsx》已提取到 qditp-wiki 仓库 reference 目录的 hongdao-dc 页（IP / 设备 / 口令掩码口径，与本手册 §2.1 互为印证）。
- 同环境另两库（同构，仅 DBA 用）：`hdmlcdb`（`172.120.110.5:1521/hdmlcdb`，dba `qdmlc`）、`hdaccdb`（`172.120.110.15:1521/hdaccdb`，dba `qdacc`）；NEVER 混连。
- 红岛集群部署事实以部署方案与现场实况为准；本手册参数为 2026-10-17 记录值，变动时 MUST 直接改本 §2.1 旧值。
