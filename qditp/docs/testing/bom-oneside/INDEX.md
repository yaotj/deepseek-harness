# BOM 单边处理测试 · INDEX

原始资料：用户提供的《ITP 电子票管理 - 单边处理（BOM）测试案例》（4 例，ITP_ETicket_Test_020~023）。
处理方式同其他域：保留用例编号，预期结果改为当前代码可验证的断言，冲突处标 ⚠️ 并汇总到 `02`。

最后更新：2026-09-08 ｜ 依据代码：`main` @ `8e8b7bd`

## 页面

- [00-链路事实与状态机](00-链路事实与状态机.md) — 调用链、`adviceOpt` 规则表、执行白名单、超时口径
- [01-用例](01-用例.md) — Test_020~023
- [02-阻塞项与缺陷候选](02-阻塞项与缺陷候选.md) — 冲突汇总

## 最重要的一条：别走错链路

项目里有**两条互不相同的补站链路**：

- **BOM 单边处理**（本域）：IF5A-01 + IF5A-03 → `ticket-server/.../CardDataHandler.java`，参数 `updateType` + `adviceOpt`，`deviceId = operaterId`
- **APP 自助补站**：`POST /ci/app/requestExcessFare` → `ticket-server/.../ExcessFareHandler.java`，参数 `upgradeAreaType`，`deviceId = 站点码 + "36" + "01"`

两条的状态白名单规则、上送字段、计费条件都不同。用例与缺陷单里引用字段名时务必分清。

## 用例可执行性一览

- Test_020 补出站：可测（`adviceOpt=006`）。前置必须是开环态 `04`/`81`/`10` 且 `updateType=00`
- Test_021 补进站：可测（`adviceOpt=018`）。前置是闭环态 `02/05/06/80` 或 `03/08/09/10`；⚠️「验证身份」ITP 无实现
- Test_022 超时补票：⚠️ **阻塞** — ITP 不计算超时费，BOM 链路 `overtimeAmount` 硬编码 `"0"`
- Test_023 未进/出站补票：⚠️ **无独立实现** — `transType=04` 只是收款单字典值、无代码分支；实际等价于 020/021

## 与其他域的关系

`../face-pay/02-BOM.md` 里说「BOM 侧没有任何 `excessFare` 接口」——那句话针对的是 `requestExcessFare` 补票**收费**链路，**不代表 BOM 不能补站**。BOM 补进出站走 IF5A-01/03，链路完整可跑，就是本域的对象。两页需一起看。

## 三条需要产品/甲方确认的事

1. **超时费的收费责任方**是闸机还是 ITP（影响 022 能否收口）
2. **「未进/出站补票」是否有独立计费规则**（影响 023 是缺失还是换名）
3. **`upgradeAreaType` 的 `03`/`04` 是什么**——代码白名单允许这两个值，但全仓无任何注释定义其含义

## 编写约束

- 只记录代码中确实存在的事实，均带 `文件:行号`；找不到写「未找到」，不推断。
- 接口定位用「模块 + URL」。本域涉及的 IF5A-01/03 在 `collect-pay-server` 与 `ticket-server` 各有一份入口，且 ticket-server 侧还有 `/ci/agm` 与 `/ci/app` 两个前缀指向同一 handler。
- 证据在 `ticket-server` 的 `QRCODE_STATUS` / `QRCODE_TXN_DETAIL`，**collect-pay-server 纯透传不落库**。
- 矛盾显式标 ⚠️，收敛到 `02`。行号绑定 `8e8b7bd`。

## 更新记录

- 2026-09-08 首次建立。来源：用户提供的测试案例 + 仓库代码实测（collect-pay-server / ticket-server / fep-dev-server / model）。
  主要发现：BOM 补进出站链路完整（纠正「BOM 无补站」的可能误读）、ITP 不计算超时费（022 阻塞）、`transType=04` 无代码分支（023 无独立实现）、IF5A-03 无鉴权且事务内发 RPC（与 8/26 事故同形态）、存在两份 `QRCodeStatusEnum` 取值数不同、`ExcessFareHandler` 用 `String.contains` 判白名单、`upgradeAreaType` 的 `03`/`04` 含义未定义。
- 2026-09-08 补 `02` 的 D 节：按《技术规范-第9部分》§7.3 与 §5.3 逐条核对。
  **最重要一条（D1）：BOM 章节没有规定「允许的前置状态」**，规格把状态判定整个交给 ITP。因此 `00` 页两张白名单表是实现方自定义的，**测试只能验「行为与代码设计一致」，不能得出「符合需求」**。
  其余：`adviceOpt` 规格内两套编码且「20分付费更新」缺取值（D2）、`adviceOpt` 应答是数组而请求是 String（D3）、`transType` 九个取值只实现 `42` 校验且 `02` 原文是「超时更新」非「超程更新」（D4）、§5.3.1 要求推送 APP_SERVER 未核实（D5）、规格无凭证打印/无 BOM 充值退款/无对账条文（D6）。
  **更正 Test_023**：规格给了 `04 未出站更新处理` 与 `05 无入站更新处理` 两个独立交易类型，属「规格有、代码无分支」，不是「等价于 020/021 的换名」。
  待完成：`bom.payTimeOut`/`bom.payTimeInterval` 与规格「扫码支付 15 秒且不要重试、其余 5 秒重试 3 次」的比对（D7）。
