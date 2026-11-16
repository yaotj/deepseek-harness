# 分库后建表 / 导入脚本（2026-11-12 导出基线）

**输入**：`docs/sql/01_qditp_ddl.sql`（187 张表 / 248 条独立索引 / 1351 条注释 /
12 条约束＝10 条外键 + 2 条 USING INDEX 的 PK/UNIQUE）、
`docs/sql/02_qditp_data.sql`（154 个表块 / 97 张有数据 / 105845 条 INSERT）。
源为测试库 `172.20.222.3:1521/AFCITPDB` schema `QDITP`，**已去除 schema 前缀与 TABLESPACE 子句**。
**生成器**：`docs/ops/environments/scripts/migration/20261112_split_qditp_by_domain.py`（可重跑，带全覆盖断言）。

> 本目录只生成本地文件，**NEVER 连库、NEVER 执行任何 SQL**。
> **2026-11-12 用户裁定：各域账号命名与口令与红岛一致**（见第二节）；
> 目标环境连接串（VIP / 服务名 / 端口）等环境特有值仍 MUST 现查，NEVER 抄红岛 / 测试值。
> 生产执行前 MUST 二次确认 + DBA 审批 + 备份（见 `AGENTS.md` 前置判据）。

## 一、产物

| 文件 | 内容 |
|---|---|
| `00_create_users.sql` | **M1 基建（第 0 步）**：8 个表空间 + 4 域账号 + `itp_ro` + 基础授权；口令与红岛一致，ASM 磁盘组 / PDB 名留现查占位 |
| `01_ddl/QDITP_<域>.sql` × 4 | 分库后建表脚本：建表 → 独立索引 → 注释 → 约束，**全部对象带 `"QDITP_<域>".` 显式前缀** |
| `02_data/QDITP_<域>.sql` × 4 | 分库后导入脚本：`INSERT INTO "QDITP_<域>"."<表>"`，按域内外键拓扑序（父表先入） |
| `03_post_load.sql` | 灌数后校准：全部 identity 列 `START WITH LIMIT VALUE`、2 张恢复 ALWAYS、校验 SQL |
| `_未纳入_BAK快照与STATION_INFO.sql` | 17 张排除表的注释态 DDL（NEVER 直接执行） |

**四域统计**（本次生成实测，重跑会随源刷新）：

| 域 | 表 | 独立索引 | 注释 | 外键约束 | 有数据表 | INSERT 行 |
|---|---|---|---|---|---|---|
| `QDITP_PLAT` | 108 | 57 | 661 | 6 | 54 | 94333 |
| `QDITP_USER` | 20 | 55 | 272 | 1 | 11 | 5275 |
| `QDITP_RIDE` | 3 | 6 | 30 | 0 | 2 | 2101 |
| `QDITP_TXN` | 39 | 128 | 383 | 3 | 17 | 3376 |
| **合计** | **170** | **246** | **1346** | **10** | **84** | **105085** |

> 守恒口径：**域统计合计 = 源导出 − 排除表**。排除 17 张表带走 建表 17 / 独立索引 2 / 注释 5 / 约束 0 条语句与 760 条 INSERT，全部在 `_未纳入_BAK快照与STATION_INFO.sql` 留痕（注释态）。
> 「约束」列只统计第 4 部分的 10 条外键；另有 2 条 USING INDEX 的 PK / UNIQUE 约束
> 内联在 `ALIPAY_SIGN_INFO`(USER) / `GATE_TXN_PAY`(TXN) 的建表块内，随本表一起输出。

## 二、账号命名与口令（2026-11-12 用户裁定：与红岛一致）

| 域 | schema / 账号 | 口令 | 默认表空间 | 临时表空间 |
|---|---|---|---|---|
| PLAT | `QDITP_PLAT` | `itp!#plt6308` | `ITP_PLAT_TS` | `ITP_PLAT_TEMP` |
| USER | `QDITP_USER` | `itp!#usr4726` | `ITP_USER_TS` | `ITP_USER_TEMP` |
| RIDE | `QDITP_RIDE` | `itp!#rd2947` | `ITP_RIDE_TS` | `ITP_RIDE_TEMP` |
| TXN | `QDITP_TXN` | `itp!#txn8153` | `ITP_TXN_TS` | `ITP_TXN_TEMP` |
| 网关只读 | `itp_ro` | `itp!#ro1592` | 共享 USERS + TEMP | — |

> **裁定口径**：四域账号命名（`QDITP_PLAT` / `QDITP_USER` / `QDITP_RIDE` / `QDITP_TXN`）、
> 表空间命名（`ITP_<域>_TS` / `ITP_<域>_TEMP`）与口令**全部与红岛一致**；
> 口令由生成器从唯一真源 `docs/ops/environments/跨环境共性.md`「三、敏感值真源」动态解析，
> **NEVER 在此硬编码**。
> 改密 MUST 同步改该真源表 + `红岛.md` 生产库节 + 各模块 `application.properties` +
> `itpops/cloudos/auth.py` 的 `_PROFILES` + `itpops/web/frontend_release.py` 的 `_profile()` +
> `20261014_bundle_m1_infra.sql`，然后**重跑本生成器**让 8 个脚本头与本节同步。
> 口令含 `!` / `#`，sqlplus 命令行 MUST 用单引号包裹（防 shell 历史展开），
> 或改走 `CONNECT <用户>/"<口令>"@//<VIP>:1521/<服务名>` 双引号定界。
>
> `00_create_users.sql` 的 5 个 `CREATE USER` 复用同一批口令（同样从真源动态解析），NEVER 另设口令。
>
> **仍 MUST 现查**：DB VIP / 服务名 / 端口 / NodePort / 公网入口 / HSM 等环境特有值，
> **NEVER 抄红岛 / 测试值**；本裁定只覆盖「账号命名 + 口令」两项。


## 三、域映射与本次新增裁决

基线真源是 `docs/ops/environments/scripts/migration/domain_map.py`（2026-10-23 四域清单，175 张）。
**本次导出多 12 张表**，增量映射记在生成器里，**NEVER 直接改 `domain_map.py`**——
10-23 两个拆分脚本断言「映射 == 175 对象全覆盖」，加表会让它们重跑失败。

| 新增表 | 归属 | 依据 | 状态 |
|---|---|---|---|
| `ASYNC_EXPORT_TASK` | PLAT | web-admin 异步导出（`AsyncExportQuartzTask` / `async-export-mvp.sql`） | 按代码归属推定，**MUST 经 DBA / 业务确认** |
| `GRAYLIST` / `GRAYLIST_OPERATE_LOG` | PLAT | blacklist-server（`GraylistService` / `graylist-schema.sql`） | 同上 |
| `HCE_BLACKLIST_FILE_STATE` | PLAT | blacklist-server HCE 黑名单文件版本状态表 | 同上 |
| `RECON_UPLOAD_FILE` | PLAT | **web-server 综管台**对账上传记录（`recon-upload-file-menu.sql`），与 recon-server 的 `RECON_*` 无外键 | 同上；名字带 RECON 易误判归 TXN，MUST 确认 |
| `TBL_DEVICE_INFO` | PLAT | para-server（`DeviceInfoServiceImpl` / `para-schema.sql`）；10-23 版曾是「本地有 DDL、老库导出无此表」的 UNKNOWN 桶，本次老库导出已证实存在 | 同上 |
| `BAK_SYS_*_20261104/20261106` 6 张 | **排除** | CTAS 快照，重建不需要 | 与旧 `BAK_*` 同口径 |

> 确认后 MUST 回写：`domain_map.py` 的四域清单 / `EXCLUDED`、`docs/ops/数据库按域分库计划.md`「3. 表归属清单」节、
> `deploy/sche/by-domain/README.md` 的映射总表，并重跑 10-23 两个拆分脚本核对（届时需同步放宽其 175 断言）。

**排除表 17 张**：`BAK_*` 16 张 CTAS 快照 + `STATION_INFO`（不迁，RIDE 改同义词读
`QDITP_PLAT.TBL_STATION_INFO`）。数据文件里这些表仍有 INSERT，本产物**不导入**。

## 四、执行顺序（本机不执行，供 DBA 走审批）

1. **M1 基础设施**：执行 `00_create_users.sql`（8 个表空间 + 4 域账号 + `itp_ro` + 基础授权；
   结构口径同 `docs/ops/environments/scripts/migration/20261014_bundle_m1_infra.sql` Step 1~3）。
   **`<ASM 磁盘组>` / `<目标PDB>` / 连接串 MUST 现查替换**，NEVER 照抄红岛的 `+DATA` / `HDTPDB` /
   `172.120.110.25`——旧脚本 Step 0 断言把这三个值写死，换环境会直接 EXIT。
   **账号命名与口令按第二节裁定与红岛一致，NEVER 改**。
2. **建表**：按 `PLAT → USER → RIDE → TXN` 执行 `01_ddl/QDITP_<域>.sql`，各域**以本域用户连接**：
   `sqlplus 'QDITP_<域>/<第二节对应口令>@//<目标VIP>:1521/<服务名>' @QDITP_<域>.sql`
   （单引号 MUST 保留：口令含 `!` / `#`；NEVER 用 SYS）。
3. **跨域同义词 / 只读授权**：`docs/ops/environments/scripts/migration/20261014_create_split_grants_synonyms.sql`
   至今仍是草稿、文件头写明严禁直接在生产执行；服务切 datasource 前 MUST 落实，否则跨域读
   PLAT 表（如 `TBL_STATION_INFO`）直接 `ORA-00942`。
4. **导数据**：按域执行 `02_data/QDITP_<域>.sql`；TXN 域灌数期 MUST 停 recon / face-pay / web-admin
   定时任务（双跑红线，见 `docs/ops/K8S迁移清单.md`「R5 切换方式」）。
5. **灌数后校准**：执行 `03_post_load.sql`（identity 列校准 + 2 张恢复 ALWAYS + 校验 SQL）。
6. **校验与切流**：逐表行数零差异、索引无 UNUSABLE、约束全 VALIDATED、分区合规、同义词可解析；
   再切各模块 datasource、观察 30 分钟 + 跑一笔真实交易，观察期 7 天。
7. **回滚**：4 级递进（撤授权 → 收角色 → 切回原 datasource → 兜底删 schema），
   见 `docs/ops/environments/scripts/migration/20261014_rollback_split.sql`。

## 五、必须知道的坑（红岛 2026-10-23 实测 + 本次导出新发现）

1. **2 张 `GENERATED ALWAYS AS IDENTITY` 带显式 ID**：`APP_PAY_SIGN_REQUEST.ID`（TXN，210 行）、
   `GATEWAY_ROUTE_CONFIG_AUDIT.AUDIT_ID`（PLAT，18 行）。直接灌数会 **ORA-32795** ⇒ 建表脚本里
   这两张已临时降为 `BY DEFAULT`，灌完 MUST 由 `03_post_load.sql` 恢复 ALWAYS。
2. **identity 列不校准 = 后台首次新增即主键冲突**：本次导出共
**37 个 identity 列**，导完一律 `START WITH LIMIT VALUE`（红岛踩过）。
3. **虚拟列不能 INSERT（ORA-54013）**：`QRCODE_STATUS.CARD_ID_TAIL`、
   `USER_ITP_REG_INFO.THIRD_USER_ID_SUFFIX`；源导出已自动把它们排除在 INSERT 列清单外，NEVER 手工补回。
4. **外键零跨域**（12 条约束全部域内），但**数据顺序仍要拓扑**：字母序会让 `QRTZ_BLOB_TRIGGERS`
   先于父表 `QRTZ_TRIGGERS` 插入 → FK 违例；生成器已按域内 FK 拓扑排序。
5. **独立索引这次是齐的**：源导出共 248 条（246 条在第 2 部分 + 2 条内联在
   `ALIPAY_SIGN_INFO` / `GATE_TXN_PAY` 的建表块，其中 82 条 UNIQUE），不像 10-23 版缺 169 个；
   那 2 张表的 `USING INDEX` 约束引用同块建立的索引，故四部分顺序 NEVER 调换。
   兼容性兜底：若目标库对 `USING INDEX "QDITP_<域>"."<索引>"` 报 ORA-00903 / ORA-01418，
   把该处 schema 前缀去掉即可（会话已设 `CURRENT_SCHEMA`，裸索引名可解析到本域）。
6. **分区**：源导出有 13 处 `PARTITION BY`；RANGE 表老库实况是 `P202606~P202612 + P_MAX`，
   2027-01-01 起数据全落 `P_MAX`、按月归档失效 ⇒ **2026-12-15 前**补月度分区或改 INTERVAL。
7. **字符集 / 客户端**：源文件 UTF-8；目标库 AL32UTF8 直接用 UTF-8 客户端，ZHS16GBK 由驱动转码
   （见 `01_qditp_ddl.sql` 头部）。DDL 带 `COLLATE "USING_NLS_COMP"`，目标库须为 12.2+。
8. **`WHENEVER SQLERROR CONTINUE` 会静默吞错**（红岛首轮 102/102 全失败才发现）：
   执行后 MUST 跑 `03_post_load.sql` 的校验 SQL + 逐表行数比对，NEVER 只看 sqlplus 退出码。
9. **幂等**：重复建表报 ORA-00955、重复导入报主键/唯一键冲突；重导前 MUST TRUNCATE 或换库。

## 六、与 2026-10-23 红岛版（`deploy/sche/by-domain/`）的关系

两版并存、**NEVER 互相覆盖**：那版源为 10-23 老库导出（175 对象、带 `"QDITP".` 前缀、缺独立索引），
表结构防老库漂移、索引靠本地 DDL 补；本版源为 11-12 新导出（187 表、已去前缀、含全部独立索引），
**对齐老库当前实况**。红岛四域已于 2026-10-23 建完（164 表 / 362 索引 / 341.2 万行），
本产物用于新环境（如辽阳东）重建或重跑演练，**NEVER 直接对红岛生产库执行**。

## 七、NEVER 清单

- NEVER 把本目录脚本直接对生产库执行（MUST DBA 审批 + 二次确认 + 备份 + 测试库演练）；
- NEVER 抄红岛 / 测试的**连接串（VIP / 服务名 / NodePort / 公网入口 / HSM 地址）**到目标环境；
  账号命名与口令按 2026-11-12 裁定与红岛一致（见第二节），口令真源见
  `docs/ops/environments/跨环境共性.md`「三、敏感值真源」，改密 MUST 重跑生成器；
- NEVER 用 SYS 或其它域账号执行域脚本；NEVER 调换文件内四部分顺序；
- NEVER 手改生成物（改口径请改生成器后重跑，保证断言与 README 统计同步）。
