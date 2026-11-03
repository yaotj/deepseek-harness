# IT-047 员工卡注销后查询仍回查 ACC 补全资料

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-047` 一行。

## 1. 背景 / 现象

- 发现时间：2026-11-02 18:38
- 发现渠道：ACC 发来注销请求后的链路检查
- 影响范围：电子员工卡 **注销（`CARD_STATUS=4`）后的 APP 查询**路径
- 现象描述：
  - 18:37:25 ACC 推 `cardStatus=4`（注销）→ ITP `account-server` 处理成功，返回 `0000`（traceId `2cd1fb99e4610afd3b2835b23a4faf2d`）
  - 库内 `USER_ACC_EMPLOYEE_CARD`：`CARD_STATUS` 由 `1` 变 `4`，`EMPLOYEE_NAME`/`ID_CARD_NO`/`COMPANY`/`CENTER`/`DEPARTMENT`/`POSITION`/`PHOTO_URL` 均被清空
  - 18:40:21 模拟 APP 调 `POST /ci/app/employeeCard/query` → 返回 `cardStatus=4`，但 `employeeName`/`company`/`department`/`photoUrl` 等资料字段又变回非空
  - 即：**注销后敏感资料被 ITP 自身「补全」机制写回本地表**

## 2. 根因分析

- 代码事实（`EmployeeCardServiceImpl.queryEmployeeCard`）：
  - 查询时若 `employeeName` 为空，会调用 `employeeCardOutboundService.queryFromAcc(cardNo)` 向 ACC 回查资料
  - 回查成功后通过 `employeeCardPersistenceService.refreshProfileFromAcc(card, accCard)` 把资料写回本地表
  - 该逻辑**未判断当前 `CARD_STATUS`**
- 根因结论：**注销（`4`）是终态，资料字段本应随注销清空；但查询补全逻辑对所有状态生效，导致终态卡仍被回灌资料。**
- 对照：ACC 通知路径（`saveFromStatusNotify`）在 `CANCELED` 时会清空资料并设置 `CANCEL_TMS`，逻辑正确；问题出在查询侧的多一次 ACC 回查。
- 额外风险：
  - 隐私/合规：已注销员工的姓名、身份证号、照片等因查询被重新写回 ITP 本地表
  - 稳定性：每次注销后查询都多一次出向 ACC 调用，ACC 若不可达则查询失败或告警

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A. 查询补全排除 `4` | `queryEmployeeCard` 仅在非注销状态下触发 ACC 资料补全 | 改动最小，精准修复 | 需确认 `2`/`3` 态是否仍需补全（禁用/未启用员工资料应仍存在，保留补全合理） | 低 |
| B. 注销后物理删除记录 | `CARD_STATUS=4` 时直接删主表记录 | 最彻底 | 破坏日志可追踪性；与现有 `CANCEL_TMS`、`CANCEL` 日志事件冲突 | 高，否 |
| C. 查询接口对 `4` 返回不存在 | 注销后统一返回「员工码信息不存在」 | 语义简单 | 与 ACC 侧「注销后仍可查询历史」的潜在需求冲突；且影响 APP 侧错误提示 | 中，暂否 |

## 4. 决策与结论

- 选定方案：**A（查询补全排除 `CARD_STATUS=4`）**。
- 决策理由：注销是终态，资料不应再被刷新；`2`（禁用）和 `3`（未启用）员工仍在职，资料缺失时回查 ACC 补全属于合理容错。修改应最小化，只加一条状态判断。
- 实施内容（2026-11-02）：
  - `EmployeeCardServiceImpl.queryEmployeeCard`：资料补全条件增加 `&& !EmployeeCardStatus.CANCELED.is(card.getCardStatus())`；
  - 新增单测 `EmployeeCardQueryTest`：3 条用例覆盖「正常态补全」「注销态不补全（资料为空）」「注销态不补全（资料仍存在）」。
- 影响范围 / 关联模块：account-server（`EmployeeCardServiceImpl` 查询分支 + `EmployeeCardQueryTest`）；`04-电子员工卡.md` 注销链路断言。

## 5. 验证与回滚

- **2026-11-02 18:46 发布并实测通过**：`account` 2.0.89 → **2.0.90**（镜像 digest `sha256:8a6d9527270d74887e4dce7da7f5b000ddb0806636a0428405c8abec2c0f0c79`，2 实例 imageID 与 digest 一致）。
- 修复后判据（卡 `13111111111`，手工造数验证）：
  1. 数据库手动置为 `CARD_STATUS=4` 且资料字段为空；
  2. APP 查询 `POST /ci/app/employeeCard/query` → `0000`，`cardStatus=4`；
  3. 返回 `employeeName`/`idCardNo`/`company`/`department`/`position` 均为 `null`；
  4. 再次查询数据库，资料字段**仍为 null**，确认未触发 ACC 回查补全。
- **2026-11-02 18:58 真实 ACC 二次验证（另一个用户 `13045027231`）**：
  1. ACC 真实下发 `cardStatus=4`（注销）→ ITP `0000` 处理成功，库内 `CARD_STATUS 1→4`，全部资料字段清空为 null，`CANCEL_TMS` 写入，日志 `CANCEL`/`4`/`员工码状态通知处理成功`（traceId `30f6199f46a291db4949a0ecf7fe13dd`）；
  2. 紧随一条 `update_notify`（`CHANGE`/`4`/`员工信息变更通知处理成功`，18:58:26.743）——ACC 在注销后同步推了资料变更，请求体资料字段亦为 null，未造成资料回灌；
  3. APP 查询 `POST /ci/app/employeeCard/query` → `0000`，`cardStatus=4`，`employeeName`/`idCardNo`/`company`/`center`/`department`/`position`/`photoUrl` **全部 null**；
  4. 再次查询数据库，`UPDATE_TMS` 仍为 `18:58:26.594597`，确认查询未触发任何 update/ACC 回查。
- 回滚：`cloudos deploy account .../itp/account:2.0.89 --app-version 2.0.89 --yes`。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-046`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的 traceId / 状态取值 / 表列名均标注来源（2026-11-02 实测日志、代码走查）
- 偏差说明：无——缺陷为实测复现，非推测。
