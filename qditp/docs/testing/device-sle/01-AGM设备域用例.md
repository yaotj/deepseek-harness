# 01 · AGM 设备域用例（ACLC_Device_Test_001 ~ 004）

原始资料：`docs/testing/ITP测试案例 - 可验证20260904-问题.xlsx` 的 **ITP-SLE** sheet 前 4 行（其余 187 行全空）。
表头：`序号 | 一级 | 二级 | 三级 | 测试步骤 | 预期结果 | 测试结果 | 备注`。二级模块只在第 1 行写了「AGM模块」，后 3 行留空（属同一模块下的续行）。

> **原表这 4 行的「测试结果」列都是 `✅ 通过（√）`**（同事口径，备注列为空）。本库**如实保留该结果**，但本库把预期重写成了代码可验证的断言，其中有几条与原预期不等价（心跳无落库、票卡状态无黑名单字段、200ms 无基线）—— 因此**本库口径下的结果记为「需按新断言重跑」**，**NEVER 把原表的 ✅ 直接当成本库断言已通过**。

> **2026-09-22 已完成第一轮实测（12 条探针）**，逐条原文见 [[03-执行记录-2026-09-22]]。下面 4 条用例的「**结果**」行已按本轮实测改写，**链路事实段落未动**。
>
> ⚠️ **本轮方法论更正**：判 `fep-dev-server` 侧「端点是否存在」**NEVER 用 HTTP 404** —— 打错端点名返的是 **HTTP 200 + UUID 形态的 `retCode`**（实测 `/ci/agm/deviceHeartbeatXX` → `{"retCode":"3e7299a4-a982-4b80-83f2-5ecf1f5621ea","retMsg":null,"data":null}`，见 `03` 的 D7）。「404 = 端点不存在」那条判据**只适用于 APP（第三方）侧的 `/app/receiveXxx` 出向通知端点**，两者 NEVER 混用，详见 [[02-阻塞项与缺陷候选]] C12。

---

## 共同前置：设备 → ITP 的真实入口

| 项 | 值 | 依据 |
|---|---|---|
| 落点模块 | `fep-dev-server`（闸机 AGM 前置，**无 DB、无 mapper**） | `AGENTS.md` §3.2 该行 |
| 类级路径 | `@RequestMapping("/ci/agm")` | `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/controller/FepAgmController.java:30` |
| 入向网关前缀 | `/itpagm/`（VS `fep-app-vr` 的 8 条前缀之一） | `AGENTS.md` §8 |
| 报文格式 | `application/x-www-form-urlencoded`，`@ModelAttribute ItpCommonFormRequest` | `FepAgmController.java:47` / `:71` / `:93` / `:113` |
| `bizData` 解析 | 私有 `parseBizData`，**Fastjson2 `JSON.parseObject`，解析失败只打 ERROR 并返回 `null` → 统一走 `INVALID_PARAM`** | `FepAgmController.java:126`~`:134` |
| `deviceId` | **不在 `bizData` 里**，从公共参数取；IF1A-01 里再由 Controller 注入进 bizData | `FepAgmController.java:48` / `:61` |
| 验签 | 该 Controller **没有验签代码** | 全文件通读（151 行）未见验签调用 |
| tracing | `fep-dev-server` **不在 tracing 名单**（8 个模块里没有它），`%X{traceId}` 那一列**恒为空** | `AGENTS.md` §2.2.1 |

**⚠️ 最常踩的一条：类级前缀是 `/ci/agm`，不是 `/itpagm/ci/agm`。** `/itpagm/` 只在经网关时出现。直打 NodePort **MUST** 用 `/ci/agm/...`；经网关时 **MUST 先现查 VS 的 `rewrite.uri`** 再拼。

### 端点全清单（该 Controller 只有 4 个方法）

| 编号 | URL | 行号 | 返回 DTO |
|---|---|---|---|
| IF1A-01 闸机检票通知 | `POST /ci/agm/notiVerifyResult` | `:46`（Javadoc `:45`，日志 `:49`） | `NotifyVerifyResultAckDTO` |
| IF1A-02 密钥同步 | `POST /ci/agm/requestSynKeyList` | `:70`（Javadoc `:69`） | `RequestSynKeyListRespDTO` |
| IF1A-04 查询票卡状态 | `POST /ci/agm/requestQrCodeStatus` | `:92`（Javadoc `:91`） | `RequestQrCodeStatusRespDTO` |
| IF1A-03 设备心跳 | `POST /ci/agm/deviceHeartbeat`、`POST /ci/agm/notiDeviceHeard`（**双别名**） | `:112`（Javadoc `:111`） | `DeviceHeartbeatRespDTO` |

**代码中已实现的 IF1A 就是这 4 个**（01/02/03/04）。规格里的 IF1A-06~12（共 7 个，**没有 05**）是数字人民币硬钱包、**全无实现**，且其中 5 个真实归属 `face-pay-server` 的 `/itpbom/ci/bom/**`、**不在闸机前置** —— 详见 [[02-阻塞项与缺陷候选]] C5。

### curl 通用骨架

```bash
# NodePort 待现查：ssh k8s-master "kubectl get svc -n itp"
FEPDEV=172.20.211.23:<fep-dev NodePort 待现查>

curl -s -X POST "http://$FEPDEV/ci/agm/<端点>" \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'charset=UTF-8' \
  --data-urlencode 'format=JSON' \
  --data-urlencode 'signType=00' \
  --data-urlencode 'sign=PLACEHOLDER' \
  --data-urlencode 'timestamp=20260922150000' \
  --data-urlencode 'deviceId=<8 位闸机号>' \
  --data-urlencode 'bizData={...}' \
  -w '\nhttp=%{http_code}\n'

# 经网关（rewrite 需现查）：
# curl -X POST "http://58.56.166.170:48000/itpagm/ci/agm/<端点>" ...
```

**全部 4 条用例都按「模拟上送 ≠ 真机链路」执行**：curl 替代的是「闸机发出的那个 HTTP 请求」，**替代不了闸机本身**。每条末尾单列真机才能覆盖的部分。

---

## ACLC_Device_Test_001 · AGM 检票通知（IF1A-01）

**原表**
- 前置：AGM 设备正常，用户正常过闸
- 步骤：1.用户在AGM扫码/刷卡 / 2.AGM发起检票请求 / 3.ITP校验 / 4.AGM开闸/拒绝 / 5.AGM发送结果给ITP
- 预期：1.检票记录写入数据库 / 2.扣费结果正确通知 / 3.用户收到乘车记录通知 / 4.行程状态更新 / 5.日志完整
- 原表测试结果：**✅ 通过（√）**

**可执行性**：可 curl 模拟。步骤 1 与 4（扫码、开闸）**必须真机**；步骤 2/3/5 就是这一个 HTTP 请求。

> ⚠️ 原表把「AGM 发起检票请求」（步骤 2）与「AGM 发送结果给 ITP」（步骤 5）写成两次交互，但**代码里只有一个端点 `notiVerifyResult`**（「检票**通知**」—— 闸机是**先本地判定并开闸、再把结果通知 ITP**）。**未找到**「ITP 先校验、闸机再据此开闸」的同步授权接口。这是原表步骤描述与代码现状的一处口径差异，见 [[02-阻塞项与缺陷候选]] C1。

**重写后的可验证断言**

| # | 原表预期 | 重写为 | 依据 |
|---|---|---|---|
| 1 | 检票记录写入数据库 | `QRCODE_TXN_DETAIL` 新增 1 行：`CARD_ID` / `TRX_TYPE` / `DEVICE_ID` / `HANDLE_DATE_TIME` / `HANDLE_STATION_CODE` / `HANDLE_RESULT_CODE` / `TICKET_TRANS_SEQ` / `TRX_AMOUNT` 与上送报文一致。**注意 `fep-dev-server` 自己不落库**，落库发生在 `ticket-server` | `fep-dev-server` 无 DB（`AGENTS.md` §3.2）；`ticket-server/src/main/resources/mapper/QRCodeTxnDetailMapper.xml` |
| 2 | 扣费结果正确通知 | 出站笔在 `GATE_TXN_PAY` 新增 1 行并**异步**收敛到 `DEBIT_STATUS='SUCCESS'`。**IF1A-01 返 `0000` 不等于扣费成功**，MUST 隔若干秒复查 | `GATE_TXN_PAY` 列清单见 `docs/testing/app/00-报文与接口字典.md` §四 |
| 3 | 用户收到乘车记录通知 | **多日计次票**扣次成功后会推 §3.63 `/app/receiveCountingTicketTimes`（`times` **恒传 1**，`transSeq` 取上送的 `ticketTransSeq`、**NEVER 取 `QRCODE_STATUS.TXN_SEQ`**）。⚠️ 但该通知**两个键默认关**（`app.notify.counting-ticket-times-url` / `-enabled`），且失败**只打 WARN、不落库不重试 ⇒ 推失败即永久丢**。**普通乘车码没有这条通知**（grep 未命中） | `AGENTS.md` §2.2.2 §3.63；落地类 `ticket-server/.../ticket/notify/CountingTicketTimesNotifier`；失败处置见 `docs/domain/outbox.md` §七① |
| 4 | 行程状态更新 | `QRCODE_STATUS.CODE_STATUS` 按方向推进：进站 → `04`，出站 → `05`（超时出站 → `06`）；`TXN_SEQ` 自增 1、比上送的 `ticketTransSeq` **大 1** | `QRCodeStatusEnum.java:9`；`TXN_SEQ` 与 `ticketTransSeq` 差 1 的实测见 `AGENTS.md` §2.2.2 §3.63 口径② |
| 5 | 日志完整 | `fep-dev-server` 对每笔打 **3 行**：`IF1A-01 闸机检票通知, deviceId=`（`:49`）→ 带 `bizData` 的那行（`:62`）→ `响应 retCode=`（`:65`）。`ticket-server` 侧另打入参 / 响应两行（`controller/ci/agm/TicketAgmController.java:43` / `:63`） | 源码直读 |
| 6 | — | ⚠️ **`trxType=03` 是一条已知缺陷路径**（多日计次票不扣次 + 金额被清零，两头落空） | `AGENTS.md` §2.2.2「超时处理」P0 条 ⇒ [[02-阻塞项与缺陷候选]] C2 |
| 7 | — | ⚠️ **`LAST_HANDLE_DATE_TIME` 可能指向一笔不存在的交易**：闸机把码体 `handleDate` 位当「上次交易时间」读，而我方填的是**生码时刻** `now()`。实测样本：新卡首程上报 `lastHandleDateTime=20260909192654`，正是那轮拉码时刻、该卡此前从未过闸 | `docs/testing/user-card/INDEX.md:91`（B15 已升级为「确认有实际影响」）⇒ [[02-阻塞项与缺陷候选]] C3 |

**执行方式**

① curl（出站；进站把 `trxType` 换成进站码值）

```bash
curl -s -X POST "http://$FEPDEV/ci/agm/notiVerifyResult" \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'charset=UTF-8' --data-urlencode 'format=JSON' \
  --data-urlencode 'signType=00' --data-urlencode 'sign=PLACEHOLDER' \
  --data-urlencode 'timestamp=20260922150000' \
  --data-urlencode 'deviceId=<8 位闸机号>' \
  --data-urlencode 'bizData={"cardId":"<卡号>","itpUserId":"<十六进制用户号>","trxType":"02","handleDateTime":"20260922150000","handleStationCode":"<站码>","trxAmount":200,"ticketTransSeq":1,"issueChannelCode":"01"}' \
  -w '\nhttp=%{http_code}\n'
```

> 字段名 **MUST** 以 `fep-dev-server/.../model/NotifyVerifyResultDeviceReqDTO.java` 为准现查（`FepAgmController.java:54` 的 `parseBizData` 目标类）；`trxType` 取值 **MUST** 现查 `TrxTypeCodeEnum`。**Fastjson2 静默丢弃 DTO 里不存在的字段**，写错字段名不报错、只会取到 `null`。
> `itpUserId` 会被 `DeviceUserIdCodec` 按**十六进制**解成十进制再补位 —— 推出去的值与库里明细不同源，**不是 bug**。
> 负例对照：把 `bizData` 置空应返 `INVALID_PARAM` + `bizData不能为空`（`FepAgmController.java:51`）；传非法 JSON 应返 `INVALID_PARAM` + `bizData格式错误`（`:58`）。**这两条是免造数据就能验的最快回归点。**

② DB 核对（库 `AFCITPDB`）

```sql
SELECT CARD_ID, CARD_TYPE, DEVICE_ID, TRX_TYPE, TRX_AMOUNT, TOTAL_AMOUNT,
       HANDLE_DATE_TIME, HANDLE_STATION_CODE, HANDLE_RESULT_CODE,
       LAST_HANDLE_DATE_TIME, LAST_HANDLE_STATION_CODE, TICKET_TRANS_SEQ
  FROM QRCODE_TXN_DETAIL
 WHERE CARD_ID = '<卡号>'
 ORDER BY HANDLE_DATE_TIME DESC;

SELECT CARD_ID, CODE_STATUS, GATE_STATUS, GATE_IN_STATION, GATE_IN_TIME,
       LAST_TXN_STATION, LAST_TXN_TIME, TXN_SEQ, TRX_AMOUNT, USE_COUNT, UPDATE_TIME
  FROM QRCODE_STATUS
 WHERE CARD_ID = '<卡号>';

SELECT ORDER_NO, DEVICE_ID, TRX_TYPE, IN_STATION, OUT_STATION,
       TRX_AMOUNT, TOTAL_AMOUNT, DEBIT_STATUS, FAILURE_COUNT,
       COUNTING_FLAG, COUNTING_TIMES, TICKET_STATUS, TICKET_TRANS_SEQ,
       OFFLINE_FLAG, DISCOUNT_CALC_STATUS, CREATE_TIME, UPDATE_TIME
  FROM GATE_TXN_PAY
 WHERE CARD_ID = '<卡号>'
 ORDER BY CREATE_TIME DESC;
```

③ 日志判据

```bash
scripts/klog.sh fep-dev-server 'IF1A-01' 300
scripts/klog.sh ticket-server  'IF1A-01' 300
scripts/klog.sh gate-txn-pay-server 'requestPay' 300
```

只有 `gate-txn-pay-server` 在 tracing 名单内；`fep-dev-server` / `ticket-server` 的 `traceId` 列**恒为空**，**按 traceId 检索这两个服务恒 0 条 —— 不是日志丢了**。

**真机才能覆盖的部分**：闸机扫码 / 刷卡的读取、码体本地解签验签与密钥版本匹配、本地黑名单表比对、**闸门物理开合**（这是「开闸/拒绝」的唯一真实判据 —— ITP 的 `retCode` 只表示「通知我收到了」）、脱机判定与断网重传、以及 APP 侧的到账通知展示。

**结果**（2026-09-22 回填，证据见 [[03-执行记录-2026-09-22]] D5 / D6）：
- **负例两条已通过**：不带 `bizData` → `1001 bizData不能为空`（D5）；`bizData=notjson` → `1001 bizData格式错误`（D6）。文案与源码逐字一致。
- **正向路径（断言 1~5）本轮未执行** —— 造真实过闸报文会推进乘车码状态机并产生扣费单，属**写操作，待批次二**。
- 断言 3（§3.63 通知）、断言 6（`trxType=03` 缺陷）、断言 7（`LAST_HANDLE_DATE_TIME` 假值）本轮**均未取证**。
- 原表 ✅ 通过（同事口径）保留，但它覆盖不到本库新增的断言 3 / 6 / 7。

---

## ACLC_Device_Test_002 · AGM 查询票卡状态（IF1A-04）

**原表**
- 前置：AGM 设备正常
- 步骤：1.用户在AGM展示二维码/刷卡 / 2.AGM查询票卡状态 / 3.ITP返回 / 4.AGM控制闸机
- 预期：1.返回票卡状态（正常/已进站/已出站/**黑名单**等） / 2.AGM根据状态正确控制闸机 / 3.**查询响应时间<200ms**
- 原表测试结果：**✅ 通过（√）**

**可执行性**：接口可 curl 模拟；**预期 1 的「黑名单」与预期 3 的「<200ms」都不能靠单次 curl 验证**（见下）。

**代码事实（本条已逐行通读实现）**

`fep-dev-server/.../qrcode/QrCodeStatusHandler.java`：

- `requestQrCodeStatus` 入口 `:34`；`cardId` 为空直接返 `INVALID_PARAM` + `cardId不能为空`（`:37`~`:40`）。
- 组装 `QueryStatusReqDTO`：只设 `cardId`（`:44`）与 `thirdUserId = deviceUserIdCodec.normalize(request.getItpUserId(), null)`（`:45`）。
- RPC `ticketClient.queryQrCodeStatus`（`:48`）→ `ticket-server` `POST /ci/app/queryQrCodeStatus`（`controller/ci/app/TicketRideStatusController.java:52`，类级 `:28`）。
- 响应为 `null` 时返 `SYSTEM_ERROR` + `票卡状态查询服务异常`（`:49`~`:54`）。
- **响应只回填 5 个字段**：`retCode` / `retMsg`（`:57`~`:58`）、`itpUserId`（`:59`，原样回显）、`cardId`（`:60`，原样回显）、`lastTicketStatus = ticketResponse.getStatus()`（`:61`）、`lastHandleDateTime = ticketResponse.getLastTxnTime()`（`:62`）。

**重写后的可验证断言**

| # | 原表预期 | 重写为 | 结论 |
|---|---|---|---|
| 1 | 返回「正常/已进站/已出站」 | `lastTicketStatus` 等于 `QRCODE_STATUS.CODE_STATUS`：`03` 初始化 / `04` 已进站 / `05` 已出站 / `06` 超时出站 / `02` 结束行程 / `08`/`09`/`10` BOM 更新 / `80`/`81` APP 自助补站 | **可验证** |
| 2 | 返回「黑名单」 | ⚠️ **响应 DTO 里没有黑名单字段**。`QrCodeStatusHandler.java:57`~`:62` 只回填 5 个字段，全链路（`fep-dev-server` → `ticket-server`）**未找到**任何黑名单查询或黑名单状态位。黑名单在本项目是 **APP 主动查 IF8A-73**（`blacklist-server` `POST /queryBlackList`，`controller/BlacklistController.java:84`），**不在这条设备查询里** | **断言不成立** ⇒ [[02-阻塞项与缺陷候选]] C4 |
| 3 | AGM 据状态正确控制闸机 | **ITP 侧无对应断言** —— 闸机拿到 `lastTicketStatus` 后怎么判、开不开门，完全在闸机本地 | **必须真机** |
| 4 | 查询响应时间 < 200ms | ⚠️ **当前无性能基线，属需另立压测口径的断言**。单次 curl 的耗时**不能**当成通过判据，理由有 4 条：①链路是 `curl → fep-dev-server → RPC → ticket-server → Oracle`，**跨 2 个服务 + 1 次 DB 查询**；②`ticket-server` 的 `DispatcherServlet` 是**懒初始化**的，冷启动后首次请求已实测到 **4850ms**（热态 36~67ms），**两组数据不可混用**（`docs/testing/user-card/INDEX.md:80`）；③全服务默认 `spring.threads.virtual.enabled=true`，而 ojdbc8 大量方法是 `synchronized` —— 虚拟线程在其中阻塞会 **pin 住载体线程**，一两条慢 SQL 就能让整个 JVM 的虚拟线程停止调度，**尾延迟不是正态分布**；④没有约定 P50/P95/P99、并发度、样本量、预热轮次、是否含网关跳。**要验它 MUST 先定这 4 项口径 + 单独立压测用例**，本库不假装单次 curl 能验 | **不可用单次 curl 验证** ⇒ [[02-阻塞项与缺陷候选]] C6 |
| 5 | — | `itpUserId` 与 `cardId` 是**原样回显**（`:59` / `:60`），不参与查询结果 —— 因此**它们相等不能证明查到了正确的卡** | 可验证（回归点） |
| 6 | — | `itpUserId` 经 `DeviceUserIdCodec.normalize` 转换后才送下游（`:45`），传 `null` 也被允许（第二参传 `null`） | 可验证 |

**执行方式**

① curl

```bash
curl -s -X POST "http://$FEPDEV/ci/agm/requestQrCodeStatus" \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'charset=UTF-8' --data-urlencode 'format=JSON' \
  --data-urlencode 'signType=00' --data-urlencode 'sign=PLACEHOLDER' \
  --data-urlencode 'timestamp=20260922151000' \
  --data-urlencode 'deviceId=<8 位闸机号>' \
  --data-urlencode 'bizData={"cardId":"<卡号>","itpUserId":"<十六进制用户号>"}' \
  -w '\nhttp=%{http_code} time_total=%{time_total}\n'
```

> 字段名以 `fep-dev-server/.../model/RequestQrCodeStatusReqDTO.java` 为准。
> `-w 'time_total=%{time_total}'` **只作观察记录**，**NEVER 拿它当 200ms 断言的通过判据**（见断言 4）。
> 负例：`bizData={}`（无 `cardId`）应返 `INVALID_PARAM` + `cardId不能为空`（`QrCodeStatusHandler.java:38`~`:39`）。
> 直连下游对照：`http://172.20.211.23:<ticket-server NodePort 待现查>/ci/app/queryQrCodeStatus` —— 用来区分耗时/错误出在接入层还是业务层。

② DB 核对

```sql
SELECT CARD_ID, CODE_STATUS, GATE_STATUS, GATE_IN_STATION, GATE_IN_TIME,
       LAST_TXN_STATION, LAST_TXN_TIME, TXN_SEQ, USE_COUNT, UPDATE_TIME
  FROM QRCODE_STATUS
 WHERE CARD_ID = '<卡号>';

SELECT * FROM BLACKLIST
 WHERE CARD_ID = '<卡号>';
```

> 第二条是**用来证明断言 2 不成立的**：即便该卡在 `BLACKLIST` 里有行，IF1A-04 的响应也不会体现。

③ 日志判据

```bash
scripts/klog.sh fep-dev-server 'IF1A-04' 300
scripts/klog.sh ticket-server  'queryQrCodeStatus' 300
```

`fep-dev-server` 对每笔打 3 行：入口（`FepAgmController.java:95`）、带 `bizData` 的那行（`:105`）、响应 `retCode`（`:107`）；Handler 另打「调用 ticket-server 查询票卡状态, 入参=」（`QrCodeStatusHandler.java:47`）与「返回=」（`:56`）。响应为 `null` 时是 WARN「IF1A-04 ticket-server 无响应」（`:50`）。

**真机才能覆盖的部分**：AGM 的扫码/刷卡读取、闸机拿到状态后的本地判定与开合、**闸机本地黑名单表**（这才是「黑名单拦截」真正生效的地方）、真实网络往返时延。

**结果**（2026-09-22 回填，证据见 [[03-执行记录-2026-09-22]] D8 / D9 / D10 / D11 / D12）：
- **断言 1 已通过**：`lastTicketStatus="05"` 与 `lastHandleDateTime="20260920180021"` 分别逐字等于库内 `QRCODE_STATUS` 的 `CODE_STATUS` / `LAST_TXN_TIME`（D8 + MCP 核对）。
- **断言 2 已实证不成立**：响应只有 5 个字段（`retCode` / `retMsg` / `itpUserId` / `cardId` / `lastTicketStatus` / `lastHandleDateTime`），**没有任何黑名单位**（D8），C4 的硬证据已落。
- 断言 3（闸机控制闸门）：**必须真机，本轮未执行**。
- 断言 4（<200ms）：热态 3 次 20ms/20ms/17ms（D12），但**不构成性能基线**——理由见 C6。
- **断言 5 已通过**：`itpUserId` 未传则为 `null`，`cardId` 原样回显（D8）。
- **断言 6 已通过**：`itpUserId` 由 `DeviceUserIdCodec.normalize` 处理后送下游、`null` 被允许（D8 侧面证明）。
- **新观察点 C11（字段裁剪）已实证**：直连下游 `ticket-server` 返 9 个字段（D11），`fep-dev` 只透传 2 个，`gateInStation` / `gateInTime` / `lastTxnStation` / `txnSeq` 全部丢弃。
- **新码值 `8004` = 未注册用户**（D10），已补进 C13。
- 负例 `bizData={}` → `1001 cardId不能为空`（D9） ✅。

---

## ACLC_Device_Test_003 · AGM 密钥同步（IF1A-02）

**原表**
- 前置：AGM 设备正常，密钥有更新
- 步骤：1.ITP生成新密钥 / 2.推送至AGM / 3.AGM接收验证 / 4.AGM更新本地密钥
- 预期：1.密钥同步成功 / 2.AGM使用新密钥验签 / 3.新旧密钥过渡期无感 / 4.密钥版本记录正确 / 5.日志完整
- 原表测试结果：**✅ 通过（√）**

**可执行性**：接口可 curl 模拟。**⚠️ 但方向与原表描述相反**：代码里是**闸机主动拉取**，不是「ITP 推送至 AGM」。

**代码事实（已通读 `KeySyncHandler` 前 70 行）**

- 入口 `FepAgmController.java:70` → `keySyncHandler.requestSynKeyList(bizData, deviceId, request.getBizData())`（`:85`，**把原始 `bizData` 字符串也一起传下去**）。
- `KeySyncHandler.requestSynKeyList` `:41`：`keyCurVerList` 为空直接返 `INVALID_PARAM` + `keyCurVerList不能为空`（`:43`~`:47`）。
- 组装 `RequestAgmSynKeyListReqDTO`：`deviceId`（`:51`）+ `requestBizData`（`:52`，原始串）+ `keyCurVerList`（`:53`，经 `toAgmKeyCurVerList` 转换）。
- RPC `keyClient.requestAgmSynKeyList`（`:55`）→ `key-server` `POST /requestAgmSynKeyList`（`controller/KeyController.java:49`）。
- 响应为 `null` 返 `SYSTEM_ERROR` + `密钥服务无响应`（`:56`~`:60`）；抛异常返 `SYSTEM_ERROR` + `密钥同步服务异常`（`:68`~`:71`）。
- **出向 RPC 地址**：`fep-dev-server` 的 `service.key.url` —— 该键**曾经在仓库 properties 里从来没出现过**、线上只靠一条 Deployment env 兜着，已于 2026-09-14 补齐（ADR-D67）。**排查密钥同步不通 MUST 同时看仓库键与集群 env。**

**重写后的可验证断言**

| # | 原表预期 | 重写为 | 结论 |
|---|---|---|---|
| 1 | 密钥同步成功 | IF1A-02 返 `retCode=0000` 且 `keyCurVerList` 非空；`fep-dev-server` 日志有「IF1A-02 key-server处理完成, deviceId=..., retCode=..., keyVersionCount=N」（`KeySyncHandler.java:64`） | **可验证** |
| 2 | ITP 生成新密钥并**推送**至 AGM | ⚠️ **方向相反**：代码里 AGM **上送自己持有的 `keyCurVerList`（当前版本清单）**、ITP 比对后**回一份需要更新的密钥**。这是**拉模型（闸机主动）**，**未找到**任何 ITP 主动推送密钥到闸机的出向通道。（注：有一条 `/app/receiveChangeAgmCaKey` 是**推给 APP** 的通知，不是推给闸机） | **口径差异** ⇒ [[02-阻塞项与缺陷候选]] C7 |
| 3 | AGM 使用新密钥验签 | **ITP 侧无对应断言** | **必须真机** |
| 4 | 新旧密钥过渡期无感 | **ITP 侧无对应断言**（过渡逻辑在闸机本地：拿到新版本后何时切、旧版本留多久） | **必须真机** |
| 5 | 密钥版本记录正确 | `key-server` 侧落表 `COM_DEVICE_SYN_KEY`（`into`）；密钥版本与池在 `METRO_AGM_KEY_VERSION` / `METRO_AGM_KEY_POOL`；另有 `METRO_CA_KEYSTORE`、`METRO_MEMBER_STATIC_KEY`（`into` + `from`） | **可验证**（表名取自 `key-server/src/main/resources` 下 mapper XML） |
| 6 | 日志完整 | `fep-dev-server` 打 3 行：入口 `deviceId=`（`FepAgmController.java:73`）、`keyCount=`（`:83`）、完成行带 `keyVersionCount`（`:86`）；`KeySyncHandler` 另打完成行（`:64`）。异常路径打 ERROR「IF1A-02 调用key-server同步AGM密钥异常」（`:68`） | **可验证** |
| 7 | — | ⚠️ **NEVER 在日志、文档或对话里回显任何密钥值**。核对只写「版本号 / 条数 / 表名 / 列名」，**不写密钥内容** | `AGENTS.md` §5.2「敏感配置」+「安全红线」 |
| 8 | — | ⚠️ **NEVER 擅自改动加密 / 签名逻辑**；涉及密钥的改动 MUST 提示人工复核安全合规性 | 同上 |

**执行方式**

① curl

```bash
curl -s -X POST "http://$FEPDEV/ci/agm/requestSynKeyList" \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'charset=UTF-8' --data-urlencode 'format=JSON' \
  --data-urlencode 'signType=00' --data-urlencode 'sign=PLACEHOLDER' \
  --data-urlencode 'timestamp=20260922152000' \
  --data-urlencode 'deviceId=<8 位闸机号>' \
  --data-urlencode 'bizData={"keyCurVerList":[{"keyType":"<密钥类型>","keyVer":"<当前版本号>"}]}' \
  -w '\nhttp=%{http_code}\n'
```

> `keyCurVerList` 元素的字段名 **MUST** 现查 `fep-dev-server/.../model/KeyCurVerReqDTO.java` 与 `model/.../model/agm/AgmKeyCurVerDTO.java`（`KeySyncHandler.java:53` 的转换目标）。
> 负例（**最有价值的一条免造数据回归点**）：`bizData={"keyCurVerList":[]}` 应返 `INVALID_PARAM` + `keyCurVerList不能为空`（`KeySyncHandler.java:44`~`:45`）。
> 「密钥有更新」这个前置**需要造数据**：让上送的 `keyVer` 低于库里当前版本。**MUST 先记原值 + 写下还原 SQL**。

② DB 核对

```sql
SELECT * FROM METRO_AGM_KEY_VERSION
 ORDER BY 1 FETCH FIRST 50 ROWS ONLY;

SELECT * FROM COM_DEVICE_SYN_KEY
 ORDER BY 1 DESC FETCH FIRST 50 ROWS ONLY;

SELECT COUNT(*) FROM METRO_AGM_KEY_POOL;
```

> 列名 **MUST 先 `describeTable`**（该 MCP 的表名键是 `table`、连接键是 `connection`，**两个键名容易记反**）。
> ⚠️ **查询结果里若含密钥密文列，NEVER 把值抄进文档或对话**，只记行数与版本号。

③ 日志判据

```bash
scripts/klog.sh fep-dev-server 'IF1A-02' 300
scripts/klog.sh key-server 'requestAgmSynKeyList' 300
```

`key-server` **不在 tracing 名单**，`traceId` 列恒为空。

**真机才能覆盖的部分**：闸机接收密钥后的本地校验与写入、SAM/加密模块、**新旧密钥过渡期的实际无感切换**（这是原表预期 3 的核心，ITP 侧完全看不到）、以及「AGM 用新密钥验签成功」这件事。

**结果**（2026-09-22 回填，证据见 [[03-执行记录-2026-09-22]] D4）：
- **断言 6 的负例分支已通过**：`bizData={"keyCurVerList":[]}` → `{"retCode":"1001","retMsg":"keyCurVerList不能为空","keyCurVerList":null}`，219ms。错误码 `1001` = `FepDevErrorCodeEnum.INVALID_PARAM`。
- 断言 1（同步成功 + 返回需更新的密钥清单）与断言 5（版本落库）**本轮未执行** —— 需先造「上送 `keyVer` 低于库内当前版本」的前置数据，属**改数据，待批次二**。
- 断言 2 的方向差异（拉 vs 推）见 C7，本轮无新证据。
- 断言 3 / 4（AGM 用新密钥验签、新旧过渡无感）**必须真机**。
- 本轮**未回显任何密钥值**，符合断言 7。

---

## ACLC_Device_Test_004 · AGM 设备心跳（IF1A-03）

**原表**
- 前置：AGM 设备正常，网络正常
- 步骤：1.AGM设备启动 / 2.按周期发送心跳（**设备ID、IP、状态、时间戳**） / 3.ITP接收 / 4.更新设备状态
- 预期：1.设备状态更新为"在线" / 2.心跳时间记录正确 / 3.日志完整
- 原表测试结果：**✅ 通过（√）**

**可执行性**：接口可 curl 模拟。**⚠️ 但原表预期 1 与 2 在代码里都没有落点** —— 这是本页最硬的一处冲突。

**代码事实（该方法只有 8 行，已全文通读）**

```java
/** IF1A-03 设备心跳。 */                                    // FepAgmController.java:111
@PostMapping({"/deviceHeartbeat", "/notiDeviceHeard"})        // :112
public DeviceHeartbeatRespDTO deviceHeartbeat(@ModelAttribute ItpCommonFormRequest request) {  // :113
    log.info("IF1A-03 设备心跳, deviceId={}", request == null ? null : request.getDeviceId());   // :114
    DeviceHeartbeatRespDTO response = new DeviceHeartbeatRespDTO();                             // :115
    response.setRetCode(FepDevErrorCodeEnum.SUCCESS.getCode());                                 // :116
    response.setRetMsg(FepDevErrorCodeEnum.SUCCESS.getMessage());                               // :117
    return response;                                                                            // :118
}
```

四条事实，逐条都能从上面 8 行直接看出来：

1. **不解析 `bizData`** —— 没调 `hasBizData`、没调 `parseBizData`。上送的「IP、状态、时间戳」**一个都不读**。
2. **不落库** —— `fep-dev-server` 本身无 DB、无 mapper（`AGENTS.md` §3.2），这个方法也没调任何 Handler / Client。
3. **不做任何 RPC** —— 方法体内零依赖调用。
4. **恒返 `SUCCESS`** —— 无论上送什么（包括空 `bizData`、错误 `deviceId`、甚至不存在的设备），**一律返成功**。

**重写后的可验证断言**

| # | 原表预期 | 重写为 | 结论 |
|---|---|---|---|
| 1 | 设备状态更新为「在线」 | ⚠️ **未找到实现**。该方法不落库、不 RPC，**ITP 侧没有任何 AGM 设备在线状态表或字段**（`fep-dev-server` 无 DB；全仓未找到 AGM 心跳落库点） | **断言不成立** ⇒ [[02-阻塞项与缺陷候选]] C8 |
| 2 | 心跳时间记录正确 | ⚠️ **未找到实现**。唯一的「记录」是日志行 `IF1A-03 设备心跳, deviceId=<X>`（`:114`），**它只含 `deviceId`，不含时间戳字段值**（时间靠日志自身的时间前缀） | **断言不成立**（只能退化成「日志里有这一行」） |
| 3 | 日志完整 | **可验证但很弱**：每次心跳恰好 1 行 `IF1A-03 设备心跳, deviceId=`（`:114`）。**上送的 IP / 状态 / 时间戳都不进日志**（因为根本没解析 `bizData`） | **部分可验证** ⇒ 同 C8 |
| 4 | — | 端点**有两个别名**：`/ci/agm/deviceHeartbeat` 与 `/ci/agm/notiDeviceHeard`（`:112`）。两条行为完全一致 —— **回归时两条都要打**，否则「上游按另一份文档取 URL」这类契约偏差发现不了 | **可验证** |
| 5 | — | **恒返成功**这一点本身可以当断言：送空 `bizData`、送不存在的 `deviceId`、连 `deviceId` 都不送，都应返 `retCode` = `FepDevErrorCodeEnum.SUCCESS`。**这是现状、不是缺陷判定**，但要写进用例好让人知道「这个接口永远不会告诉你设备有问题」 | **可验证** |
| 6 | — | 对照：`face-pay-server` 有一个「**设备离线**」`@Scheduled`（7 个 `@Scheduled` 之一，`AGENTS.md` §3.2）—— 那是 **TVM/BOM 域**的设备离线判定，**与 AGM 心跳不是同一套、NEVER 混用**。AGM 侧确实没有 | 事实记录 |

**执行方式**

① curl（两个别名都打；第三条是「恒返成功」的证明）

```bash
# 别名 1
curl -s -X POST "http://$FEPDEV/ci/agm/deviceHeartbeat" \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'charset=UTF-8' --data-urlencode 'format=JSON' \
  --data-urlencode 'signType=00' --data-urlencode 'sign=PLACEHOLDER' \
  --data-urlencode 'timestamp=20260922153000' \
  --data-urlencode 'deviceId=<8 位闸机号>' \
  --data-urlencode 'bizData={"deviceId":"<8 位闸机号>","deviceIp":"10.0.0.1","deviceStatus":"1","timestamp":"20260922153000"}' \
  -w '\nhttp=%{http_code}\n'

# 别名 2（同一方法）
curl -s -X POST "http://$FEPDEV/ci/agm/notiDeviceHeard" ... -w '\nhttp=%{http_code}\n'

# 恒返成功的证明：不带 bizData、deviceId 用一个不存在的值
curl -s -X POST "http://$FEPDEV/ci/agm/deviceHeartbeat" \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'deviceId=99999999' \
  -w '\nhttp=%{http_code}\n'
```

> 上面 `bizData` 里的 `deviceIp` / `deviceStatus` **是按原表步骤描述写的占位字段，代码并不读它们** —— 保留在这里是为了让「上送了但没人接」这件事在用例里可见。**NEVER 据此认为存在这几个字段的契约。**

② DB 核对

**没有可查的表。** 这本身就是断言 1 / 2 的判据：

```sql
SELECT TABLE_NAME FROM USER_TABLES
 WHERE TABLE_NAME LIKE '%HEART%' OR TABLE_NAME LIKE '%DEVICE%STATUS%'
    OR TABLE_NAME LIKE '%AGM%DEVICE%';
```

期望：**查不到任何承载 AGM 在线状态的表**。若查到了，说明本条结论过期、MUST 整段改写。
（`COM_DEVICE_SYN_KEY` 会被 `%DEVICE%` 类模糊匹配命中，但它是**密钥同步记录**、不是心跳状态表 —— 别误判。）

③ 日志判据

```bash
scripts/klog.sh fep-dev-server 'IF1A-03 设备心跳' 500
```

期望：每次 curl 恰好新增 1 行，内容只有 `deviceId=`。**上送的 IP / 状态 / 时间戳都不会出现在日志里。**

⚠️ 排查「心跳到底有没有到」时注意：`scripts/klog.sh` **只看尾部窗口**，而**心跳是高频接口**、很容易把窗口刷满并把别的日志顶掉；同时 **Pod 一重启日志目录就消失**。要确认某个历史时刻的心跳，**MUST** `ssh k8s-master` 后进容器 grep 完整日志文件（`/home/javaapp/app/logs/*/*.log`）。

**真机才能覆盖的部分**：闸机的心跳发送周期与重试、设备真实的网络状态、以及**「设备掉线了谁会知道」** —— 按当前实现，ITP 完全不知道；如果现场有在线状态监控，那它**不是靠这个接口**实现的，MUST 另找来源。

**结果**（2026-09-22 回填，证据见 [[03-执行记录-2026-09-22]] D1 / D2 / D3）：
- **断言 1「设备状态更新为在线」已实证不成立**：`deviceId=99999999`（不存在的设备、无 `bizData`）仍返 `{"retCode":"0000","retMsg":"成功"}`（D3）。一个从不存在的设备上送心跳也返成功 ⇒ ITP 不可能据此维护在线状态。**C8 硬证据已落。**
- **断言 2「心跳时间记录正确」同上不成立**：完整报文里的 `deviceIp` / `deviceStatus` / `timestamp` 三个字段**无任何落点**（D1）。
- 断言 3「日志完整」：只能退化成「日志里有 `IF1A-03 设备心跳, deviceId=` 这一行」，本轮未逐 Pod 取日志文件，**弱通过**。
- **断言 4「双别名行为一致」已通过**：`/ci/agm/deviceHeartbeat` 25ms 与 `/ci/agm/notiDeviceHeard` 6ms 均返 `0000 成功`（D1 / D2）。
- **断言 5「恒返成功」已通过**：三种形态（完整报文 / 只送 deviceId / 不存在设备号）全部 `0000`（D1~D3）。
- **对原表 ✅ 的判读已被本轮证实**：那个 ✅ 只能解释为「验证了接口返回成功」，而**这个接口对任何输入都返回成功**。

---

[[INDEX]] · [[02-阻塞项与缺陷候选]] · [[03-执行记录-2026-09-22]] · `docs/testing/app/INDEX.md`
