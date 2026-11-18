# 旧 ITP 灰名单导出包（2026-11-17 导出，2026-11-18 网络恢复后导入）

> **新增（2026-11-17 晚）**：本目录另产出 [旧灰名单功能梳理](旧灰名单功能梳理.md)（旧系统风控链路+灰名单全字段实扫事实）与 [新灰名单功能对标计划](新灰名单功能对标计划.md)（明天连网后执行的方案：新建 `GRAYLIST_RISK` 台账表 + 只读页 + 迁 1821 行 + 验证/回滚）。`old-side-evidence/` 存抓取的 9 个页面/JSON 证据。

## 一、导出概况

| 项 | 值 |
|---|---|
| 来源 | 旧 ITP `http://58.56.166.170:9080/qd-omp/a/risk/riskGrayList/listData`（`FuTianTest001` 登录，只读导出） |
| 导出时间 | 2026-11-17 19:5x（本机），旧页显示 1821 条 / 92 页 |
| 记录数 | **1821 行**，**948 张不同卡**，**4 种风险规则** |
| 风险规则分布 | 最大允许单边交易数 616 / 单日累计允许自助处理次数 467 / 扣款失败 412 / 最大允许进入黑名单次数 326 |
| 入库时间跨度 | 2018-06-07 ~ 2026-11-16（说明该表是长期自动累积的台账） |
| 字段 | `userId` 逻辑卡号 / `riskRuleId` 风险规则**名称**（非编码）/ `riskRuleIdTimes` 发生次数 / `riskOccursLastDate` 最后发生时间 / `managerId` 管理编码 / `regDate` 入库时间 / `updateDate` 更新时间 |

## 二、文件清单

| 文件 | 用途 |
|---|---|
| `old-graylist-raw.json` | 接口原始返回（含 1821 条全字段），**保真正源，勿改** |
| `old-graylist-full.csv` | 展开的 CSV（UTF-8 BOM），便于核对/Excel 打开 |
| `gen_import_sql.py` | 从 raw.json 生成两个导入 SQL 的脚本（可重跑） |
| `import-A-faithful-GRAYLIST_LEGACY.sql` | **Option A（推荐·保真）**：建 `GRAYLIST_LEGACY` 表，导入 1821 行，字段一一对应，不丢明细 |
| `rollback-A-drop-GRAYLIST_LEGACY.sql` | Option A 回滚（DROP 表，不可恢复——先确认 raw/csv 留存） |
| `import-B-aggregate-into-GRAYLIST.sql` | **Option B（有损）**：按卡聚合成 948 行塞进现有 `GRAYLIST`，逐规则明细压进 REASON 文本 |
| `rollback-B-clean-LEGACY_MIGRATE.sql` | Option B 回滚（按 `CREATE_BY='LEGACY_MIGRATE_20261118'` 删，不碰运维手工进灰数据） |

## 三、⚠️ 必须先做的决策（本次未擅自定）

**旧灰名单与新 `GRAYLIST` 不是同一功能**：
- 旧＝风控规则命中自动入灰、累计「发生次数/最后发生时间」的**只读台账**，主键是 (卡号+风险规则)，一卡可多行（948 卡 → 1821 行）。
- 新＝运维手工进灰/移出的**观察名单**，`CARD_ID` 有 UNIQUE 约束，一卡只能一行。

因此**直接把 1821 行灌进现有 `GRAYLIST` 会撞 UNIQUE 约束失败**（873 行重复卡号）。两个选项：

| | Option A（推荐） | Option B（有损） |
|---|---|---|
| 落点 | 新建 `GRAYLIST_LEGACY` 表 | 现有 `GRAYLIST` 表 |
| 行数 | 1821（逐规则保真） | 948（按卡去重） |
| 明细 | 风险规则/次数/最后时间/入库时间/管理编码全保留 | 压成 REASON 文本，不可结构化查询 |
| 何时选 | **默认选这个**——完整保留旧台账 | 仅当业主明确说"旧明细不要了，只要卡号进新观察名单" |
| 代价 | 新库多一张表，前端暂无页（可后续补只读页） | 丢明细，且给观察名单混入系统数据来源 |

> 请在 2026-11-18 执行前明确：选 A 还是 B。**默认按 A 执行**。

## 四、明天执行步骤（网络恢复后）

1. **前置校验**：
   - 新测试库 `AFCITPDB` 可达；确认 `GRAYLIST` 当前 `total=0`（或已知运维手工行数）。
   - 选 A：`SELECT COUNT(*) FROM GRAYLIST_LEGACY;` 应报ora-00942（表不存在），确认无同名表。
   - 选 B：先记录 `SELECT COUNT(*) FROM GRAYLIST;` 原值给回滚参照。
2. **导入**：用 DBeaver/MCP 数据库通道执行 `import-A-*.sql` 或 `import-B-*.sql`（A 脚本含 DDL，需有建表权限）。
3. **验证**：
   - A：`COUNT(*)=1821`、`COUNT(DISTINCT USER_ID)=948`、风险规则 4 种分布对得上（616/467/412/326）。
   - B：`COUNT(*) WHERE CREATE_BY='LEGACY_MIGRATE_20261118' = 948`，抽查几张卡的 REASON 文本。
4. **回滚**（如有问题）：执行对应 `rollback-*.sql`。
5. **回写**：在 `docs/testing/11-核验报告-综管台对齐现状-2026-11-17.md` 与 `问题清单.md` 登记导入结果。

## 五、安全与口径

- 全程**只读导出**旧系统，未做任何写操作（未改密码、未增删灰名单）。
- 迁移数据含真实卡号，属生产规模数据；测试环境内使用，NEVER 外传。
- 旧灰名单业务语义待业主裁定（见 11-核验报告 §6）——**本次导入只是搬数据，不代表功能已对齐**；导入后仍需决定新系统是补只读台账页，还是此数据仅作存档。
