# IT-046 测试库 `USER_ITP_REG_INFO.THIRD_USER_ID` 前导零丢失，9 个真实用户拉码一律 8004

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-046` 一行。

## 1. 背景 / 现象

- 发现时间：2026-11-02 17:18
- 发现渠道：用户反馈（手机号 `19037529560` 在测试环境拉码失败）
- 影响范围：测试环境 `USER_ITP_REG_INFO` 中 27 条真实用户记录（9 个账号）全部无法拉码——不是单个手机号的问题
- 现象描述：APP 拉码 `POST /ci/app/requestIndustryData`（IF8A-03）稳定返回
  ```
  {"retCode":"8004","retMsg":"没有账号卡片数据","cardData":null}
  ```
  该手机号在 17:15~17:18 的 3 小时内共 12 次重试，全部同因（`fep-app` 日志 `IF8A-03 请求行业数据` 的 `thirdUserId` 恒为 `00522967`）。

## 2. 根因分析

排查过程（日志 → 库 → 代码三方交叉）：

1. 按手机号检索 VictoriaLogs，得到 `traceId=13927edda792e4525394079e39222d16` 的完整链路：
   `fep-app` 拉码 → `ticket-server` `/internal/ticket/industry/online` → `account` `/queryUserInfo`
   → account 侧 SQL `where THIRD_USER_ID='00522967' and CARD_ID='0426090949000058' and CARD_TYPE='0441' and DEL_YN=1` 命中 `Total: 0`
   → `retCode 8004` 逐层返回，fep-app 原样透传。
2. 查测试库（`172.20.222.3:1521/AFCITPDB`）：该手机号在 `USER_ITP_REG_INFO` 的 `THIRD_USER_ID` 实际是 **`522967`（6 位）**，而 APP 上送的是 `00522967`（8 位，左补两个 0）。等值查询必然 0 行。
3. 同库其他表都是 8 位，可排除「APP 送错格式」：
   | 表 | 该用户 `THIRD_USER_ID` |
   |---|---|
   | `USER_ITP_REG_LOG`（开户流水 5 条） | `00522967` |
   | `APP_USER_PAY_CHANNEL`（支付通道 1 条） | `00522967` |
   | `USER_ITP_REG_INFO`（乘车账户 4 条） | `522967` |
4. 排除「代码写错」：开户落库时主表与流水取的是同一个字段——`AccountRegistrationServiceImpl` 里 `regInfo.setThirdUserId(request.getThirdUserId().trim())`，`RegistrationCommitServiceImpl` 里 `regLog.setThirdUserId(regInfo.getThirdUserId())`，一次事务不可能写出两种格式；且主表 4 条的 `REG_TMS` 与流水 `OPER_DATE_TIME` 秒级完全一致（9-22 15:58:07 / 16:37:20 / 17:19:58 / 17:58:47）。
5. 定位为「主表事后被刷坏」的旁证：主表这 4 条 `USER_ID` 全为 `NULL`，而开户 insert 语句包含 `USER_ID` 列（正常开户不应为空）。
- 根因结论：`USER_ITP_REG_INFO.THIRD_USER_ID` 被一次数据脚本/手工 UPDATE 写成了丢前导零的 6 位形态。全表 1027 行中 8 位的 1000 条全是 10-21 批量造的假号（`009xxxxx` / `13000000xxx`），**真实用户 27 条全是 6 位**，说明这是一次只伤及真实数据的批量改写，读写两侧格式自此不一致。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A. 修数据（`LPAD` 补零到 8 位） | 对 `LENGTH(THIRD_USER_ID)=6` 的 27 行执行 `LPAD(...,8,'0')` | 一次修好 9 个用户；与流水、支付通道、APP 上送三方对齐；零代码风险 | 不防复发 | 低（补零后与已有 8 位行唯一索引冲突数实测 0） |
| B. 查询侧兼容（account 查询前补零） | 在 `queryUserInfo` 等入口对 thirdUserId 规范化 | 兼容历史脏数据 | 等值查询语义被改写，掩盖数据问题；11 处 select 都要跟着改，漏一处就是一个静默 8004 | 高 |
| C. 开户侧加格式校验 + 修数据 | A 的基础上，开户时校验 `thirdUserId` 必须 8 位数字 | 从源头防脏值 | 需改代码并发版；外部渠道若真送 6 位会被拒 | 中 |

## 4. 决策与结论

- 选定方案：**A（已执行）**，B/C 暂不做。
- 决策理由：B 会把「数据错」变成「代码猜」，且读点多、遗漏即静默失败；C 属契约变更，需先确认外部渠道上送口径（APP 稳定送 8 位，但其他渠道未核实），不宜在排障中顺手改。
- 实际执行：
  ```sql
  UPDATE USER_ITP_REG_INFO
     SET THIRD_USER_ID = LPAD(THIRD_USER_ID, 8, '0')
   WHERE LENGTH(THIRD_USER_ID) = 6;   -- 影响 27 行
  ```
- 备份：27 行原值（`ID`/`THIRD_USER_ID`/`CARD_ID`/`CARD_TYPE`/`ISSUE_ORG_CODE`/`DEL_YN`）已导出到 `.workbuddy/tmp/backup-user_itp_reg_info-thirdUserId-20261102.json`。
- 影响范围 / 关联模块：account-server 数据（无代码改动）。
- 附带结论：**「测试环境真实用户 thirdUserId 应为 8 位」这一事实此前没有任何脚本或断言守护**，造数脚本（1000 条 8 位假号）与真实数据（6 位）本身就是两套口径，后续应统一。

## 5. 验证与回滚

- 验证方式 / 结果：
  1. 长度分布：修复前 `6→27 / 8→1000`，修复后 `8→1027`（无 6 位残留）。
  2. 模拟 account 等值查询（`thirdUserId='00522967' + cardId + cardType='0441' + DEL_YN=1`）：修复前 0 行，修复后命中 1 行。
  3. 端到端：`ticket-server` `/internal/ticket/industry/online` 用该用户入参实测，最终返回 `{"retCode":"0000","retMsg":"成功","cardData":"0007FAD...9A7C41"}`（数据修复后先暴露 IT-045 同类配置缺陷，该缺陷修完才拿到 0000）。
  4. 9 个账号的卡记录数已按 8 位复核，均可等值命中（`00522961` 1 / `00522967` 4 / `00522971` 1 / `00522976` 15 / `00522977` 2 / `00522980` 2 / `00522982` 1 / `00522983` 1）。
- 回滚预案：按备份文件的原始值逐行 `UPDATE ... WHERE ID=<原 ID>` 恢复为 6 位（回滚后功能回到 8004，不会更差）。
- 遗留待查：那条把真实数据写成 6 位的脚本/操作来源未定位（该表无 `UPDATE_TMS`，无审计字段），建议后续禁止对 `USER_ITP_REG_INFO` 手工改 `THIRD_USER_ID`。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-046`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的端口 / 路径 / 类名 / 卡号 / 时间均标注来源（2026-11-02 实测日志、库内实查、代码走查）
- 偏差说明：改的是测试库数据而非代码——本问题定性为数据一致性缺陷，代码侧无缺陷；「写坏数据的那次操作」未追到源头，属未闭环项，已在本节与 §5 遗留项显式标注。
