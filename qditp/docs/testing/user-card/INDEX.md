# 用户卡管理测试 · INDEX

原始资料：用户提供的《用户卡管理测试手册》（ITP测试案例 - 可验证20260904）。
本知识库把该手册按代码实测结果重写：保留全部用例编号，预期结果改为**当前代码可验证的断言**，与代码现状冲突处标 ⚠️ 并汇总到 `05`。

最后更新：2026-09-09 ｜ 依据代码：`main` 工作树（销户链路已上线；开卡卡号来源已改为 card-pool-server 预占；生码链路 IF8A-03 / IF8D-03 已按 17:41~17:54 实测补入，行号已偏离 `8e8b7bd`）

## 页面

- [00-术语与状态字典](00-术语与状态字典.md) — 乘车码状态取值、`DEL_YN=1` 表示有效、`OPER_TYPE` 0/1/2/3、`COMPANION_FLAG`、员工卡 `cardStatus`
- [01-二维码电子票](01-二维码电子票.md) — Test_001~004 + 开卡扩展 Test_001a~001g + **生码扩展 Test_001h~001n** + **过闸扩展 Test_001o~001s** + 销户扩展 Test_002a~002f / 003a / 003b
- [02-同行码](02-同行码.md) — Test_005~006（开卡 / 关闭）
- [03-用户信息与状态](03-用户信息与状态.md) — Test_008~009（换绑手机 / 黑名单）
- [04-电子员工卡](04-电子员工卡.md) — Test_010~013（开卡 / 销户 / 停启用 / 手机号变更）
- [05-阻塞项与缺陷候选](05-阻塞项与缺陷候选.md) — 冲突汇总，按影响排序

## 用例可执行性一览

- Test_001 开卡：**可测，已通过**（2026-09-09 实测 `00522949`）
- Test_001a 卡号来源（卡池预占 → 确认）：可测，已通过
- Test_001b 预占确认失败的一致性缺口：观察点，未执行
- Test_001c 乘车码先建、本地库后落（顺序观察点）：未执行
- Test_001d 重复开户拦截：可测，未执行
- Test_001e 支付宝签约 + 通道绑定（IF8A-16/23/24）：可测，已通过（两个 ⚠️ 观察点见 `01`）
- Test_001f 开卡后过闸：**已通过**（2026-09-09 19:37 实测，进出站 + 扣费全链路收敛）
- Test_001g IF8A-24 重复调用幂等：可测，已通过
- Test_001h 生码-在线码 IF8A-03：可测，已通过
- Test_001i 生码-离线码 IF8D-03（entry + exit 双码）：可测，已通过
- Test_001j 连续拉码 `txnSeq` 不递增：已观察到，规格符合性待甲方确认
- Test_001k 码体唯一性 + `timeStamp` / `handleDate` 语义：已观察到，语义待确认
- Test_001l 生码前黑名单查询 IF8A-73：正常路径已通过；命中黑名单路径未执行
- Test_001m 冷启动首次拉码 4850ms：已观察到，预热 / 超时策略待确认
- Test_001n 日志 ERROR ≠ 业务失败：已确认（排查约定）
- Test_001o 过闸后码体渠道位合法（B14 回归）：已通过
- Test_001p 出站扣费全链路收敛：已通过（支付中心 6.9s，异步收敛）
- Test_001q 同站进出票价与站名：已通过（附 para 重复查询观察点）
- Test_001r 折扣计算字段未参与：原因已定位（`0B` 渠道闸门），非缺陷
- Test_001s APP 支付结果轮询：已通过（待补缴列表返回 0 条）
- Test_001t 钱包渠道 `0B` 折扣链路：**未执行、无样本**（见 `05` B20）
- Test_002 正常注销：**可测，已通过**（IF8A-42 已上线，A1 关闭）
- Test_002a 通道先解绑的反序场景：可测，已通过
- Test_002b 销户幂等：可测
- Test_002c 销户参数校验：可测（归属校验缺口见 B7）
- Test_002d 解约回调 `cardType` 为 null 兜底：回归用例，已通过
- Test_002e IF8A-75 兼容路径可达：可测，已通过
- Test_002f 归档时点（多渠道逐个解绑）：可测，已通过
- Test_003 未结清余额：**可测**（账户维度 `8023` 已实现）
- Test_003a IF8A-35 查询失败 fail-closed `8024`：**未实测，用户 2026-09-11 裁决暂缓**（两条非破坏性路子已证走不通，操作口径见 `01` 该节）
- Test_003b 关闭欠费校验开关：可测（配置项验证）
- Test_004 进行中行程：✅ 已裁决不校验（A1a 关闭，ADR-D20），现状即预期
- Test_005 同行码开卡：可测，另有重复开卡观察点（B2）
- Test_006 同行码关闭：⚠️ 部分阻塞 — 无单卡关闭入口（A2）
- Test_007：原手册缺号，不补造
- Test_008 换绑手机：主流程可测；「原号收到通知」⚠️ 不可测（A4）
- Test_009 黑名单：查询可测；「无法乘车」⚠️ 不成立（A3）
- Test_010 员工卡开卡：可测（原备注「暂不支持」已过期）
- Test_011 员工卡销户：⚠️ 阻塞 — `cardStatus` 语义未定义
- Test_012 停用/启用/同步：可测，重点验状态机是否闭环（B3）
- Test_013 员工卡手机号变更：可测

## 编写约束

- 只记录代码中确实存在的事实，均带 `文件:行号`；找不到的写「未找到」，不推断。
- 矛盾显式标 ⚠️，不擅自裁决，统一收敛到 `05`。
- 执行结果回填在各用例的「结果」行，**不改写链路事实段落**；链路事实与代码不符时（如 A1）**MUST** 整段改写并注明作废日期。
- 代码变更后需重新核对行号：**销户相关内容基线为 2026-09-09 工作树，其余内容仍绑 `8e8b7bd`**。

## 更新记录

- 2026-09-07 首次建立。来源：用户提供的测试手册 + 仓库代码实测。产出 6 页 + 本索引。
  主要发现：注销全链路无实现（Test_002/003/004）、黑名单只查不拦（Test_009）、同行码无关闭实现（Test_006）、换绑手机无验签无验证码（Test_008 + 安全风险 B1）、员工卡接口已存在但状态语义缺定义（Test_010~012）。
- 2026-09-09 销户链路补全。IF8A-42 已实现并端到端验证（`fep-app-server` 2.0.74 / `account-server` 2.0.42 / `pay-sign-server` 2.0.72）。
  改动：①`01` 的「注销」链路事实段整段重写；②Test_002 由阻塞改为已通过，Test_003 补 `8023` 口径；③新增 8 条销户扩展用例（002a~002f、003a、003b）与「销户验证 SQL 速查」；④`05` 关闭 A1、拆出 A1a（进行中行程校验缺失）、修订 A2、新增 B7（销户无验签无归属校验）/ B8（`OPER_TYPE=1` 只记默认通道）/ B9（归档物理删除不可回溯）。
  实测样本：`00522946`（反序归档）、`00522947`（3 卡 2 渠道）、`00522948`（单渠道正序）；修复项包含 `/userData/unbindAgreement` 未注册、`cardType` NPE、归档时点缺口三处。
- 2026-09-09 开卡链路实测补全。**实测时的线上版本：`account-server` 2.0.43（pod 16:04:10 CST 启动）、`card-pool-server` 1.0.14、`fep-app-server` 2.0.74、`pay-sign-server` 2.0.72**。⚠️ 注意两处版本漂移：①仓库 `account-server/pom.xml` 已是 **2.0.44**，集群仍为 2.0.43，即本地这一版**未部署**，本轮证据不覆盖 2.0.44 的改动；②`card-pool-server` 已于 17:34:49 CST 换成 **1.0.15**，晚于本轮开卡实测，Test_001a 的卡池侧结论需在 1.0.15 上复测。
  样本 `00522949`（17:10:27，account traceId `8516f5fd17f9cfcb4cce65cb923d8821`）：卡号 `0426090942000017`、`cardType 02 → 0441`、`USER_ITP_REG_INFO` ID=249 `DEL_YN=1`、`QRCODE_STATUS.CODE_STATUS=03`、`LOGIC_CARD_POOL_CARD` `STATUS=ASSIGNED` / `BUSINESS_ID=00522949:0441` / `BATCH_NO=100042`、`APP_USER_PAY_CHANNEL=ACTIVE`、`APP_PAY_SIGN_INFO=SIGNED`。
  改动：①`01` 追加「开卡链路事实基线变更」段（**append-only，不改写 `8e8b7bd` 原段**），记录卡号来源由本地生成改为 card-pool-server 两阶段预占（`/internal/card-pools/reservations` → `/confirm`，`expireTime` 10 分钟）；②Test_001 按代码现状重写预期并回填 ☑；③新增 Test_001a~001g（001a/001e/001g 已通过，001b/001c/001d/001f 未执行）。
  ⚠️ 新增观察点（待裁决，见 `01` 与 `05`）：`APP_PAY_SIGN_INFO.CARD_ID` / `CARD_TYPE` 实测为 NULL（签约未回写卡维度）；被放弃的签约请求 `requestSignSeq=0052294901523912` 悬挂无清理；预占已确认但本地插入失败时无补偿；乘车码先建可能产生孤儿档。
- 2026-09-09 生码链路实测补全（IF8A-03 在线码 / IF8D-03 离线码 / IF8A-73 黑名单前置查询）。样本仍是 `00522949` / `0426090942000017`，共 7 次拉码：17:40:04、17:41:58（03+8D 成对）、17:42:10、17:48:58（03+8D 成对）、17:54:46。
  **实测时的线上环境**：⚠️ 期间发生过重启，17:54 那次是冷启动样本——`ticket-server` pod `ticket-server-856f8898bf-8flvn` 17:52:24 启动、`DispatcherServlet` 17:54:48.481 才懒初始化；`fep-app` 的 `DispatcherServlet` 17:54:45.280 初始化。因此 17:41 / 17:48 是热态样本（36~67ms），17:54 是冷态样本（4850ms），**两组数据不可混用**。`card-pool-server` 17:34:49 已换 1.0.15，但生码链路不经卡池，本轮结论不受影响。
  改动：①`01` 追加「生码链路事实」段（append-only），含码体 11 段字段顺序与实测拆解；②新增 Test_001h~001n 七条用例（001h/001i/001l/001n 已通过或已确认，001j/001k/001m 为已观察到但规格符合性待确认）；③补回 `01` 中缺失的 `### Test_003 用户注销-未结清余额` 小节标题；④`05` 修订 A3（黑名单不是「零调用」而是「APP 查了、服务端不拦」）、新增 B14~B17。
  ⚠️ 新增观察点：`issueChannelCode=5412` 被 `normalizeHex(...,2,...)` 取右 2 位落成 `12`；`handleDate` 位落「当前时间」而非上次交易时间；离线码 `txnSeq` 恒为 `原值+1` 且不回写库；ticket-server 实际端口是 **9100**（文档记 9103）；blacklist-server 每 30s 刷 OTel 导出失败 ERROR。
- 2026-09-09 晚 B14 闭环 + 新增 B18。
  **B14 已修完并验证**：`CARD_ISSUE_CODE` 收窄为发行渠道编码（仅 `0001`/`0007`），新增 `USER_ITP_REG_INFO.ISSUE_ORG_CODE` 存 APP 上送的 4 位机构码原值；新建 `CardIssueOrgEnum` 承载字典与映射；`warnIfTruncated` 收口为「截断结果非法才告警」。生产库已执行（备份表 `USER_ITP_REG_INFO_BAK20260909` 24 行 → 加列 → 搬原值 24 行 → 归一 24 行，迁移后无非法值），脚本与执行记录见 `scripts/20260909_issue_org_code_migration.sql`。已部署 `itp/account-server:2.0.45` + `itp/industry-data-server:2.0.15`。19:26:54 / 19:27:06 两轮离线码验证：码体渠道位由 `12` 变 `01`，日志窗口无 WARN 无 ERROR。
  ⚠️ 两处口径订正：①迁移实际是 **24 行不是 23 行**，且 `USER_ITP_REG_INFO` 里**没有 `0007` 行、没有 NULL**（只有 `0008`×11 与 `5412`×13），支付宝 `0007` 用户在 `ALIPAY_USER_INFO`；②集群此前跑的是 industry-data-server **2.0.13**，2.0.14 从未上线。
  `QRCODE_TXN_DETAIL` 已落的 82 条 `12`/`08` 按裁决**不改**，仅记明受影响区间 2026-08-07 ~ 2026-09-09。
  **新增 B18（未处理）**：IF8A-29 查询上次行程，未过闸的新卡被返回成站名 `FFFF`、时间全零的假行程（`latestDetail == null` 分支把 `QRCODE_STATUS` 占位值原样当行程 + `resolveStationName` 站名查不到时回退返回编码），APP 因此提示「记录行程异常」。后端 `retCode=0000` 无报错。待 APP 侧确认判异常的字段后再改。
- 2026-09-09 19:37 过闸链路实测补全（IF1A-01 进站 + 出站 + 出站扣费 + 支付中心收敛）。样本 `00522949` / `0426090942000017`，设备 `02450604`，进出站同为 `0245`（合川路），票价 200 分，订单 `GT20260909193759016000017`。
  改动：①`01` 追加「过闸链路事实」段（时序表 + 四张表落库结果，append-only）；②`Test_001f` 由「未执行」改为**已通过**并补证据；③新增 `Test_001o~001s` 五条（001o 是 B14 回归用例，001p 扣费收敛，001q 同站进出，001r 折扣字段待确认，001s APP 轮询）；④`05` 的 B15 由「语义待确认」升级为**已确认有实际影响**、新增 B19。
  ✅ **B14 下游闭环**：闸机上报 `issueChannelCode=01`，`QRCODE_TXN_DETAIL` 两行与 `GATE_TXN_PAY` 一行全是 `01`（修复前会是 `12`）。全链路 `DEBIT_STATUS=SUCCESS`、`PAY_STATUS=SUCCESS`、`REQUEST_COUNT=1`。
  ⚠️ **B15 升级的硬证据**：首次进站 AGM 上报 `lastHandleDateTime=20260909192654` —— 正是那轮拉码时刻，而该卡此前从未过闸。闸机确实把码体 `handleDate` 位当「上次交易时间」读，我方填的是生码时刻 `now()`，导致 `QRCODE_TXN_DETAIL` 首程行的 `LAST_HANDLE_DATE_TIME` 指向不存在的交易。
  ⚠️ **新增 B19（未处理）**：`PAY_TXN_DETAIL` 的 `COUPON_AMOUNT=200` 但本次无优惠，且 `CASH_AMOUNT+COUPON_AMOUNT=400≠TOTAL_AMOUNT=200`；`PAY_TIME=20260909193738` 比我方 `FIRST_REQUEST_TIME=19:37:59` 早 21 秒。已核实这三列在 `PaySignWorkflow:822-827` 是原样落支付中心回调值，我方不计算不校验 —— 需与支付中心核对口径，**在确认前不要加断言式校验、也不要"顺手修正"落库值**。
  ⚠️ 其它观察点（非缺陷）：`GateTransactionHandler.requestGateTxnPay` 对同一站码 `0245` 调了两次 `requestStationLineInfo`，未去重；支付中心响应耗时 6.9s（异步收敛，不阻塞闸机）；`GATE_TXN_PAY` 折扣相关 7 列全 null。
- 2026-09-09 20:05 上述两个观察点追查到根因，**其中一条修正了当日的误判**。
  **`requestStationLineInfo` 两次调用**：`fillStationNames:493/:501` 两个独立 try 各调一次，`queryStationLineInfo:298-310` 每次直连 para、无缓存无去重。同站进出两个入参值相同故必打两次。**评估可改批量接口**——`/ci/app/requestStationNameBatch` 已存在且 ticket-server 在用（`ParaClient:134`、`AppParaQueryMapper.xml:106`），改后**同站与异站都能降到 1 次**，只动 `fep-dev-server` 一个方法，para/model/rpc 都不用改。未实施。
  **折扣 7 列全 null**：`GateTxnPayServiceImpl.calculateWalletDiscount:643-646` 第一行 `if (!"0B".equals(paymentVendor)) return;`，本次 `03` 直接 return，故是「全 null」而非 `SKIPPED`（SKIPPED 只在方法体内部分支写）。折扣**不由报文携带**，是 gate-txn-pay 自算（para 原价 + 钱包累计 + `DISCOUNT_LEVEL` 档位）。→ Test_001r 改判为「非缺陷」，新增 **B20**（`0B` 分支自 2026-08-26 修复后从未有样本，主路径无证据）+ Test_001t（未执行）。
  ⚠️ **修正一次误判 → 新增 B21**：我先前说「`GateTransactionHandler` 漏了 `payRequest.setDiscountFee(...)`，补上即可」——**错**。`gate-txn-pay:451-452` 确实消费入参并转发 pay-sign，但 **ticket-server 的 IF1A-01 响应从不给这两个字段赋值**（3 处 `setDiscountFee` 全在查询路径，从 `PAY_TXN_DETAIL` 反读填 APP 响应）。折扣流向是**反向**的，扣费方向上这两个字段是死链，补 setter 只会永远传 null。已做：删掉 `GateTransactionHandler` 透传日志里那两个恒 null 字段并就地注释流向，`fep-dev-server` 编译通过；**DTO、gate-txn-pay 转发逻辑一律不动**。
- 2026-09-09 20:1x `fep-dev-server` 站名查询改批量并部署 **2.0.50**（与上条 B21 的日志收口打包成一次构建）。
  改动：`GateTransactionHandler.fillStationNames` 由「两次单查 `requestStationLineInfo`」改为一次 `requestStationNameBatch`（`ParaClient:134` / `AppParaController:115` / `AppParaQueryMapper.xml:106`，接口已存在且 ticket-server 在用），新增私有 `fetchStationNames(String...)` 做 distinct + Map 归集。**同站与异站都从 2 次 HTTP 降到 1 次**，每笔出站交易固定省 1 次跨服务调用 + 1 次 `TBL_PARA_VERSION` 子查询。
  边界（已在代码注释里写明，防后人误改）：①`buildStationInfoMap`（支付宝路径）**仍走单查**，它要 `lineCode` / `lineName`，批量 SQL 不返回线路字段，因此 `queryStationLineInfo` 不能删；②保留「进站码为空即整体返回」的原有语义，未动行为——放开它会让原本 null 的 `EXIT_STATION_NAME` 变有值，属行为变更，需单独确认；③入参 `distinct` 是必要的，否则 foreach 生成 `IN ('0245','0245')` 白占绑定变量。
  构建部署：`itp/fep-dev-server:2.0.50` 已推 Harbor 并 `set image` 滚动完成（从 2.0.49），Pod `fep-dev-server-596df955fc-6m296` `2/2 Running`。
  ⚠️ **回归未做**：这次改动**没有真实过闸样本验证**。下次过闸时按 Test_001q 的回归判据核对：非支付宝路径不再出现 `requestStationLineInfo`、改为一条 `requestStationNameBatch`、两个站名字段与改动前一致、无批量查询失败 WARN。



