# AGM 设备域测试（SLE）· INDEX

原始资料：`docs/testing/ITP测试案例 - 可验证20260904-问题.xlsx` 的 **ITP-SLE** sheet（**有效行只有 4 行**，`ACLC_Device_Test_001` ~ `004`，其余 187 行全空）。该 xlsx **只读，NEVER 修改**。

表头是 `序号 | 一级 | 二级 | 三级 | 测试步骤 | 预期结果 | 测试结果 | 备注`（与 APP sheet **不同**：没有「维护人」列，且「一级」列全空）。二级模块只在第 1 行写了「AGM模块」，后 3 行留空 —— 属同一模块下的续行。

建库日期：2026-09-22 ｜ 依据：仓库工作树（SVN，非 git）

> **原表这 4 行的「测试结果」列都是 `✅ 通过（√）`**，备注列全空。本库**如实保留该结果**（同事口径），但本库把预期重写成了代码可验证的断言 —— 其中 **3 条与原预期不等价**（心跳无落库、票卡状态无黑名单字段、200ms 无基线）。因此本库口径下一律记为「**需按新断言重跑**」，**NEVER 把原表的 ✅ 直接当成本库断言已通过**。

---

## 页面

- [01-AGM设备域用例](01-AGM设备域用例.md) — 共同前置（真实入口 / 类级前缀 / 端点全清单 / curl 通用骨架）+ `ACLC_Device_Test_001`~`004` 四条用例（**「结果」行已按 2026-09-22 实测回填**）
- [02-阻塞项与缺陷候选](02-阻塞项与缺陷候选.md) — C1~C13 + 执行前环境前置清单（**C4 已补实证证据；C11 字段裁剪 / C12 UUID-非-404 判据 / C13 `8004` 码值为 2026-09-22 新增**）
- [03-执行记录-2026-09-22](03-执行记录-2026-09-22.md) — **本轮 12 条探针逐条原文**（请求 / 逐字应答 / 断言映射 / 结论）+ 覆盖度小结

三页：源表只有 4 条，且全落在同一个 Controller（`FepAgmController`，151 行、4 个方法）；`03` 是执行记录，**只增不改**。

---

## 落点与入口（一句话版，细节在 `01`）

| 项 | 值 |
|---|---|
| 落点模块 | `fep-dev-server`（闸机 AGM 前置，**无 DB、无 mapper**） |
| 类级路径 | `@RequestMapping("/ci/agm")`（`fep-dev-server/.../controller/FepAgmController.java:30`） |
| 直打 NodePort | `http://172.20.211.23:<fep-dev NodePort 待现查>/ci/agm/<端点>` |
| 经网关 | `http://58.56.166.170:48000/itpagm/ci/agm/<端点>`，**rewrite 需现查 VS** |
| 已实现端点 | IF1A-01 `notiVerifyResult`（`:46`）、IF1A-02 `requestSynKeyList`（`:70`）、IF1A-04 `requestQrCodeStatus`（`:92`）、IF1A-03 `deviceHeartbeat` / `notiDeviceHeard`（`:112`，双别名） |

**⚠️ 最常踩的一条：类级前缀是 `/ci/agm`，不是 `/itpagm/ci/agm`。** `/itpagm/` 只在经入向网关时出现。

**落点判断的硬约束**：设备域与 APP 域入向流量当前落在哪个应用，**MUST 现查** `kubectl get vs fep-app-vr -n itp -o yaml` + `fep-app` Deployment 的 `service.collectPay.url` env。**本库任何页面都不写死结论。**（TVM `/itptvm/` 与 BOM `/itpbom/` 两条的 `rewrite.uri` **带前缀、不是 `/`**，照抄会 404。）

---

## 执行口径（用户已明确）

**真机与设备动作一律用 curl 直打端点、以模拟设备上送报文替代。** 4 条用例都写成三段：

1. **curl 骨架** —— `application/x-www-form-urlencoded`，公共参数 `sign`/`charset`/`format`/`timestamp`/`deviceId`/`signType` + `bizData`（JSON）。NodePort 一律写「**待现查 `kubectl get svc -n itp`**」。
2. **DB 核对 SQL** —— 库 `AFCITPDB`，表名列名取自 mapper XML。注意 **`fep-dev-server` 自己不落库**，落库都在下游（`ticket-server` / `gate-txn-pay-server` / `key-server`）。
3. **日志判据** —— 服务名 + 关键字，走 `scripts/klog.sh`。`fep-dev-server` **不在 tracing 名单**，`traceId` 列恒为空。

每条显式标注 **「模拟上送 ≠ 真机链路」** 并列出**真机才能覆盖的部分** —— 设备域这批用例里这部分占比尤其高：**闸门物理开合、码体本地解签验签、闸机本地黑名单表、密钥新旧过渡切换、心跳周期与掉线** 全都在闸机内，curl 一个也覆盖不到。

---

## 4 条可执行性一览

| 编号 | 三级模块 | 接口 | 分类 | 与原表预期的差异 |
|---|---|---|---|---|
| ACLC_Device_Test_001 | AGM检票通知 | IF1A-01 `POST /ci/agm/notiVerifyResult` | 可 curl 模拟（步骤 1 扫码 / 4 开闸必须真机） | ⚠️ 原表写成「ITP 校验后 AGM 再开闸」，代码里是**事后通知、无同步授权接口**（C1）；「用户收到乘车记录通知」只对**多日计次票**存在且**默认关**；撞 C2（`trxType=03`）、C3（`LAST_HANDLE_DATE_TIME` 假值） |
| ACLC_Device_Test_002 | AGM查询票卡状态 | IF1A-04 `POST /ci/agm/requestQrCodeStatus` | 接口可 curl 模拟 | ⚠️ **「黑名单」断言不成立**（响应只有 5 个字段、无黑名单位，C4）；**「<200ms」需另立压测口径**（C6）；「AGM 控制闸机」必须真机 |
| ACLC_Device_Test_003 | AGM密钥同步 | IF1A-02 `POST /ci/agm/requestSynKeyList` | 可 curl 模拟（造「有更新」需降 `keyVer`） | ⚠️ **方向相反**：代码是**闸机拉取**、不是 ITP 推送（C7）；「AGM 用新密钥验签」「新旧过渡无感」必须真机 |
| ACLC_Device_Test_004 | AGM设备心跳 | IF1A-03 `POST /ci/agm/deviceHeartbeat`、`/ci/agm/notiDeviceHeard` | 接口可 curl 模拟 | ⚠️ **预期 1「状态更新为在线」与预期 2「心跳时间记录正确」均未找到实现**（C8）：该方法不解析 `bizData`、不落库、不 RPC、**恒返成功** |

**本库不产出实现率 / 进度百分比**（`AGENTS.md` §5.3）。上表是可执行性分类，不是完成度。

### 本轮实测结论（2026-09-22，逐条原文见 [[03-执行记录-2026-09-22]]）

| 编号 | 本轮实打 | 结论 |
|---|---|---|
| ACLC_Device_Test_001 | **未打**（IF1A-01 是状态变更型接口，会推进乘车码状态机并产生扣费单；本轮只做只读探针） | 仍为「需按新断言重跑」，**待写操作批次** |
| ACLC_Device_Test_002 | **已打 4 条探针**（正常卡 / 空 `bizData` / 未注册卡 / 直连 ticket-server 对照） | 断言 1 ✅ 通过；**断言 2（黑名单）已实证不成立**（C4）；断言 3 必须真机；断言 4（<200ms）**无基线、不判定**（C6）；断言 5-6 ✅ 通过。**新增 C11（字段裁剪）、C13（`8004`）** |
| ACLC_Device_Test_003 | **已打 1 条**（空 `keyCurVerList` 负向） | 参数校验断言 ✅ 通过（返 `1001 keyCurVerList不能为空`）；「有更新」正向分支**需降 `keyVer` 造数据，本轮未做**；方向相反那条（C7）不变 |
| ACLC_Device_Test_004 | **已打 3 条**（完整报文 / 别名 `notiDeviceHeard` / 不存在设备号 `99999999`） | **断言 1「状态更新为在线」与断言 2「心跳时间记录正确」已实证不成立**（三种输入全返 `0000`，C8 硬证据已落）；断言 4（双别名一致）✅；断言 5（恒返成功）✅ |

**原表那 4 个 ✅ 的判读已被本轮证实是「只验证了接口返回成功」** —— 心跳接口对任何输入都返回成功。**NEVER 据此把本库断言记成通过。**


---

## 与已有测试目录的重叠指向

重叠条目**只留指向、NEVER 重复写**：

| 本库内容 | 重叠目录 | 指向的具体页面 | 分工 |
|---|---|---|---|
| IF1A-01 的乘车码状态推进、`QRCODE_STATUS` / `QRCODE_TXN_DETAIL` 断言、`CODE_STATUS` 取值 | `docs/testing/user-card/` | `00-术语与状态字典.md`（`CODE_STATUS` 10 个取值 + `DEL_YN` 反直觉语义 + 默认值两处不一致）、`01-二维码电子票.md`（过闸扩展 `Test_001o~001t`：码体渠道位回归 / 出站扣费收敛 / 同站进出 / 折扣字段 / APP 轮询 / `0B` 钱包折扣）、`05-阻塞项与缺陷候选.md`（B14~B21） | **过闸链路的真实时序基线与实测样本在那边**（2026-09-09 19:37 样本 `00522949` / `0426090942000017`、设备 `02450604`、订单 `GT20260909193759016000017`）；本库只写「设备侧怎么上送 + 断言落在哪列」 |
| IF1A-01 出站扣费的 `GATE_TXN_PAY` 断言 | `docs/testing/app/` | `00-报文与接口字典.md` §四（`GATE_TXN_PAY` 全列清单）、`03-异常与行程账务.md`（`APP_test_010` 的三条重试任务 220 / 255 / 345） | 扣费与补款的**任务侧口径在 app 目录**；本库只写「IF1A-01 返 `0000` ≠ 扣费成功、MUST 复查 `DEBIT_STATUS`」 |
| TVM / BOM 设备域（`/itptvm/`、`/itpbom/`） | `docs/testing/face-pay/` + `docs/testing/bom-oneside/` | `face-pay/00-链路事实与配置字典.md`、`01-TVM.md`、`02-BOM.md`、`03-STT.md`、`04-阻塞项与缺陷候选.md`（`ITP_Payment_012~020`）；`bom-oneside/00-链路事实与状态机.md`、`01-用例.md`（`ITP_ETicket_Test_020~023`）、`02-阻塞项与缺陷候选.md` | **本库只覆盖 AGM 闸机域**。TVM / BOM 虽同属「设备域」，但落点是 `face-pay-server`、表是 `F2F_*`，**全部指向那两个目录** |
| ACC 侧参数与逻辑卡号（设备用的参数版本、卡号池） | `docs/testing/itp-acc/` | `00-链路事实与配置字典.md`、`01-参数管理.md`、`02-逻辑卡号获取.md`、`03-阻塞项与缺陷候选.md`（`ACLC_ACC_Test_001~003`） | **编号前缀同为 `ACLC_`**，容易混 —— 那边是 `ACLC_ACC_Test_*`（ITP↔ACC），本库是 `ACLC_Device_Test_*`（ITP↔AGM）。**NEVER 互相引用编号** |
| 黑名单（C4 的对照） | `docs/testing/user-card/` | `03-用户信息与状态.md`（`Test_009`）、`05-阻塞项与缺陷候选.md`（A3「APP 查了、服务端不拦」） | 黑名单的裁决**在那边**；本库只用来证明 IF1A-04 响应里没有黑名单位 |
| 密钥同步的 `key-server` 侧（IF8A-02） | `docs/testing/app/` | `00-报文与接口字典.md`（IF8A-02 `requestKeyList` → `key-server` `controller/KeyController.java:40`） | **IF8A-02 是 APP 拉密钥、IF1A-02 是闸机拉密钥，落在 `KeyController` 的两个不同方法（`:40` vs `:49`），NEVER 混用** |

`docs/testing/app/`（本次同批新建）与本库的分工：**APP 域 19 条在那边**；两边凡涉及「闸机上送 IF1A-01」的 curl 骨架**同源**，改一处 MUST 同步另一处。

---

## 编写约束

- 只记录代码中确实存在的事实，均带 `文件:行号`；找不到的写「**未找到**」，**不推断、不编造 URL / 表名 / 列名**。
- 原编号 `ACLC_Device_Test_00x` 全部保留；本库自拟的补充场景用后缀 `a`/`b`/`c` 并注明「本库自拟」（**当前尚无自拟条目** —— 4 条原用例本身已含大量子场景，先把这些跑实再扩）。
- 冲突显式标 ⚠️，**不擅自裁决**，统一收敛到 `02`。
- **性能类断言（如「<200ms」）NEVER 用单次 curl 的 `time_total` 当通过判据** —— 必须另立压测口径，理由见 `02` 的 C6。
- **NEVER 在文档、日志或对话里回显任何密钥值**（IF1A-02 相关）；核对只写版本号、条数、表名、列名。
- 执行结果回填在各用例的「**结果**」行，**不改写链路事实段落**。
- 行号绑定建库时的工作树。`FepAgmController` 目前 151 行 / 4 个方法，**新增端点会让后面所有行号漂移**，代码变更后需重新核对。

---

## 更新记录

- **2026-09-22 首次建立**。来源：ITP-SLE sheet 4 行 + `FepAgmController`（151 行全文）/ `QrCodeStatusHandler`（全文）/ `KeySyncHandler`（前 70 行）实读。产出 3 个文件（本索引 + 1 页用例 + 阻塞项）。
  主要发现：①**IF1A-03 设备心跳是个空壳** —— 不解析 `bizData`、不落库、不 RPC、恒返成功，因此原表「设备状态更新为在线」「心跳时间记录正确」两条预期**均无实现**，而原表该行标的是 ✅ 通过；②**IF1A-04 的响应只有 5 个字段、没有黑名单位**（`QrCodeStatusHandler.java:57`~`:62`），原表「返回黑名单状态」**断言不成立**；③**IF1A-02 是闸机拉取、不是 ITP 推送**，与原表步骤描述方向相反；④**IF1A-01 是事后通知**，代码里不存在「ITP 校验后闸机据此开闸」的同步授权接口；⑤「查询响应时间<200ms」**当前无性能基线**，且该链路跨 2 服务 + 1 次 DB、冷启动实测 4850ms vs 热态 36~67ms、虚拟线程 pin 会让尾延迟远离均值 —— **单次 curl 验不了**；⑥`fep-dev-server` 不在 tracing 名单，设备域链路会在接入层断 traceId；⑦设备域 4 个端点**全部无验签**，而 IF1A-01 是状态变更型接口。

- **2026-09-22 第一轮实测回填**。新增 `03-执行记录-2026-09-22.md`（12 条探针 D1~D12，逐条含请求 / 逐字应答 / 断言映射 / 结论）；`01` 四条用例的「**结果**」行按实测改写 + 头部补「UUID 非 404」告示（**链路事实段落一行未动**）；`02` 补 C4 实证证据、新增 C11 / C12 / C13。
  **本轮新事实**：①**判 `fep-dev-server` 端点存在性 NEVER 用 HTTP 404** —— 打错端点名返 **HTTP 200 + UUID `retCode`**（`/ci/agm/deviceHeartbeatXX` → `retCode=3e7299a4-...`），此前那条「404 = 不存在」判据**只适用于 APP 第三方侧的 `/app/receiveXxx`**，两者 NEVER 混用（C12）；②**心跳对不存在的设备号 `99999999`、对空 `bizData` 一律返 `0000 成功`** —— C8「恒返成功」由代码推断升级为实证（C8 / 断言 5）；③**IF1A-04 在 `fep-dev` 这一跳把下游 9 个字段裁成 6 个**（`gateInStation` / `gateInTime` / `lastTxnStation` / `txnSeq` 丢失），排查过闸问题 MUST 直连 `ticket-server`（C11，**不是缺陷判定**）；④**`8004` 在设备域读作「未注册用户」、在 APP 域读作「员工码信息不存在」** —— 同码不同义，断言 MUST 同时钉 `retCode` + `retMsg`（C13）；⑤热态实测 20 / 20 / 17ms、冷态首次 1152ms，**仍不作为 C6 那条 SLA 的通过判据**。
  **本轮未做**：IF1A-01 检票通知（状态变更型，留给写操作批次）、IF1A-02 的「有更新」正向分支（需降 `keyVer` 造数据）。

