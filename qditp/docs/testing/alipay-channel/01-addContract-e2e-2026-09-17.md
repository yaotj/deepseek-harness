# 支付宝小程序 §3.67 添加签约信息 `/channel/addContract` 端到端联调（2026-09-17）

**结论（截至复测）：**
- **首轮（`alipay-pay-sign:1.1.21`）**：接口链路本身跑通（8 个用例全部符合预期），但发现两个缺陷，其中 P0 是 ADR-D129 / D131 / D132 整套「支付通道同步 outbox」改造一行都没上线。
- **复测（`alipay-pay-sign:1.1.22`，同日 15:52）**：**P0 已闭合** —— 首签、重复签约、补偿扫描三段全部实测通过，见 §九。**P1（`agreementCode` 经 `fep-alipay` 丢字段）仍在**，待甲方核对应答字段清单后再改。

## 一、被测环境（现查实测，NEVER 引用本节，MUST 每次重查）

| 项 | 实测值 | 查法 |
|---|---|---|
| 入向路由 | `fep-app-vr` 的 `/fep-alipay/` → `fep-alipay-rec8g-svc:30020`，`rewrite.uri=/` | `kubectl get vs fep-app-vr -n itp -o yaml` |
| 网关 Deployment | **`fep-alipay`**（`itp/fep-alipay:1.0.61`）—— 集群里另有一个 `fep-alipay-server`（1.0.58 / `fep-alipay-server-uk4mo-svc:30023`），**不在入向链路上** | `kubectl get deploy -n itp` + svc selector |
| 签约服务 | `alipay-pay-sign-server`（`itp/alipay-pay-sign:1.1.21`），NodePort **30022** | 同上 |
| 账户服务 | `alipay-account-server`（`itp/alipay-account:1.0.15`），NodePort **30021** | 同上 |
| `fep-alipay` 转发键 | `service.alipay-pay-sign.url=http://172.20.211.23:30022`（中划线）、`service.account.url=http://172.20.211.23:30021` | Deployment env |
| 探活 | 30020 / 30021 / 30022 全 `http=200` | `curl .../actuator/health` |
| 目标库 | `172.20.222.3:1521 / AFCITPDB`（`mcp_database_qd`） | — |

**两个 fep-alipay 并存是本域特有的坑**：`kubectl get deploy` 会列出 `fep-alipay` 与 `fep-alipay-server` 两条，名字更「完整」的那个反而**不承接入向流量**。判断打哪个 **MUST** 用 svc selector 反查（`fep-alipay-rec8g-svc` 的 selector 是 `app=fep-alipay`），**NEVER 按名字长短猜**。

## 二、测试账号

| 项 | 值 |
|---|---|
| `THIRD_USER_ID` | `0700009917`（本次新开） |
| `CARD_ID` | `0426090949000160`（卡池分配，`CARD_TYPE=02`） |
| `MSISDN` | `15064259917` |
| 协议号 | `070000991722894917` |
| 支付账号 | `2088302232559917` |
| 对照账号 | `0700001448` / `070000144822894262`（2026-09-11 那笔，已签约） |

`docs/testing/alipay-channel/00-开户与签约真实链路基线.md` §一记的 `CARD_ID=0426090949000070` **已过期** —— 库里 `0700001448` 现在挂的是 `2607031119542741`，**NEVER 引用那一行**。

## 三、用例与实测结果（全部 HTTP 200）

打 `POST http://172.20.211.23:30020/channel/addContract`，`application/x-www-form-urlencoded` + `bizData`（JSON）。

- **A 缺 `agreementCode`** → `{"retCode":"8001","retMsg":"无效的参数"}` ✅
- **B `bizData` 整个缺失** → `8001 无效的参数` ✅（`FepAlipayTripController:48` 的前置判空）
- **C 未开户用户** → `{"retCode":"9999","retMsg":"用户未开户，无法签约"}` ✅（`AlipayContractServiceImpl` 反查账户域拿不到 `cardId`）
- **D 已签约用户重复请求（换新协议号）** → `{"retCode":"0000","retMsg":"该用户已签约"}`，**不新增行、不覆盖原协议号** ✅
- **E 开户 `requestApplication`** → `0000` + `cardId=0426090949000160` ✅
- **F 首签 `addContract`** → `0000`，落库正确（见 §四） ✅
- **G 首签后重复 `addContract`（换协议号）** → `0000 该用户已签约`，返回的是**原协议号** ✅
- **H 直连 30022（绕过网关）同一报文** → `{"retCode":"0000","retMsg":"该用户已签约","agreementCode":"070000991722894917"}` —— **比经网关多了 `agreementCode`**，即缺陷 2

幂等口径：`selectByThirdUserIdAndChannel(thirdUserId, 'ALIPAY')` 命中即短路，**按用户维度幂等、不按协议号**。因此「同一用户换协议号再签」会被当成重复而不是换签，这是现行设计（换签只能先解约）。

## 四、落库核对（`mcp_database_qd` 只读回查）

`ALIPAY_SIGN_INFO`（`0700009917`）：`SIGN_STATUS=SIGNED`、`OPERATION_TYPE=SIGN`、`SIGN_TIME=2026-09-17 15:19:15`、`CARD_ID` / `CARD_TYPE` 从账户域反查回填正确、`CHANNEL='ALIPAY'`（**注意报文里 `channel=05`，与落库值不是同一取值体系**）。

`ALIPAY_USER_INFO`（`0700009917`）：`THIRD_PAY_ID=2088302232559917`、`REQ_CONTRACT_NO=070000991722894917`、`UPDATE_TIME=15:19:15` —— **支付通道回写成功**，出网 `updatePaymentChannel` 返 `true`。

`ALIPAY_SIGN_LOG`：+1 行（ID=11），`RESULT_CODE=0000`，`RESPONSE_BODY` 原文 `{"agreementCode":"070000991722894917","retCode":"0000","retMsg":"成功"}`。

> 查该表 **NEVER 直接 SELECT `REQUEST_BODY` / `RESPONSE_BODY`** —— 是 CLOB，MCP 返回 `oracle.sql.CLOB@<hash>`。要看原文用 `DBMS_LOB.SUBSTR(col,4000,1)`，或直接从服务日志的 INSERT 语句里取。

## 五、缺陷 1（P0）：ADR-D129 / D131 / D132 整套 outbox 改造没上线

**现象**：新签那行 `CHANNEL_SYNC_STATUS='PENDING'`、`CHANNEL_SYNC_TIME=null`、`CHANNEL_SYNC_RESULT=null`、`CHANNEL_SYNC_RETRY_COUNT=0` —— 四列全空。按 ADR-D132，`ChannelSyncDeliverer.deliver` 首推成功后应回写 `SUCCESS` + 「支付通道已同步」+ 计数 +1。

**这四列现在的值只是 DDL 的 `DEFAULT 'PENDING'` / `DEFAULT 0` 在起作用，不是代码写的。** 库侧 DDL 已执行到位（回查 `USER_TAB_COLS` 四列齐全、`IDX_ASI_CHANNEL_SYNC` `VALID`），缺的是应用。

**硬证据三条**（容器内反查，`kubectl exec` + `jar xf /app.jar`）：

1. `BOOT-INF/classes/.../service/impl/ChannelSyncDeliverer.class` **不存在**（`No such file or directory`）。
2. 容器内 `BOOT-INF/classes/mapper/AlipaySignInfoMapper.xml` 里 `grep -c CHANNEL_SYNC` = **0**，而仓库版那份 insert 明确带 `CHANNEL_SYNC_STATUS, CHANNEL_SYNC_RETRY_COUNT` 并写 `'PENDING', 0`。
3. 运行日志里的 INSERT 语句**没有那两列**；全程**没有 `UPDATE ALIPAY_SIGN_INFO SET CHANNEL_SYNC_STATUS...`**、没有「支付通道已同步」；`updatePaymentChannel` 应答是裸 `true`（旧的 boolean 形态，不是 `RpcOutcome` 三态）。
4. 旁证：日志行号 `AlipayContractServiceImpl.java:61` 打「接收到支付宝添加签约信息报文」、`:115` 打「支付宝签约成功」，而**仓库源码里分别是 `:80` 和 `:113`**。

**成因**：Harbor 上的 `itp/alipay-pay-sign:1.1.21` 是 ADR-D129 之前的构建 —— pom `<version>` 已升到 1.1.21，但那次改动**没有重新 build/push**（或 push 的是改动前的工作副本）。

**这是 AGENTS.md §7「pom `<version>` 不等于线上版本」的同型问题，但更隐蔽：image tag 与 pom 号已经完全一致，只看 tag 会判成「已上线」。** 因此新增一条判据：**改动落在「新增列 / 新增类 / 改 mapper XML」上时，MUST 用容器内 `jar xf` + `grep` 反查该类或该 SQL 片段是否真在 jar 里，NEVER 只对比 tag 与 pom 号。**

**连带后果**：`ChannelSyncCompensationService` 与 `POST /internal/alipay/channelSync/compensate` 端点在线上**也都不存在**，即使 web-admin 建了 `sys_job` 也打不通。ADR-D132 那条「上线前 MUST 在 web-admin 建对应 `sys_job`」现在还多一个前置：**先把镜像重建出来**。

**修法**：`alipay-pay-sign-server` pom `<version>` 升到 1.1.22（**NEVER 复用 1.1.21** —— 同 tag 覆盖后 `imagePullPolicy: IfNotPresent` 的节点不换镜像）→ 重建推镜像（该模块 jkube 绑定阶段用前 MUST 先 `grep -A6 kubernetes-maven-plugin alipay-pay-sign-server/pom.xml` 核实）→ `kubectl set image` → 再跑一遍 §三 用例 F，判据是**新签那行 `CHANNEL_SYNC_STATUS` 变成 `SUCCESS`**。

**遗留数据**：库里现有 2 行签约（`0700001448` / `0700009917`）的 `CHANNEL_SYNC_STATUS` 都是 `PENDING`，而两者的账户域支付通道**实际都已回写正确**。新镜像上线后补偿扫描会把这两行捞起来重推一次 —— `updatePaymentChannel` 幂等，重推无副作用，会自然收口成 `SUCCESS`。**不要手工 UPDATE 成 `SUCCESS`**，让补偿自己走一遍才同时验证了扫描链路。

## 六、缺陷 2（P1）：`agreementCode` 经 `fep-alipay` 转发时被静默丢弃

**现象**：同一报文，直连 `30022` 应答带 `agreementCode`，经网关 `30020` 不带。

**成因**：两侧用的是**同名不同包的两个 DTO**——

- `alipay-pay-sign-server` 侧：`com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO`，有 `retCode` / `retMsg` / **`agreementCode`** 三个字段。
- `fep-alipay-server` 侧：`com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO`，`extends CommonResult` 且**自身零字段**，因此**没有 `agreementCode`**。

Fastjson2 宽松模式静默丢弃目标类里不存在的字段，于是网关一反序列化就没了，既不报错也不告警。**基线文档 §三 记的那份带 `agreementCode` 的应答原文取自 `ALIPAY_SIGN_LOG.RESPONSE_BODY`（alipay-pay-sign 侧记录的），不代表网关出去的报文** —— 这正是它此前没被发现的原因。

**影响**：规格 §3.67 的应答含协议号。签约成功那一支上游本来就知道自己送了什么协议号，影响有限；**真正会咬人的是「该用户已签约」那一支** —— 上游在那里拿到的是**它没送过的、库里既有的那个协议号**，而这一支恰恰是唯一需要靠应答告知协议号的场景。现在这个值到不了上游。

**修法（MUST 先确认口径再动）**：给 `model` 的 `AlipayTripAddContractRespDTO` 加 `agreementCode` 字段。注意这属于 AGENTS.md §7 那条 —— **`model` 版本号恒为 2.0.0，加完字段 MUST 同时重建 `fep-alipay` 与 `alipay-pay-sign-server` 两个镜像**，只重建一边等于没改。另外该 DTO 是**能被上游解析的对外契约**，加字段前 MUST 与甲方核对 §3.67 应答字段清单。

## 七、复查 SQL

```sql
SELECT THIRD_USER_ID, AGREEMENT_CODE, SIGN_STATUS, CHANNEL_SYNC_STATUS,
       CHANNEL_SYNC_RETRY_COUNT, CHANNEL_SYNC_TIME, CHANNEL_SYNC_RESULT
  FROM ALIPAY_SIGN_INFO ORDER BY CREATE_TIME DESC;

SELECT THIRD_USER_ID, CARD_ID, THIRD_PAY_ID, REQ_CONTRACT_NO, UPDATE_TIME
  FROM ALIPAY_USER_INFO WHERE THIRD_USER_ID = '0700009917';

SELECT ID, OPERATION_TYPE, RESULT_CODE,
       DBMS_LOB.SUBSTR(RESPONSE_BODY, 4000, 1) RESP, CREATE_TIME
  FROM ALIPAY_SIGN_LOG ORDER BY ID DESC FETCH FIRST 5 ROWS ONLY;

SELECT ID, CARD_NO, STATUS, BUSINESS_TYPE, BUSINESS_ID
  FROM LOGIC_CARD_POOL_CARD WHERE CARD_NO = '0426090949000160';
```

## 八、未覆盖

- 未走公网入口 `58.56.166.170:48000`，只从 `k8s-master` 打 NodePort 30020（等价于网关转发的落点，但没验证 istio 那一跳）。
- 未验签（本链路当前无入向验签）。
- 未做并发首签（幂等靠 `select` 短路而非唯一索引，`ALIPAY_SIGN_INFO` 上**没有 `THIRD_USER_ID + CHANNEL` 唯一索引**，理论上并发可插两行 —— **待验，本次没测**）。
- 解约、免密扣款、退款本轮未回归。

## 九、复测（`alipay-pay-sign:1.1.22`，2026-09-17 15:52）

### 9.1 先确认新代码真在 jar 里（不看 tag）

Deployment 已换成 `itp/alipay-pay-sign:1.1.22`，Pod `alipay-pay-sign-server-769b8cbb85-6tfc8`，探活 `http=200`。容器内 `jar xf /app.jar` 反查：

- `ChannelSyncDeliverer.class`、`ChannelSyncCompensationService.class`、`AlipayChannelSyncInternalController.class` **三个都在**（首轮全部缺失）。
- jar 内 `mapper/AlipaySignInfoMapper.xml` 搜 `CHANNEL_SYNC` **命中 11 处**（首轮 0 处）。

### 9.2 首签 —— outbox 首推收口

新开户 `0700009918`（`cardId=0426090949000384`、`msisdn=15064259918`、协议号 `070000991822894918`、支付账号 `2088302232559918`）→ `addContract` 返 `0000`。

落库（15:52:03）：`SIGN_STATUS=SIGNED`、**`CHANNEL_SYNC_STATUS=SUCCESS`**、`CHANNEL_SYNC_RETRY_COUNT=1`、`CHANNEL_SYNC_TIME=15:52:03`、`CHANNEL_SYNC_RESULT='支付通道已同步'`；`ALIPAY_USER_INFO` 的 `THIRD_PAY_ID` / `REQ_CONTRACT_NO` 同步回填。**首推与签约同秒收口，ADR-D129 / D132 链路成立。**

### 9.3 重复签约 —— 四种形态，全部只走幂等短路

对同一个 `0700009918` 连打三次，均返 `0000 该用户已签约`：

- 同协议号重发
- **换协议号**（`070000991866666666`）
- **换协议号 + 换支付账号**（`2088999999999999`）
- 直连 30022 再换一个协议号（`070000991855555555`）

关键断言全部成立：

- `ALIPAY_SIGN_INFO` **仍是 1 行**，`AGREEMENT_CODE` 与 `CHANNEL_USER_ACCOUNT` 保持首签值 —— **没被后来那个 `2088999999999999` 覆盖**。
- **`CHANNEL_SYNC_RETRY_COUNT` 仍是 1、`CHANNEL_SYNC_TIME` 仍是 15:52:03、`UPDATE_TIME` 未变** ⇒ 幂等短路发生在 `syncPaymentChannel` **之前**，重复签约**不重复出网、不动 outbox 四列**。这是本次最需要验的一条：若短路点写错位置，每次重签都会白打一次账户域并把计数刷高，而应答完全看不出来。
- `ALIPAY_USER_INFO.THIRD_PAY_ID` 未被改写。
- 直连 30022 那次应答带 `agreementCode`，且是**首签那个协议号**（不是本次上送的）。

> 因此现行语义是「**按用户维度幂等，换协议号 = 重复签约、不是换签**」。换签只能先解约再签。**若甲方期望「换协议号即更新」，那是需求变更、不是 bug**，MUST 先澄清。

### 9.4 补偿扫描 —— 遗留 PENDING 行收口

`POST /internal/alipay/channelSync/compensate` → `{"retCode":"0000","retMsg":"本轮收口 2 条"}`。

回查：首轮遗留的 `0700001448` 与 `0700009917` 两行（旧镜像下停在 `PENDING`）**全部变成 `SUCCESS` / `RETRY_COUNT=1` / `CHANNEL_SYNC_TIME=15:52:33` / `'支付通道已同步'`**。三行现在全 `SUCCESS`。**没有手工 UPDATE，全靠补偿链路自己走通** —— 顺带证明了 `selectCompensableChannelSync` 的取批 SQL 与 `deliver` 的重推是通的。

### 9.5 负面用例回归（改造没打坏原有分支）

缺 `agreementCode` → `8001`；未开户用户 → `9999 用户未开户，无法签约`；`bizData` 整个缺失 → `8001`。与首轮一致。

### 9.6 复测后仍未闭合

1. **P1 `agreementCode` 经 `fep-alipay` 丢字段照旧**（见 §六）——本次复测再次对照确认：直连 30022 带、经 30020 不带。
2. **web-admin 侧 `sys_job` 还没建**。补偿端点现在真实可用了，但**没有任何调度源在打它**，本次是手工 curl 触发的。按 ADR-D132 与 AGENTS.md §2.2.1，**判据是 `SYS_JOB_LOG` 有没有记录，「代码写了」不等于「补偿在跑」** —— 上线前 MUST 建这条 job。
3. `/internal/alipay/channelSync/compensate` **无鉴权**（与同模块另两个 `/internal/**` 同现状），上线前 MUST 一并补。
4. §八 那四项未覆盖仍未覆盖，其中**并发首签**值得优先补：`ALIPAY_SIGN_INFO` 上确认没有 `THIRD_USER_ID + CHANNEL` 唯一索引，而幂等只靠 `select` 短路，理论上并发能插两行。

## 十、第三轮复测（`alipay-pay-sign:1.1.23` / ADR-D135，2026-09-17 16:29）

**1.1.22 → 1.1.23 把「重复签约」的语义改了，§三 与 §9.3 记的那批期望值已部分作废，以本节为准。**

### 10.1 版本与 jar 反查

Deployment `itp/alipay-pay-sign:1.1.23`，Pod `alipay-pay-sign-server-586cb59844-mh8tg`，探活 200。三个 `ChannelSync*` class 仍在；jar 内 mapper 的 `CHANNEL_SYNC` 命中数 **11 → 16**（新增 `reactivateSign` 那条，四列复位写在里面）。

### 10.2 落库分三支（被表结构逼出来的）

`ALIPAY_SIGN_INFO` 主键是 **`THIRD_USER_ID` 单列**、解约只改状态不删行 ⇒ **一个用户全表最多一行**。于是 `addContract` 分三支：有生效签约就短路 / 有历史 `TERMINATED` 行就 `reactivateSign` CAS 改回 `SIGNED` / 都没有才 `INSERT`。**原实现在第二支上 `INSERT`，必撞主键 ⇒ 已解约用户永远签不回来**，这是 1.1.23 修的主要缺陷。

### 10.3 用例与实测（`0700009920` / `cardId=0426090949000317`）

- 开户 → `0000`
- 首签（协议号 `...22894920`）→ `0000`；落库 `SIGNED` + **`CHANNEL_SYNC_STATUS=SUCCESS` / `RC=1` / `SYNC_T=SIGN_T=16:29:56` / `'支付通道已同步'`**，`INSERT` 语句带 `CHANNEL_SYNC_STATUS, CHANNEL_SYNC_RETRY_COUNT` 并写 `'PENDING', 0`，随后一条 `UPDATE ... SET CHANNEL_SYNC_STATUS = 'SUCCESS'` ✅
- 同协议号重签 → `0000 该用户已签约` ✅
- **换协议号重签 → `9999 该用户已存在生效中的签约，请先解约后再签约`** ✅ —— **这是本轮最关键的行为变化**。§9.3 记的「换协议号也返 `0000`」是 1.1.22 的**缺陷行为**：那时不比对协议号、直接返库里的旧号，于是**换号重签被静默吞掉**（新号不落库、上游拿到旧号却以为签成功了）。1.1.23 改成显式拒绝，日志留 ERROR「该用户已有生效签约且协议号不一致，拒绝签约」并带库内/入参两个号。**NEVER 改成覆盖更新**：`CHANNEL_AGREEMENT_CODE` 是销卡通知发给支付中心的号，覆盖等于让旧协议再也解不了约。
- 直连 30022 同协议号 → `0000` + `agreementCode` 为**首签那个号** ✅
- 负面三条（缺字段 / 未开户 / `bizData` 缺失）→ `8001` / `9999 用户未开户，无法签约` / `8001` ✅

拒绝分支**零副作用**：表里仍 1 行，`AGREEMENT_CODE` 与 `CHANNEL_USER_ACCOUNT` 保持首签值（没被那个 `2088999999999999` 覆盖）、`RC` 仍 1、`CHANNEL_SYNC_TIME` 与 `UPDATE_TIME` 都停在 16:29:56 ⇒ 幂等/拒绝都发生在 `syncPaymentChannel` 之前，不重复出网、不动 outbox 四列。

补偿端点再打一次 → `本轮收口 0 条`（三行都已 `SUCCESS`，扫描 SQL 正确排除已收口行）✅

### 10.4 `reactivateSign` 路径本轮**未能覆盖**（阻塞在测试数据，不在环境）

要走那一支必须先有一行 `SIGN_STATUS='TERMINATED'`，而本轮造不出来：

- `POST /channel/terminateContract` → `0000`，只在 `ALIPAY_TERMINATION_REQUEST` 落一行 `STATUS='PENDING'`（`MERCHANT_NO` 为 null），**不动 `ALIPAY_SIGN_INFO`**。
- `GET /channel/executeTermination?agreementCode=...` → **`9001 解约未完成，登记保持 PENDING 待重试`**，签约行仍 `SIGNED`、`ALIPAY_TERMINATION_REQUEST` 仍 `PENDING`。

**成因是测试数据，不是环境不可达 —— 这一条 NEVER 记成「测试环境打不通支付中心」。** 日志原文（`TerminationNotifier.java:161` / `:166`）：

```
支付宝出行-销卡结果通知,调用支付中心, agreementCode=070000992022894920, notifyAgreementNo=2088302232559920
支付宝出行-销卡结果通知未获成功应答, retCode=9999, retMsg=通知推送失败,
  支付中心code=600, msg=未查询到协议信息: 2088302232559920
```

支付中心**是通的**（单次 1306ms、有真实应答），返的是业务码 `600 未查询到协议信息`。出向销卡通知用的 `notifyAgreementNo` 就是签约时上送的 **`CHANNEL_AGREEMENT_CODE`**，而本轮那个值 `2088302232559920` 是**为测试编造的**，支付中心侧不存在该协议。

**因此推出一条硬约束**：**要在真实链路上跑通解约，签约时上送的 `channelAgreementCode` MUST 是支付中心侧真实存在的协议号**（支付宝那边真实签过的），编号造不出来。而 `reactivateSign` 只能在解约成功之后才进得去 —— **`addContract` 的第三支（解约后重签）无法用纯造号数据端到端验证**。

服务侧在这一步的处置是**正确的**：远端没成功就保持登记 `PENDING`、不动签约行，符合「远端先、本地后」。

**业务顺序口径（用户 2026-09-17 明确）**：**换协议号必须先解约、再用新协议号签约；解约请求只带旧协议号，NEVER 带新协议号。** 这与 §10.3 那条 `9999` 拒绝互为正反面。

> **踩过一次的调用坑**：`executeTermination` 是 **`@GetMapping` + `@RequestParam`**（`AlipayPaySignController.java:76`），不是 POST。用 POST 打会返 **`9001 系统内部错误`** 且日志里 `requestParams:` 为空、连「收到执行解约请求」那行都没有 —— 405 被全局异常处理器换成了 9001，**极易误判成解约链路有 bug**。判据：**没有 controller 入口日志就说明没进方法，先查 HTTP 方法与参数绑定形式，NEVER 直接怀疑业务逻辑**。

**两条待办（择一）**：
1. 拿一个**支付中心侧真实存在**的协议号 + 支付宝账号，重跑「开户 → 签约 → 解约 → 换新协议号重签」完整真实链路。
2. 手工把 `0700009920` 那行置 `TERMINATED`，只验 `reactivateSign` 的库侧行为（返 `0000` / 协议号换新 / `TERMINATION_TIME` 回 `NULL` / 四列复位后重新收口 `SUCCESS` 且 `RC` 重新计）——**绕过真实解约那一跳，属半程验证**。还原 SQL 见下。

```sql
UPDATE ALIPAY_SIGN_INFO SET SIGN_STATUS = 'TERMINATED', TERMINATION_TIME = SYSDATE
 WHERE THIRD_USER_ID = '0700009920' AND CHANNEL = 'ALIPAY' AND DELETE_FLAG = '0';

UPDATE ALIPAY_SIGN_INFO
   SET SIGN_STATUS = 'SIGNED', TERMINATION_TIME = NULL,
       AGREEMENT_CODE = '070000992022894920',
       CHANNEL_AGREEMENT_CODE = '2088302232559920',
       CHANNEL_USER_ACCOUNT = '2088302232559920',
       CHANNEL_SYNC_STATUS = 'SUCCESS', CHANNEL_SYNC_RETRY_COUNT = 1,
       CHANNEL_SYNC_RESULT = '支付通道已同步'
 WHERE THIRD_USER_ID = '0700009920' AND CHANNEL = 'ALIPAY' AND DELETE_FLAG = '0';
```
