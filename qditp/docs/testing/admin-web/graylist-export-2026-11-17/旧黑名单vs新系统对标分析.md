# 旧黑名单 vs 新系统黑名单对齐分析（2026-11-17 实扫）

> 事实来源：旧系统 `http://58.56.166.170:9080/qd-omp/a/risk/riskBlackList/*` 登录实扫（`FuTianTest001`）+ 新系统源码/DB DDL 比对。
> 证据：`docs/testing/admin-web/graylist-export-2026-11-17/old-side-evidence/old_riskBlackList.html` + `blacklist_detail_form.html` + `blacklist-data.json` + `old-blacklist-optHist-*.json/csv`。
> **本报告只分析、不写代码/不发版**；涉及写操作的缺口已标注，需业主裁定后按灰名单对标计划的同等流程执行。

## 1. 旧黑名单（实扫 3 条）

### 1.1 列表页
- **面包屑**：风险控制 / 黑名单管理
- **查询条件**：逻辑卡号 / 审批状态(下拉：待审批 / 已审批 / 已停用) / 管理编码
- **表格列**（5 列）：
  | 列头 | 字段 | DB 列 | 说明 |
  |---|---|---|---|
  | 逻辑卡号 | userId | a.user_id | 可点击跳详情（formatter→/form?idNo=xxx） |
  | 审批状态 | status | a.status | 字典：0 待审批 / 1 已审批 / 2 已停用 |
  | 管理编码 | managerId | a.manager_id | — |
  | 更新时间 | updateDate | a.update_date | — |
  | 操作 | actions | — | 编辑（`/form?idNo=xxx`）；**删除已注释掉** |
- **工具栏**：查询 / **新增**（`/riskBlackList/form` 空 form）
- **数据量**：当前 **3 条**（11-03 报告记 339 条，数据持续增长后已过时）

### 1.2 详情页（`/riskBlackList/form?idNo=xxx`）
- **表单字段**（POST `/riskBlackList/save`）：
  - `id` / `idNo`（hidden）
  - `userId`（文本，只读展示）
  - `status`（文本，readOnly=true，显示当前审批状态）
  - `hanleDesc`（文本，maxlength=500，备注字段，可编辑）
- **4 个按钮**：
  | 按钮 | JS 动作 | API | 返回码语义 |
  |---|---|---|---|
  | **加入黑名单** | `pass("1")` → `POST /updateStatus{rStatus=1}` | `/riskBlackList/updateStatus` | 0=成功 / 1=加入失败 / **3=已达拉黑上限永久禁用** / 99=已存在 |
  | **移除** | `pass("2")` → `POST /updateStatus{rStatus=2}` | 同上 | 0=成功 / 2=移除失败 |
  | **保存** | 表单 submit → `/riskBlackList/save` | — | 保存 hanleDesc 备注 |
  | **关闭** | `js.closeCurrentTabPage()` | — | 仅关 tab |
- **操作记录子表**（详情页嵌入，`riskBlackListOptHist/listData?userId=xxx`）：
  - 列：管理编码 / 审批状态 / 备注 / 更新时间
  - 每条黑名单默认 **1 条**历史记录（入灰时写一条）

### 1.3 业务语义总结
- 审批流：手动加入（`加入黑名单` 按钮）→ 待审批(0) → 已审批(1) / 已停用(2)
- **没有渠道同步概念**（无 channel_sync_* 字段）
- 业务错误码含「已达拉黑上限→永久禁用」——这是旧引擎的业务规则，**新系统没有对应语义**
- 入灰是**人工触发**（按钮），不是规则引擎自动累计

## 2. 新系统黑名单（现状）

### 2.1 表结构
- `BLACKLIST`：CARD_ID(唯一) / THIRD_USER_ID / CARD_TYPE / CHANNEL_CODE / BLACK_SOURCE('09') / BLACK_CAUSE('09') / BIZ_NO / REASON / CREATE_BY / CREATE_TIME / CHANNEL_SYNC_STATUS(PENDING/SUCCESS/FAIL...) / CHANNEL_SYNC_TIME / CHANNEL_SYNC_RETRY / CHANNEL_SYNC_FAIL_REASON / STATUS(ACTIVE/RELEASING) / RELEASE_REASON / RELEASE_BY
- `BLACKLIST_OPERATE_LOG`：CARD_ID / THIRD_USER_ID / OPERATE_TYPE / REASON / OPERATE_TIME / OPERATOR
- `BLACKLIST_RELEASED`：历史解除行（两阶段改造前）

### 2.2 前端页（`views/trans/blacklist/index.vue`）
- **查询条件**：卡ID / 三方用户ID / 状态(生效中/解除中) / 渠道同步(PENDING/SUCCESS/FAIL/NONE) / 创建时间区间
- **表格列**：序号 / 卡ID / 三方用户ID / 状态(Step 图示) / 渠道同步 / 拉黑原因 / 解除原因 / 创建时间 / **操作(解除)**
- **新增 dialog**：卡ID / 三方用户ID / 卡类型 / 渠道 / 拉黑分类 / 关联业务号 / 拉黑原因
- **解除 dialog**：卡ID / 解除原因 / 操作人
- **状态机**：ACTIVE(生效中) ↔ RELEASING(解除中) —— 两阶段解除机制
- **渠道同步**：PENDING → SUCCESS/FAIL，失败可重试

### 2.3 后端接口（`BlacklistController`）
- `GET /page/blacklist`：分页查询（cardId/thirdUserId/status/channelSyncStatus/timeRange）
- `POST /page/blacklist`：新增（`AddBlackListReqDTO`，blackSource 定死 `09`）
- `DELETE /page/blacklist/{cardId}`：解除（`DeleteBlackListReqDTO`，两阶段 → RELEASING）
- `POST /queryBlackList` / `/addBlackList` / `/deleteBlackList`：内部服务调用端点

## 3. 新旧差异对照

| 维度 | 旧系统 | 新系统 | 判定 |
|---|---|---|---|
| 数据模型 | 单行 per card（主键 idNo） | 单行 per card（CARD_ID UNIQUE） | ✅ 等价 |
| 审批状态 | 0 待审批 / 1 已审批 / 2 已停用 | ACTIVE(生效中) / RELEASING(解除中) | ❌ **状态机不同** |
| 入灰方式 | 人工按钮「加入黑名单」 | 前端新增 dialog + POST /page/blacklist | ⚠️ 方向一致但语义不同（旧无渠道同步） |
| 移出方式 | 按钮「移除」→ updateStatus(2) | 前端「解除」按钮 → DELETE /page/blacklist/{cardId}（两阶段） | ❌ **移出机制不同**（旧一步到位，新两步） |
| 渠道同步 | 无 | CHANNEL_SYNC_STATUS / TIME / RETRY / FAIL_REASON | ❌ 新多出功能（旧无此需求） |
| 操作记录 | 详情页嵌入 optHist 子表（按 userId 查） | `BLACKLIST_OPERATE_LOG` 表（有但前端无子表展示） | ⚠️ 新有表无 UI |
| 业务错误码 | 「已达拉黑上限→永久禁用」(rStatus=3) | 无对应（新系统无上限概念） | ❌ 语义丢失 |
| 数据量 | 3 条（11-17 快照，持续增长） | 测试库 0 条 | — |
| 菜单位置 | 风险控制 / 黑名单管理 | 风险管理（独立一级菜单 2013） | ⚠️ 分组口径不同 |

## 4. 对齐缺口分析

### 4.1 必须对齐的（功能等价）
1. **「加入黑名单」动作**：旧是一步到位；新是两阶段（ACTIVE→RELEASING）。若业主希望新系统保留旧的一步到位语义，需裁定是否引入新状态 `ADDED` 或复用 `ACTIVE`。
2. **「移除黑名单」动作**：旧是直接移除；新是两阶段解除。语义不同——旧移除后卡立即出黑名单，新移除后要等渠道推达。
3. **操作记录子表**：旧详情页嵌入 optHist，新系统有表 `BLACKLIST_OPERATE_LOG` 但前端未展示。

### 4.2 可选对齐的（新系统超越旧系统）
4. **渠道同步**：旧无，新有（CHANNEL_SYNC_* 四列）。属于新系统的增值功能，无需对齐旧。
5. **两阶段解除**：新系统特有设计（防止渠道推达前误放行）。旧无此概念，属升级而非对齐。
6. **STATUS_RELEASING**：新状态，旧无对应。

### 4.3 待业主裁定
| 项 | 问题 | 建议 |
|---|---|---|
| Q_B1 | 是否需要对齐旧「已达拉黑上限→永久禁用」业务语义？ | 默认**不需要**——新系统无此上限概念，属旧引擎特有规则 |
| Q_B2 | 是否需要在详情页嵌入操作记录子表（对标 optHist）？ | 默认**需要**——运营核对入灰/移出流水的依据 |
| Q_B3 | 旧 3 条存量数据是否迁移？ | 默认**迁移**（数量少，迁移成本低） |
| Q_B4 | 状态机是否对齐？（旧 0/1/2 vs 新 ACTIVE/RELEASING） | 默认**不对齐**——新系统两阶段是升级，非回归 |

## 5. 对标方案（若业主确认 Q_B1~Q_B4）

### 5.1 最小对齐（仅补 UI 缺口）
- **后端**：新增 `GET /page/blacklist/operate-logs`（复用 `BLACKLIST_OPERATE_LOG` 现有 mapper），入参 cardId/pageNum/pageSize。
- **前端**：黑名单详情页嵌入 optHist 子表（参考灰名单 `handleLogs` 做法），列表行点击打开含子表的 dialog。
- **数据迁移**：旧 3 条 → 新 `BLACKLIST`（映射：userId→CARD_ID，hanleDesc→REASON，regDate→CREATE_TIME，status=0→ACTIVE）。同时迁移 3 条 optHist 到 `BLACKLIST_OPERATE_LOG`（OPERATE_TYPE='ADD'）。

### 5.2 完整对齐（如需状态机对齐）
- 需新增旧状态 0/1/2 到新状态 ACTIVE/RELEASING 的映射逻辑
- 需实现旧「加入黑名单」一步到位的语义（可能需新增 `addBlackList` 接口绕过两阶段，或加开关）
- 需实现旧「移除」一步到位语义（跳过 RELEASING 直接进入 released 表）
- **风险高**：涉及支付安全红线（AGENTS.md §4.2），MUST 走 IT 重档 + 安全复核

### 5.3 推荐方案
**5.1 最小对齐**优先实施（补 UI 缺口 + 迁 3 条存量），**5.2 完整对齐**作为二期课题待业主裁定。

## 6. 与灰名单计划的关联

- 灰名单计划（`新灰名单功能对标计划.md`）范围限定在灰名单，**不含黑名单**。
- 本分析为黑名单对标提供事实基础，后续如需推进可按同等流程（裁定→方案→实施→发版）执行。
- 两张表都在 `blacklist-server` 模块，可合并一次发版。
