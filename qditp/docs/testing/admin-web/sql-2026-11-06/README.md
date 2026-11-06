# 数据库变更脚本包 · 2026-11-06 · 综管台对标补建 + UI 优化落库

> 用途：把 11-04 完成的「对标补建 28 页」代码（已发测试环境）配套的**数据库变更**落到测试库 AFCITPDB。
> 执行通道：MCP `mcp_database_qd`（`http://172.20.211.23:30031/mcp`）或 DBeaver。
> 环境：测试库 `AFCITPDB`（`172.20.222.3:1521`，账号 `qditp`）。

## 一、为什么需要这套脚本

11-04 已完成代码并发版测试环境（web 1.2.44.18 + 7 后端模块 jar），但**数据库变更未执行**，导致：

- **blacklist-server** 灰名单端点报 `ORA-00942: table or view does not exist`（`GRAYLIST` / `GRAYLIST_OPERATE_LOG` 表不存在）
- **菜单** 未入库（28 个新页面在综管台侧边栏不可见）
- **IT-056** 异步导出调度仍暂停（`sys_job` JOB_ID=405）
- **B19** 部门/岗位演示数据未清理

## 二、执行顺序（MUST 按序）

| # | 脚本 | 作用 | 风险 |
|---|---|---|---|
| 1 | `00-backup-before.sql` | 落原值快照（可回滚） | 无（只读+建备份表） |
| 2 | `10-graylist-ddl.sql` | 建 `GRAYLIST` / `GRAYLIST_OPERATE_LOG` 两表 | 低（新建表，不影响既有） |
| 3 | `20-legacy-txn-menu.sql` | 交易 6 页菜单 | 低（幂等 INSERT） |
| 4 | `21-report-menu.sql` | 报表 12 页菜单 | 低 |
| 5 | `22-graylist-menu.sql` | 灰名单菜单 | 低 |
| 6 | `23-alipay-channel-menu.sql` | 渠道 3 页菜单 | 低 |
| 7 | `30-it056-enable-job.sql` | 启用异步导出调度 + cron 改分钟级 | **中**（需重启 web-admin） |
| 8 | `40-b19-clean-demo.sql` | 清理部门/岗位演示数据 | **中**（影响共享环境） |
| 9 | `99-restore-after.sql` | 还原（仅回滚时执行） | — |

## 三、执行后 MUST 做

1. **重启 web-admin**（IT-056 内存 JobStore 要求）：
   ```bash
   itp-ops.sh --env test cloudos act web-admin restart --yes
   ```
2. **验证菜单**：登录综管台（30029）→ 侧边栏应出现「交易管理 6 页 / 报表 12 页 / 灰名单 / 渠道 3 页」
3. **验证灰名单**：侧边栏「风险管理 → 灰名单管理」可打开、可查询
4. **验证异步导出**：等 5 分钟后查 `SYS_JOB_LOG` 是否有 JOB_ID=405 的新记录
5. **验证部门/岗位**：综管台「系统管理 → 部门管理 / 岗位管理」不再显示 RuoYi 演示值

## 四、回滚

执行 `99-restore-after.sql`（已含全部还原逻辑）。**快照表保留**，验证通过后由 DBA 手工 DROP。

## 五、关联文档

- 方案：`docs/design/综管台UI-UE优化方案-2026-11-04.md`
- 对标补建执行记录：`docs/testing/admin-web/09-执行记录-2026-11-04-对标补建.md`
- 问题清单：`docs/tracking/问题清单.md`（IT-056 / IT-061）
