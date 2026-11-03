# IT-042 员工卡资料变更只落 ITP、不同步 APP

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-042` 一行。

## 1. 背景 / 现象

- 发现时间：2026-11-02 17:07
- 发现渠道：联调测试（ACC 推带资料的员工信息变更）
- 影响范围：`POST /employee_card/update_notify`（ACC → fep-acc → account）；APP 侧员工卡资料的时效性
- 现象描述：ACC 17:07:23 推送变更（`cardNo=13111111111`，`center="测试"`，`photo` 为 Base64 JPEG）→ fep-acc 返回 `0000 员工信息变更通知处理成功`；ITP 本地已更新（`CENTER='测试'`、`PHOTO_URL` 长 88020 字符，查询接口可回读到），但**同一时刻没有任何到 APP（bestonepay）的出向请求**。

## 2. 根因分析

- 代码走查（`EmployeeCardServiceImpl.updateEmployeeInfo`）：
  1. 校验 `cardNo`、按卡号查 `USER_ACC_EMPLOYEE_CARD`，不存在返 `8004`；
  2. 只 set `company/center/department/position/photoUrl` + `updateTms`（**不含 `employeeName` / `idCardNo`**）；
  3. `employeeCardMapper.updateEmployeeInfo` 落库，0 行则返 `8004`；
  4. `recordEvent(CHANGE, ...)` 写日志；
  5. 直接返回 `0000`——**方法体内无任何出网调用**。
- 对照开卡路径：`notify`（`processAppBatch`）会先 `registerToApp(batch)` 把员工卡同步给 APP，再落库；两条路径对 APP 的处置口径不一致。
- 边车佐证：17:07 前后该 Pod 无 `219.146.65.164:443`（APP 侧域名解析地址）连接，最近一次是 16:56（ACC 推启用触发 `registerToApp`）。
- 根因结论：**资料变更链路设计上只维护 ITP 本地真值，不向 APP 推送**。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A. 变更后补调 APP 注册接口 | `updateEmployeeInfo` 落库成功后调 `employeeCardSync`（复用现有出网方法与配置） | 与开卡路径口径一致；APP 资料实时 | 引入一次外部依赖，失败处理需定义（是否回滚？建议不回滚只记 WARN + 日志表留痕） | 中：需确认 APP 该接口是否支持「仅更新资料」语义 |
| B. APP 每次展码实时查 ITP | APP 不存副本，查询即取最新 | 单一真值来源，最干净 | 需 APP 侧改造；展码链路增加一次 RPC | 中 |
| C. 维持现状 | 只在 ITP 更新 | 零改动 | APP 资料长期过期；若 APP 展示员工照片/部门会不一致 | 低（技术）/ 高（业务观感） |

## 4. 决策与结论

- 选定方案：**维持现状（C）——本单判定「非缺陷」，关闭**。
- 依据（2026-11-02 用户确认）：**APP 展码时实时查 ITP**，自身不持有员工卡资料副本。因此 `update_notify` 落库即等同「APP 可见」，不需要额外的 APP 同步调用。
- 决策理由：展示源唯一（ITP 本地表），不存在「APP 侧资料陈旧」的问题；若再补一次 `employeeCardSync` 反而多一个可能失败的外部依赖。
- 影响范围：无代码改动。
- **遗留观察（转出，不属本单）**：资料补全只在本地 `EMPLOYEE_NAME` 为空时触发（一次性），且 `update_notify` 的字段范围不含 `employeeName` / `idCardNo`。若 ACC 侧**改名或更正证件号**，ITP 不会自动刷新——需业务确认是否要求姓名变更也能同步，若要求则属新需求（建议「查询时按频率/版本号校验」而非每次全量回查）。

## 5. 验证与回滚

- 验证方式 / 结果（现状，2026-11-02 17:07 实测）：
  - `update_notify` → `0000`；`update USER_ACC_EMPLOYEE_CARD set ... PHOTO_URL=...` 1 行；日志 `CHANGE`/`1`
  - 查询接口回读：`center="测试"`、`photoUrl` 长 88020（Base64 JPEG，未超 250KB 限制）
  - APP 侧：同期无出向请求 → 未同步
- 若采纳 A 后的判据：变更接口返回 `0000` 且边车可见一次到 APP 的出向；APP 侧资料与 ITP 一致；APP 调用失败时 ITP 仍返回 `0000`（本地已生效）并记 WARN。
- 回滚预案：方案 A 为新增调用，`git revert` 即可；无数据迁移。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-042`）
- [x] 已建本处理文档并记录处理过程与决策
- [ ] 处理后已回填索引「状态 / 下一步 / 更新」（待 APP 侧确认后回填）
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的 traceId / 字段名 / 长度值均标注来源（2026-11-02 实测日志与接口回读、代码走查）
- 偏差说明：无——现象与代码一致，结论停在「待确认」而不擅自定性为缺陷。
