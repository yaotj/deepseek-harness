# 支付宝出行 §3.69 开卡申请 `/channel/requestApplication` 端到端联调（2026-09-18）

**结论：内部固定四步链路完整跑通，5 个用例全部符合预期，无阻塞缺陷。** 日志已逐步钉住「卡池预占 → 注册乘车状态 → 短事务落两表 → 卡池 confirm」的真实顺序与耗时，三张表 + 卡池行状态全部核对一致。发现 1 个 P2 数据口径问题（`QRCODE_STATUS.CHANNEL` 恒为 `01`）、2 个未闭合风险项（confirm 失败仍返成功；线上镜像字节码与仓库源码不一致），见 §六、§七。

**复测（`alipay-account:1.0.16`，同日 09:32）：§六 的 P2 与 §七 的 ①②（返回码 + 失败分支 release）已修并实测通过，§七③ 的字节码漂移随本次重建自然消除；见 §九。**

## 一、被测环境（2026-09-18 09:18 现查，NEVER 引用本节，MUST 每次重查）

| 项 | 实测值 |
|---|---|
| 入向路由 | `fep-app-vr` 的 `/fep-alipay/` → `fep-alipay-rec8g-svc:30020`，`rewrite.uri=/` |
| 网关 | Deployment **`fep-alipay`**（`itp/fep-alipay:1.0.61`）；另一个 `fep-alipay-server`（1.0.58 / svc 30023）**不在入向链路上** |
| 账户服务 | `alipay-account-server`（`itp/alipay-account:1.0.15`），NodePort **30021** |
| 卡池 | `card-pool-server`（`itp/card-pool-server:1.0.18`），svc `card-pool-server-86mc1-svc:30033` |
| 乘车状态 | `ticket-server`（`itp/ticket-server:2.1.86`），`9100:30014` |
| `fep-alipay` 转发键 | `service.account.url=http://172.20.211.23:30021` |
| `alipay-account` 下游键 | `service.ticket.url=http://172.20.211.23:30014`；`service.cardPool.url=${CARD_POOL_URL:http://card-pool-server-86mc1-svc.itp.svc:30033}`（env `CARD_POOL_URL` 已移除，实际落默认值，Pod 内 DNS 可解析） |
| 目标库 | `172.20.222.3:1521 / AFCITPDB`（`mcp_database_qd`） |

发请求方式：`ssh k8s-master` 后 `curl -X POST http://172.20.211.23:30020/channel/requestApplication`，`application/x-www-form-urlencoded` + `bizData`（JSON）。公共参数按 `ItpCommonFormRequest` 带全（`signType=00`），**本链路全程不验签**（`fep-alipay-server` 无 Verifier，`alipay-account-server` 的 `/channel/**` 是 `@RequestBody` 内部端点）。

测试前基线：`ALIPAY_USER_INFO` 5 行；`LOGIC_CARD_POOL_CARD` 的 `0441` 有 99978 `AVAILABLE` / 31 `ASSIGNED`，**无 `RESERVED` 行**（无悬挂预占）。

## 二、用例与实测结果（全部 HTTP 200）

- **T1 首次开户**（`thirdUserId=0700009930`，`cardType=02`，`cardIssueCode=0007`，`msisdn=15064259930`）→ `{"retCode":"0000","retMsg":"成功","cardId":"0426090949000418","cardType":"02","status":"ACTIVE"}`，端到端 **156ms** ✅
- **T2 幂等重放**（同一 `thirdUserId` 原样再打）→ `0000 用户已开户` + **同一个 `cardId`**，卡池未再消耗、两表未新增行 ✅（`selectByThirdUserId` 命中即短路，只打一行 WARN）
- **T3 缺 `cardIssueCode`** → `8001 cardIssueCode 不能为空` ✅（校验在预占之前，未触达卡池）
- **T4 票种不走卡池**（`cardType=99`）→ `8001 票种配置错误，无法开卡` ✅；卡池侧原文 `{"code":"400","msg":"票种码非法或为空: 99"}`，本端按 `REJECTED` 分流打 ERROR「重试无用」，**未消耗卡号、未落任何行** ✅
- **T5 第二个新用户**（`0700009933`）→ `0000` + `cardId=0426090949000328`，总 **40ms** ✅

## 三、四步顺序与耗时（T5 日志逐条，`alipay-account` Pod）

```
09:18:15.484  接收到支付宝出行-开卡申请报文 {"cardIssueCode":"0007","cardType":"02","msisdn":"...","thirdUserId":"0700009933"}
09:18:15.486  SELECT ALIPAY_USER_INFO ... THIRD_USER_ID='0700009933'  → fetchRowCount:0（幂等前置查重）
09:18:15.486  POST card-pool /internal/card-pools/reservations        ① 预占
09:18:15.499  ← {"code":"200","data":{"reservationId":"c3f5333f-...","cardNo":"0426090949000328","cardType":"0441","expireTime":"09:28:15"}}
09:18:15.499  POST ticket /ci/app/registerRideStatus                   ② 注册乘车状态
09:18:15.510  ← {"retCode":"0000","cardId":"0426090949000328","itpUserId":"0700009933","cardStatus":"03"}
09:18:15.512  INSERT ALIPAY_USER_INFO                                 ③ 短事务（TransactionTemplate）
09:18:15.514  INSERT ALIPAY_REG_LOG                                   ③ 同一事务
09:18:15.515  POST card-pool .../reservations/{reservationId}/confirm  ④ confirm
09:18:15.522  ← {"code":"200","msg":"SUCCESS"}
09:18:15.522  开卡申请成功, thirdUserId=0700009933, cardId=0426090949000328
```

四步与 `AlipayAccountServiceImpl.requestApplication` 的实现完全对应，且**三次 RPC 都在事务之外**（`requestApplication` 与类上都没有 `@Transactional`，只有第 ③ 步用 `transactionTemplate.executeWithoutResult` 包两条 INSERT）—— 符合 AGENTS.md §5.2「`@Transactional` 内 NEVER 发起 RPC」。

`businessId` 形态实测 `ALIPAY_ACCOUNT_OPEN:<thirdUserId>:<发卡票种>`，`ownerId=thirdUserId`，`businessType=ALIPAY_ACCOUNT_OPEN`；`cardType=02` 经 `CardTypeMapping.toIssueCardType` → **`0441`**。预占租期 **10 分钟**（`expireTime` 与 `RESERVED_TIME` 差 10min）。

## 四、落库核对（`mcp_database_qd` 只读回查）

`ALIPAY_USER_INFO` +2 行（5 → 7，T3/T4 未落行）：

- `0700009930` / `0426090949000418` / `CARD_TYPE=02` / `CARD_ISSUE_CODE=0007` / `CHANNEL=07` / `STATUS=ACTIVE` / `VERSION=1` / `DELETE_FLAG=0` / `CREATE_TIME=09:18:01`
- `0700009933` / `0426090949000328` / 同上 / `CREATE_TIME=09:18:15`

`ALIPAY_REG_LOG` +2 行，与上面一一对应，`RESULT_CODE=0000` / `RESULT_MSG=成功`，`REQUEST_SEQ` 为本地生成的 32 位大写 UUID（`AD98EEAE52A54BF5B6103B4202D9B193` / `DB292DDBBA734EC68FE8B85F009B8FB1`）。**失败请求（T3/T4）不落 `ALIPAY_REG_LOG`** —— 该表只记成功，属现状。

`LOGIC_CARD_POOL_CARD`（两张卡）：`STATUS=ASSIGNED`、`RESERVATION_ID` 与日志一致、`BUSINESS_ID=ALIPAY_ACCOUNT_OPEN:<user>:0441`、`OWNER_ID=<user>`、`RESERVED_TIME` → `CONFIRM_TIME` 间隔 **67ms / 26ms**，无悬挂 `RESERVED`。

`QRCODE_STATUS`（两张卡各 1 行，`registerRideStatus` 落库）：`CODE_STATUS=03`、`GATE_STATUS=0000`、`TXN_SEQ=0`、`LAST_TXN_TIME=00000000000000`、`GATE_IN_STATION=FFFF`、`USE_COUNT=0`、`CHANNEL=01`。

## 五、幂等口径（实测确认）

按 **`THIRD_USER_ID` + `DELETE_FLAG='0'`** 维度幂等，命中即返 `0000 用户已开户` + 原卡信息，**不新增卡、不换卡**。因此「同一支付宝用户想再开一张不同票种的卡」在本域**开不出来** —— 与 `account-server` 侧由 `ticketLimit` 控制多卡（ADR-D122）**不是同一套规则**，本域没有 `ticketLimit` 语义。上层若有多卡需求 MUST 先澄清。

## 六、P2：`QRCODE_STATUS.CHANNEL` 把支付宝渠道记成 `01`

`AlipayAccountServiceImpl` 组装 `RegisterRideStatusReqDTO` 时**只传 `thirdUserId` / `cardId` / `cardType`，没传 `channel`**，而 `TicketRideStatusServiceImpl:79` 是「入参没带就落默认值 `${ticket.default-channel:01}`」。于是同一张卡：`ALIPAY_USER_INFO.CHANNEL='07'`（支付宝），`QRCODE_STATUS.CHANNEL='01'`。

影响面是「按渠道口径统计 / 排查乘车码来源」会把支付宝卡算进 `01`。修法是发起侧显式带 `channel=IssueChannelCodeEnum.ALIPAY.getCode()`（`07`），**不要动 ticket-server 的默认值**（那是 APP 域的默认）。改前 MUST 确认 `QRCODE_STATUS.CHANNEL` 的取值体系与 `ALIPAY_USER_INFO.CHANNEL` 是否同一套 —— 本次未核实，属待澄清。

## 七、未闭合风险（本次未构造故障、只做代码口径记录）

1. **第 ④ 步 confirm 失败仍对上游返 `0000`，且不开异常工单**（`AlipayAccountServiceImpl:116~119` 只打一行 ERROR「留人工核对」）。这与 AGENTS.md §5.2 / ADR-D52 的「最后那步确认失败 MUST NOT 返成功，MUST 落 `ACCOUNT_EXCEPTION_TICKET.CARD_POOL_CONFIRM_REJECTED`」相反；`alipay-account-server` 模块内也没有工单表的 Mapper。`account-server` 那份**未接线**的同 URL 实现（`AlipayTripRegistrationServiceImpl:132~137`）是按 D52 写的（开工单 + 返 8007）。**两处口径不一致，MUST 定归属后统一。**
2. **失败分支仍 `releaseReservation`**（`:129~135`）—— ADR-D52 明确「按业务键幂等的远端资源 NEVER 在失败分支回滚，交 `sys_job` 107 超时回收」（**107 现为 240「卡池数据导入」**，2026-09-21 改名改号），account-server 那份已删掉全部 release 并留了 NEVER 注释。本域并发下会踩 D52 记录的那类「卡号已发给 A、卡池却回到 AVAILABLE」。本次为单线程用例，未触发。
3. **线上镜像的字节码与仓库当前源码不一致**：日志行号是 `AlipayAccountServiceImpl.java:85 / 103 / 312 / 146`，而工作副本对应位置是 `:66 / 84 / 284 / 127`（`svn status` 干净、`svn diff` 为空、BASE 与工作副本均 367 行）。偏移量不固定（19 / 28），与「注释知识迁移」那批改动剥掉分散注释的形态一致。也就是说 **`itp/alipay-account:1.0.15` 这个 tag 上的镜像是注释剥离前构建的**，下次 `mvn package`（该模块 jkube 在 `remote` profile 且 `activeByDefault=true`、绑 `package`）会**同 tag 覆盖**。行为预期等价（只是注释），但要按 AGENTS.md §7 取硬证据得在容器内 `javap` 反查。**本次未做该反查。**

## 八、本次产生的测试数据（不需要清理，可作后续签约用例的账号）

`0700009930` / `0426090949000418`、`0700009933` / `0426090949000328`（均 `CARD_TYPE=02`，卡池已 `ASSIGNED` + `CONFIRM_TIME` 有值）。`0700009931`、`0700009932` 只用于失败用例，**库里没有任何行**。复测那笔是 `0700009940` / `0426090949000069`（`QRCODE_STATUS.CHANNEL='07'`，是**第一张渠道值正确的卡**）。

## 九、复测（`alipay-account:1.0.16`，2026-09-18 09:32）

改动三处（`AlipayAccountServiceImpl`，pom 1.0.15 → 1.0.16，镜像 `Pushed itp/alipay-account:1.0.16` + `digest: sha256:c6e5ca05…`，`kubectl set image` 后 `rollout status` 成功、探活两次均 `http=200` 且 body 里 `db` / `readinessState` 全 UP）：

1. **注册乘车状态补传 `channel`**：`registerReq.setChannel(IssueChannelCodeEnum.ALIPAY.getCode())`。修的是 §六 —— ticket-server 侧是「入参没带就落 `${ticket.default-channel:01}`」，**没改 ticket-server 的默认值**。
2. **confirm 失败改为不返成功**：返 `9001` + `retMsg=卡号确认失败，请稍后重试`。**没有用 8007** —— 公共 `FepAppErrorCodeEnum` 里刻意没有 8007，而各模块自有枚举的 8007 语义互斥（`AccountErrorCodeEnum` 服务提供商不可用 / `TicketErrorCodeEnum` 合作伙伴验证失败 / `CollectTicketErrorCodeEnum` 设备不存在 / `BomPayCodeEnum` 订单已退款…），往公共枚举加 8007 会污染 21 个模块共用的码值空间。按用户裁决「只改返回码、工单另开任务」，工单仍未落。
3. **删掉 catch 分支的 `releaseReservation`**（连私有方法、`reservationSettled` 标志、失效的 `StringUtils` import 一并删净），与 account-server 那份的 ADR-D52 口径对齐，并在 confirm 分支上方留了一行式护栏注释。

**残留（本次未闭合，属选项 `code_only` 的已知代价）**：confirm 失败返 `9001` 后，上游重试会命中「用户已开户」幂等短路、**不会补做 confirm**，那行预占到期由 `sys_job` 107 回收（**107 现为 240「卡池数据导入」**，2026-09-21 改名改号） —— 而卡号已经写进 `ALIPAY_USER_INFO` 并发给用户，回收后可能再分配给别人。护栏注释已把这条写进代码。要真正闭合需要「短路分支里判断卡池是否已 confirm 并补做」或异常工单，**MUST 另开任务**。

复测四例（打 `172.20.211.23:30020`，与 §二 同一形态）：

- **R1 新用户 `0700009940`** → `0000` + `cardId=0426090949000069` ✅
- **R2 幂等重放** → `0000 用户已开户` + 同一 cardId ✅
- **R3 缺 `cardIssueCode`** → `8001` ✅
- **R4 `cardType=99`** → `8001 票种配置错误，无法开卡` ✅

落库回查：`QRCODE_STATUS`（`0426090949000069`）`CHANNEL='07'`、`CODE_STATUS='03'`、`TXN_SEQ='0'` —— **P2 已闭合**；同批旧两行仍是 `01`（历史数据，**未回填、不改**）。`ALIPAY_USER_INFO` + `ALIPAY_REG_LOG` 各 1 行且 `RESULT_CODE=0000`；卡池行 `STATUS=ASSIGNED`、`CONFIRM_TIME=09:32:07.236`（预占 → 确认 891ms）。

**confirm 失败分支已用单测钉住**（`alipay-account-server/src/test/java/.../AlipayAccountCardPoolConfirmTest.java`，2 个用例，`mise exec -- mvn test -Dtest=AlipayAccountCardPoolConfirmTest` 全绿）：①confirm 返 `REJECTED` 时对上游返 `9001` + 那句 retMsg、两表各 1 行 INSERT 仍在、`release` 零调用；②confirm 成功时返 `0000` + cardId，且 `registerRideStatus` 的入参 `channel` 断言等于 `07`。

**「打断网络做故障注入」这条路已被卡池语义否掉，NEVER 再试**（2026-09-18 读 `CardPoolServiceImpl.confirm:263~274` + `LogicCardPoolMapper.xml:151~156/178~182` 确认）：`confirm` 的 CAS 谓词是 `RESERVATION_ID + BUSINESS_ID + STATUS='RESERVED'`，**不校验 `EXPIRE_TIME`**（过期预占照样能确认），CAS 不中还会回查「`ASSIGNED` + 同 businessId 即幂等返成功」；而 `reserve` 的幂等查询**只按 `(BUSINESS_TYPE, BUSINESS_ID)`、不带 `STATUS` 也不带过期判断**，命中后状态白名单只放 `RESERVED` / `ASSIGNED`（其余直接 400）。**于是真实环境里造不出「reserve 成功但 confirm 失败」**：把那行改成 `ASSIGNED` → confirm 幂等返成功；改成别的状态 → 第 ① 步 reserve 就先返 400。要线上实证只能起一个假卡池 + 临时改 `service.cardPool.url` env，成本高于收益，故按裁决只做单测。

**回滚命令**：`kubectl set image deploy/alipay-account-server alipay-account-server=os-harbor-svc.default.svc.cloudos:443/itp/alipay-account:1.0.15 -n itp`（注意 1.0.15 那个 tag 上的镜像是**注释剥离前**的构建产物，见 §七③）。
