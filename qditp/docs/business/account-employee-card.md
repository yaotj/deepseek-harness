---
业务域: 账户开户 / 支付通道 / 电子员工卡
模块: account-server, fep-acc-server, alipay-account-server
---

# 提示词：账户与电子员工卡

## 何时读本文件
开户（IF8A-01）、用户信息查询、支付通道增删改与默认通道设置（IF8A-23/24/25/77）、销户（IF8A-42）、更换手机号、HCE 数据更新、电子员工卡（IF3A 域，含激活/禁用）、账户域异常工单，以及支付宝出行开卡相关改动。

## 模块定位
- **account-server**：账户中心，端口 **9098**，`spring.application.name=account`，镜像 `itp/account-server`。真正持久化用户注册与通道信息，8 张表见下方「数据表」。
  - ⚠️ 端口 9098 与 `collect-ticket-server` 冲突，同机部署 **MUST** 提示用户改端口。
- **fep-acc-server**：ACC 侧**员工卡通知的薄接入层**，端口 **9110**，镜像 `itp/fep-acc`。**全模块只有 3 个 java 文件**（`FepAccServer` + `BaseAccController` + `EmployeeCardController`），无 mapper、无数据源、无定时任务、无 MQ，pom 里连 `sql-datasource` / `ojdbc8` 都没有。
  - 认知纠正：本模块**不做** ACC 文件传输、不做对账、不处理 `0xXXXX` 系统级报文。
  - ⚠️ 它的 jkube goals **被整段注释掉**，`mvn package` 只出 jar 不推镜像，出镜像 **MUST** 显式 `mvn k8s:build k8s:push`（AGENTS.md §7）。
- **alipay-account-server**：支付宝渠道开户，端口 **8080**，有自己的 5 张表（`ALIPAY_USER_INFO` / `ALIPAY_REG_LOG` / `ALIPAY_CARD_POOL` / `ALIPAY_PHONE_CHANGE_LOG` + `LOGIC_CARD_POOL_*` 只读）。**与 account-server 零 RPC 耦合**：它没开 `@EnableRpcAccount`、properties 里没有 `service.account.url`，两者互不调用。详见 `docs/business/alipay-channel.md` 与 `docs/domain/decisions.md` ADR-D15。

## 接口清单（20 个端点，2026-09-11 按 Controller 注解逐个核实）

> ⚠️ **account-server 的 8 个 Controller 里，只有 `ItpUserPageController`（`/page/user/itp`）与 `AccountExceptionTicketPageController`（`/page/exception-ticket`）有类级 `@RequestMapping`**，其余 6 个只有 `@RestController`。**包路径（`controller/ci/app`、`controller/ci/channel`、`controller/ci/employee`、`controller/internal`、`controller/task`）不构成任何 URL 前缀** —— `/internal/payChannel/syncPayAccountId` 里的 `/internal` 是方法级注解里写死的字符串，不是包名带出来的。按包名拼路径会被 Spring 当静态资源、日志报 `No static resource ...`、响应退化成全局异常处理器的 UUID `retCode`——2026-09-11 一天内因此白跑三次。**发请求前 MUST 先看类级与方法级注解。**
>
> ⚠️ **Controller 已按「请求来源」分面**（ADR-D35）：`ci/app` 外部（APP 经 fep-app）、`ci/employee` 外部（ACC 经 fep-acc + APP 经 fep-app 两个来源）、`ci/channel` 渠道（零调用点）、`internal` 对内（服务间直连）、`page` 运营后台、`task` 定时任务（web-admin Quartz）。分面判据是**调用方集合零交集**，不是「概念上算内部还是外部」；交集非空的 3 个端点已就地标注，见下。
>
> ⚠️ **24 个端点全部没有鉴权。** `AccountRequestVerifier` 虽在 `RequestApplicationController` 被注入，但**全模块零调用点**（全库 grep 只有 import 与字段声明），是悬空依赖。这与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」冲突，属已知的有意降级，**上线前 MUST 恢复**。ADR-D34/D35 之后**补验签的落点清楚了**：`ci/app` + `ci/employee` 两个外部面整体加，`internal` 两个类不加（服务间直连没有 APP 报文骨架也没有 `sign`），**但 `RequestApplicationController` 里 3 个双来源端点 MUST 单独处理**（给它们加验签等于要求 pay-sign / ticket / gate-txn-pay 也加签）。

**account-server / `controller/ci/app/RequestApplicationController`**（9 个，**外部面**：报文来自 APP 经 fep-app-server，无类级前缀）
| 方法 | 真实路径 | 参数 | 编号 |
|---|---|---|---|
| POST | `/requestApplication` | `@RequestBody RequestApplicationReqDTO` | IF8A-01 |
| POST | `/requestAddPayChannel` | `@RequestBody` | IF8A-23 |
| POST | `/requestSetDefaultPayChannel` | `@RequestBody` | IF8A-24 |
| POST | `/requestRemovePayChannel` | `@RequestBody` | IF8A-25。**双来源**：fep-app + pay-sign `ChannelSyncDeliverer`（解约后清理通道的唯一实现；**原 `PaySignWorkflow.removeAccountPayChannel` 已于 2026-09-15 随 god class 删除**，见 ADR-D87 / ADR-D48） |
| POST | `/requestAgreeRelease` | `@RequestBody RequestRemovePayChannelReqDTO` | 钱包解绑，语义=删通道。**双来源**：fep-app + pay-sign `ContractDomainServiceImpl.releaseWalletBinding`（2026-09-15 前在 `PaySignWorkflow`） |
| POST | `/userCancel` | `@RequestBody UserCancelReqDTO` | IF8A-42 |
| POST | `/queryUserInfo` | `@RequestBody` | **双来源**：fep-app `IndustryDataServiceImpl` + ticket / gate-txn-pay / pay-sign 三个内部模块 |
| POST | `/requestUpdateChannelDefaultContract` | `@RequestBody` | IF8A-77 |
| **GET** | `/updatePhone` | `@RequestParam thirdUserId` + **`newMsisdn`** | if8a_76 落地端 |

> ⚠️ **表里 3 个标「双来源」的端点是 ADR-D35 显式接受的例外**，不是漏整理：APP 与内部模块调的是**同一个幂等动作**，拆成两个 URL 只会造两套语义。**补验签时它们 MUST 单独处理**——不能靠「这个类都是外部面」一刀切。

**account-server / `controller/internal/PayChannelInternalController`**（2 个，**对内面**，无类级前缀，ADR-D34 从上表切出）
| 方法 | 真实路径 | 参数 | 调用方 |
|---|---|---|---|
| POST | `/queryPayChannelByContractNo` | `@RequestBody` | pay-sign（IF8A-75 反查 cardId/cardType） |
| POST | `/internal/payChannel/syncPayAccountId` | `@RequestBody` | pay-sign（回写 `PAY_ACCOUNT_ID`，ADR-D32） |

**account-server / `controller/internal/CardDataInternalController`**（2 个，**对内面**，无类级前缀，ADR-D35 从上表切出）
| 方法 | 真实路径 | 参数 | 调用方 |
|---|---|---|---|
| **GET** | `/queryCardTypeByCardId` | `@RequestParam String cardId` | ticket-server ×3、fep-dev-server ×1 |
| POST | `/updateHceData` | `@RequestBody` | ticket-server、face-pay-server、collect-pay-server |

> ⚠️ **这 4 条对内路径一个字都不能改**：全部硬编码在 `rpc/AccountClient.java`（`:130` / `:136`（`?cardId=` 拼在路径里）/ `:156` / `:165`）。两个 internal controller **刻意都不加类级 `@RequestMapping`**，所以 `/internal/payChannel/syncPayAccountId` 里的 `/internal` 只是方法级注解里的字符串、与包名无关；前缀不统一是既有事实，**NEVER 顺手统一**——改任何一条都会让对应调用方 404，而**编译与单测都发现不了**，要统一 MUST 与 `rpc` 及下游镜像同批改并端到端验证。

**account-server / `controller/ci/employee/EmployeeCardController`**（4 个，**外部面但有两个不同来源**，无类级前缀）
- `POST /employeeCard/notify` — ACC 员工码状态通知（批量 `cardList`），来源 **ACC 经 fep-acc-server**
- `POST /employeeCard/query` — 来源 **APP 经 fep-app-server**
- `POST /employeeCard/activate` — **激活/禁用，`actionFlag` 1=激活 0=禁用**，会真打 ACC；来源 **APP 经 fep-app-server**
- `POST /employeeCard/updateNotify` — 员工信息变更，来源 **ACC 经 fep-acc-server**

> ⚠️ 这个类里 **ACC 来源与 APP 来源各占两个**。两者的验签方式不同（ACC 侧无验签、APP 侧应走 `AccountRequestVerifier`），**补验签时 MUST 按方法区分，NEVER 在类上一刀切**。ADR-D35 本轮未拆它——四个端点共用同一份员工码状态机与 ACC 出网协作者，按来源拆会把一个状态机劈到两个类。

> `IF3A` 编号在 account-server 代码里**完全不存在**（全模块 grep 只命中 IF8A-01/23/24/35/42/75/77 与 IF1A-01），别指望靠 grep 编号定位员工卡代码。

**account-server / `controller/ci/channel/FepAlipayTripRequestApplicationController`**（1 个，无类级前缀）
- `POST /channel/requestApplication` — **保留但不在链路上**，权威实现在 alipay-account-server。**NEVER 把任何模块的 `service.account.url` 指过来处理支付宝开卡**，否则同一 `thirdUserId` 会在两套表里各开一次户且互不可见。见 ADR-D15。

**account-server / `controller/page/ItpUserPageController`**（2 个，**唯一有类级 `@RequestMapping("/page/user/itp")`**）
- `GET /page/user/itp/search` — 入参是 POJO 绑定 query（`queryType` + `keyword`）
- `GET /page/user/itp/pay-channels` — `@RequestParam thirdUserId` + `cardId` + `cardType`，返回含 `terminationReady`

**account-server / `controller/page/AccountExceptionTicketPageController`**（2 个，类级 `@RequestMapping("/page/exception-ticket")`，2026-09-11 新增 / 2.0.57）
- `GET /page/exception-ticket/list` — `ticketStatus` / `ticketType` / `thirdUserId` 三个可选筛选 + `limit`（缺省 50、硬上限 200）
- `POST /page/exception-ticket/close` — `id` + `closedBy`，**CAS 只接受 `OPEN`**，影响 0 行时返 `error("工单不存在或已关闭")`，**NEVER 改成无条件 ok**
- **只做后端，前端页面由前端同学另建**（用户 2026-09-11 决定）；**当前无鉴权**，见下方「已知坑」

**account-server / `controller/task/TaskController`**（2 个，无类级前缀）
- `POST /quartzDemo` — 固定返 `{"retCode":"0000"}`、无副作用。**做 ACC 桩用的就是它**（ADR-D13 的 E2E）
- `POST /phoneSignSyncCompensate` — 换号显示账号同步的重推入口，由 web-admin Quartz 触发

> 这两个端点**不收任何入参**，这是它们可以暂不鉴权的唯一前提，**NEVER 给它们加「按流水号重推」这类外部可控参数**。

**fep-acc-server / `controller/EmployeeCardController`**（2 个，无类级前缀，`consumes=multipart/form-data`）
- `POST /employee_card/notify` → 转发 account-server `POST /employeeCard/notify`
- `POST /employee_card/update_notify` → 转发 account-server `POST /employeeCard/updateNotify`

> ⚠️ **入向用下划线、出向用驼峰**，两套命名都真实存在，别当成笔误改掉。
> ⚠️ `BaseAccController` 全文 23 行，**只有 `parseBizData` 一个方法，没有任何验签代码**。`sign` / `signType` / `timestamp` 被 `ItpCommonFormRequest` 接住后**直接丢弃**，只有 `bizData` 往下传。也就是说这条链路**两端都没有验签**：fep-acc 不校验，转发给 account-server 的又是裸 JSON body、公共头已丢失。


> ⚠️ **if8a_76 更换手机号，改动前必读**：对外契约以规范（`docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx` if8a_76 表117/118）为准——路径**只有** `/app/changePhone`，`bizData` **只认** `newPhone` + `thirdUserId`，应答**只有** `retCode` + `retMsg`。2026-09-09 已严格收敛：曾临时放宽的 3 条路径别名（`updatePhone` / `/ci/app` 前缀）、11 个字段别名、以及应答里恒为 null 的 `thirdUserId` 全部删除，代码内留有 NEVER 注释。**account-server 内部仍叫 `newMsisdn`**（方法签名、`USER_PHONE_CHANGE_LOG.NEW_MSISDN` 列），属实现细节，不必改名。
>
> 规范说明「更换了用户表、普通卡池表、日票卡池表的数据」，但 **2026-09-09 核对生产库：三张卡池表都没有手机号列**（`USER_ACC_TICKETNO` 5 列、`LOGIC_CARD_POOL_CARD`、`DAILY_TICKET_INSTANCE` 均只用 `THIRD_USER_ID` 关联），手机号不冗余存储。**NEVER** 凭规范这句话给卡池表加手机号列——会引入两处真值；如甲方坚持，先确认他们的表结构。
>
> **2026-09-11 补充**：换号除改 `USER_ITP_REG_INFO.MSISDN` + 写 `USER_PHONE_CHANGE_LOG` 外，**还会把员工码的 `PHONE` 一并迁移**（`updatePhoneByThirdUserId`，见下方「员工码与 ITP 用户的挂接」）。这一处**刻意不 catch**：它在事务内，卡表更新失败就应连同换号一起回滚，否则会留下「用户已换号、员工码仍是旧号」的不一致。命中 0 行是正常的（该用户没有员工码）。
>
> 另有两个已知缺陷：①该接口无验签（`signType=00` 免签）、无短信验证码、无归属校验，且是 `@GetMapping`，手机号进 URL 与访问日志；②**旧记载「`catch` 后 `return false` 导致 `@Transactional` 不回滚」已过时**——`updatePhone` 现在**故意不带 `@Transactional`**（它要在事务外调支付域 RPC），落库部分走 `updatePhoneLocally`，见下方跨域同步一节。

## 验签现状（**当前等于没有**）
`account-server/.../service/AccountRequestVerifier.java`（130 行）是入向校验的唯一实现：校验 `providerId / charset / format / timestamp / signType / sign / bizData`，`signType=00` 视为免签、`01` 走 SHA-1、`02` 走 MD5，签名源串按 key 排序拼接 + `&key=${itp.signKey}`（**不是 RSA**）。

> ⚠️ **它从未被调用。** 全模块 grep 只命中 `RequestApplicationController:41` 的 `@Autowired` 字段声明与 import 两处，**没有任何一个端点真的调 `validateCommonRequest` 或 `checkSign`**。因此账户域 24 个端点当前**全部裸暴露**。
> ⚠️ 该类是**无接口的裸 `@Component`**，与其它三组 interface + impl 形态不一致。
> 修改此类 **MUST** 提示人工复核安全合规性；补鉴权 **MUST** 复用它，**NEVER** 自造签名逻辑。

## 数据表（`account-server-schema.sql` 共 8 张 + 2 个序列，7 个 Mapper）

| 表 | 用途 | 唯一键 / 关键约束 | Mapper |
|---|---|---|---|
| `USER_ITP_REG_INFO` | 开户主表（逻辑卡号、票种、手机号、默认通道、HCE 数据）。LIST 分区 by `THIRD_USER_ID_SUFFIX` | **无唯一索引**，只有 3 个 LOCAL 普通索引 | `UserItpRegInfoMapper`（读+写+物理删） |
| `USER_ITP_REG_LOG` | 开户/解绑/销户/归档留痕，**兼「用户信息历史表」** | 无 | `UserItpRegLogMapper`（**只有 insert**） |
| `APP_USER_PAY_CHANNEL` | 用户支付通道绑定。LIST 分区 | **复合主键** `(THIRD_USER_ID, CARD_TYPE, CHANNEL)` | `UserPayChannelMapper`（3 select + insert + delete + **`updatePayAccountIdByReqContractNo`**，2.0.63 起有这一条 UPDATE，见 ADR-D30） |
| `USER_ACC_EMPLOYEE_CARD` | ACC 电子员工码主表 | `UK_UAEC_CARD_NO(CARD_NO)`；`CHECK CARD_STATUS IN (1,2,3,4)` | `UserAccEmployeeCardMapper` |
| `USER_ACC_EMPLOYEE_CARD_LOG` | 员工码操作日志 | `CHECK EVENT_TYPE IN ('OPEN','CHANGE','STATUS','CANCEL')` | `UserAccEmployeeCardLogMapper`（**只有 insert**） |
| `USER_PHONE_CHANGE_LOG` | 换号历史 + 显示账号同步到支付域的投递状态 | 无唯一索引；ID 取 `SEQ_USER_PHONE_CHANGE_LOG` | `UserPhoneChangeLogMapper`（insert + 2 个 CAS update + 扫表 select） |
| `ACCOUNT_EXCEPTION_TICKET` | 账户域异常工单，**account-server 独占，其它域 NEVER 直接写** | **`UK_ACCT_EXC_TICKET_TYPE_KEY(TICKET_TYPE, BIZ_KEY)`**（幂等键）；`IDX_ACCT_EXC_TICKET_STATUS(TICKET_STATUS, CREATE_TMS)`（运营查询）；ID 取 `SEQ_ACCOUNT_EXCEPTION_TICKET` | `AccountExceptionTicketMapper`（`insert` / `selectByCondition` / **CAS `close`**，2026-09-11 补齐；**关单只能人工触发，NEVER 让补偿任务调 `close`**） |
| `USER_ACC_TICKETNO` | **已废弃，保留不删** | `UK_UAT_CARD_ID(CARD_ID)` | **无任何 Mapper** |

> ⚠️ **`USER_ACC_TICKETNO` 没有 mapper、没有 entity、没有任何读写代码**（2026-09-11 全仓 grep 核实）。发号已整体迁至 card-pool-server 的 `LOGIC_CARD_POOL_CARD`。本文件此前点名的 **`UserAccTicketNoMapper` 并不存在**，`account-server/src/main/java/.../mapper/` 下只有上表那 7 个。schema 里已加 `COMMENT ON TABLE` 标注。
> ⚠️ **`APP_USER_PAY_CHANNEL` 有两处 DDL**：`pay-sign-server/src/main/resources/sql/pay-sign-schema.sql` 与 `account-server/src/main/resources/sql/account-server-schema.sql` 各一份，**指向同一张物理表**。给它加列 **MUST 同时改这两份**（2.0.63 加 `PAY_ACCOUNT_ID` 时两份都已同步）；只改一份的后果是「谁先在空库跑另一份 schema，建出来的表就缺列」，而缺列时 account-server 的所有相关 select 都报 `ORA-00904`。注意两份本身还有个既有差异：account 侧带 LIST 分区、pay-sign 侧不带，**本次未动**。
> ✅ **controller 已不再直连 Mapper**（ADR-D27）：`ItpUserPageController` 的查询编排下沉到 `ItpUserQueryService`，`AccountExceptionTicketPageController` 的列表与关单下沉到 `AccountExceptionTicketService`。**NEVER 把 Mapper 或 RPC Client 注回 controller**。
> ✅ **运营查询已无跨域 RPC**（2.0.63，ADR-D30）：`APP_USER_PAY_CHANNEL` 新增 `PAY_ACCOUNT_ID`（**DDL 待执行**），由 IF8A-77 回写；`ItpUserQueryServiceImpl` 改读本地列并**交回 `PaySignClient` 注入**，原先按渠道逐条打 `querySignInfoBySeq` 的 N+1 已删除。三个同名列 **NEVER 混用**：`THIRD_PAY_ID`（账户域自有，APP 上送）/ `PAY_ACCOUNT_ID`（支付中心 `payUserId`，本次同步的）/ `DISPLAY_ACCOUNT`（在支付域但由账户域推送）。**原「新通道行走过 IF8A-77 之前该列为空」的覆盖率退让已由 ADR-D32 补上**：支付域签约落库提交后经 `POST /internal/payChannel/syncPayAccountId` 推一次（**允许失败、不做补偿**）。**NEVER 因为回写可能丢就把 RPC 加回读路径。**
> ⚠️ **IF8A-77 里的 `paySignClient.querySignInfoBySeq` NEVER 改成读本地 `PAY_ACCOUNT_ID`**（ADR-D32 已评估）：那是它要写进 `THIRD_PAY_ID` 的**输入值**，而 APP 只上送 `regSignSeq`；改读本地会形成「IF8A-77 读一个只有 IF8A-77 才写的列」的循环依赖，首次调用必空、直接返 `INVALID_SIGN_DATA`。它与 D30 消掉的那条 N+1 性质不同（那条只是展示用）。
> DDL 位置：主脚本 `account-server/src/main/resources/sql/account-server-schema.sql`；迁移脚本还有 `*-card-type-migration.sql`、`*-companion-flag-migration.sql`、`*-hce-data-migration.sql`、`*-phone-sync-migration.sql`。mapper XML 在 `account-server/src/main/resources/mapper/`（7 个），新增 SQL **MUST** 放这里并走自研 `mybatis-adaptor`。

## 状态列取值全集（本项目用字面量而非枚举，改动前 MUST 全局 grep）

**`USER_ACC_EMPLOYEE_CARD.CARD_STATUS`**（`NUMBER(1)`，Integer）
- `1` 启用 / `2` 禁用 / `3` 未启用 / `4` 注销
- **两个写入方，语义不同**：①`applyActivationResult`（本地发起激活/禁用）走**白名单** —— 激活只收 `3`、禁用只收 `1`，其余返 `2002`；②`applyAccInfo`（ACC 状态通知）**不设白名单、原样落库**，因为 ACC 是权威发卡方、本地应当跟随，在此拒绝会造成永久不一致。**NEVER 把这两处混为一谈去「统一加白名单」。**
- 入参只校验区间 `1..4`（`EmployeeCardServiceImpl.validateCard`）。
- 逆向跃迁（从终态 `4` 回退、回到起始态 `3`）自 **2.0.62 起有告警留痕**：`saveFromStatusNotify` 落库前比旧值，命中即记 WARN 并在 `USER_ACC_EMPLOYEE_CARD_LOG.REMARK` 打 `[逆向跃迁 x->y，已放行待核对]`，**但仍然放行**（ADR-D23，用例 `EmployeeCardBackwardTransitionTest`）。**仍未闭合的是业务口径**：ACC 到底会不会下发这类通知、含义是什么，待甲方确认。

**`USER_ITP_REG_INFO.DEL_YN`**（**极性反直觉**）
- `1` = **有效**，`0` = **已注销**，`NULL` 代码一律当无效。
- `UserItpRegInfoMapper.xml` 里 **10 处** select/update 硬带 `and DEL_YN = 1`。写新查询 **MUST** 带上，**NEVER** 按「1 表示已删除」的常见约定理解。
- 绕过 `DEL_YN` 的后门只有两个：`selectAnyByThirdUserIdAndCardIdAndCardType`（IF8A-75 销户后清通道用）与 `selectAnyListByThirdUserIdForUpdate`。

**`APP_USER_PAY_CHANNEL.STATUS`**
- **只有 `'ACTIVE'` 一个取值，硬编码**，唯一插入点是 `buildUserPayChannel`。
- **没有状态机**：mapper 内无任何 UPDATE，解绑走**物理删除**，因此 `UPDATE_TMS` 恒等于 `CREATE_TMS`。
- 唯一读取比较点是 `ItpUserPageController` 算 `terminationReady`；**所有业务查询都不带 `STATUS` 过滤**。
- **已决定不引软删除**（ADR-D14）：只加状态列而不改全部读点，等于让「已解绑的通道」继续被当成有效，比现在的物理删除更危险。要做 MUST 连读点一起改。

**`USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS`**
- `PENDING`（本地事务内置入）→ `SUCCESS`（终态）/ `FAILED`（`RETRY_COUNT+1`）；`NULL` = 改造前历史行，**有意永不被扫表捞取**。
- 两条 UPDATE 都带 CAS 条件 `AND SIGN_SYNC_STATUS IN ('PENDING','FAILED')`，即 `SUCCESS` 不可回退。

**`ACCOUNT_EXCEPTION_TICKET`**
- `TICKET_STATUS`：`OPEN` / `CLOSED`。**`CLOSED` 在代码中无任何写入点**——mapper 只有 insert，关单只能人工 `UPDATE`。
- `TICKET_TYPE` 两种：`SIGN_SYNC_RETRY_EXHAUSTED`（`BIZ_KEY` = `USER_PHONE_CHANGE_LOG.ID`）、`EMPLOYEE_CARD_STATUS_UNSYNCED`（`BIZ_KEY` = `卡号:目标状态`）。

**`USER_ITP_REG_LOG.OPER_TYPE`**（无 DDL 注释也无字典表；取值分散在三处常量：`1` 在 `PayChannelServiceImpl`、`2` 在 `AccountCancelServiceImpl`、`3` 在 `AccountArchiveServiceImpl`，**新增取值 MUST 三处互相登记**）
- `0` 开户 / `1` 删除支付通道 / `2` 销户 / `3` 销户归档。新增取值 **MUST** 同步这两处。


## 换号后的跨域同步与异常工单
- 换号成功后 **MUST** 把新号作为「签约展示账号」同步给支付域（`PaySignClient.updatePaySignDisplayAccount` → pay-sign `/ci/app/updateDisplayAccount`）。该调用**在事务外**发起（AGENTS.md §5.2：事务内 NEVER 发 RPC），成败一律回写 `USER_PHONE_CHANGE_LOG.SIGN_SYNC_*`。
- 重推入口是 **`POST /phoneSignSyncCompensate`**（`TaskController`），由 web-admin 的 Quartz `sys_job` 触发；**account-server 内 NEVER 加 `@Scheduled`**。扫表条件 `SIGN_SYNC_STATUS IN ('PENDING','FAILED') AND NVL(SIGN_SYNC_RETRY_COUNT,0) < 10`，`SIGN_SYNC_STATUS IS NULL` 的历史行**有意不捞**（改造前的记录没有待投递事实）。该端点**不收任何入参**，这是它可以不鉴权的前提，**NEVER 给它加「按流水号重推」这类外部可控参数**。
- **`updatePaySignDisplayAccount` 的返回值 MUST 检查**：它是「内部 catch 后 return false、从不抛异常」那类方法，而支付域在 `APP_PAY_SIGN_INFO` UPDATE 影响 0 行（该用户无签约记录）时就返回 FAIL。2026-09-11 实测过丢弃返回值的版本：那种行被写成 `SUCCESS`、既不重试也不开工单。
- 重推累计 10 次仍失败 ⇒ 开 `ACCOUNT_EXCEPTION_TICKET` 一张 `TICKET_TYPE='SIGN_SYNC_RETRY_EXHAUSTED'` / `BIZ_KEY=USER_PHONE_CHANGE_LOG.ID` / `TICKET_STATUS='OPEN'`，之后该行被扫表条件过滤、不再自动重推。**关单目前只能手工 `UPDATE`（无流程、无后台入口）**。判据、幂等与实测记录见 `docs/domain/decisions.md` ADR-D13。

## 开户发号：走 card-pool-server 三段式，不再自己发号
`requestApplication`（IF8A-01）**故意不带 `@Transactional`**，编排顺序按 AGENTS.md §5.2「先调远端、后改本地」固定为：
1. `reserveFromPool` 向 card-pool-server **预占**卡号（HCE 票种另经 `SecurityClient` 取卡数据）
2. `ticketClient.registerRideStatus` 注册乘车状态，失败即 `releaseReservation` 并返 `8014`
3. `transactionTemplate` **短事务**落 `USER_ITP_REG_INFO` + `USER_ITP_REG_LOG`
4. `confirmReservation` **确认**预占
5. `attachEmployeeCardsQuietly` 挂员工码（见下节）

> ⚠️ **`confirmReservation` 失败不回滚已提交的开户数据**——卡号已发给用户，回滚才是错的；未确认的预占会被卡池超时回收，此时同一卡号可能被二次发放，因此 **MUST 打 ERROR 供人工核对**。
> ⚠️ 落库失败时乘车状态留在远端等重推：卡池按 `businessId` 幂等发号，重推拿到同一卡号、不会产生第二条乘车状态。
> ⚠️ `CardTypeMapping.toIssueCardType` 对**未识别值原样返回、不抛异常**，脏值会静默流到下游（SQL 命中 0 行、卡池匹配不到分区）。入口 **MUST** 先用 `isSupportedAppCardType` 校验。

## 员工码激活 / 禁用（`POST /employeeCard/activate`）
- `actionFlag` **1=激活、0=禁用**；目标态 `targetStatus = actionFlag==1 ? 1 : 2`。
- **前置状态白名单**：激活只收 `CARD_STATUS=3`、禁用只收 `1`，其余返 `2002`。
- **`@Transactional` 已去掉**，顺序是「校验 → **事务外**调 ACC → ACC 成功后由 `EmployeeCardPersistenceService.applyActivationResult` 在**独立短事务**里提交状态 + 事件日志两条写」。
- **返回码语义 MUST 区分**（这是 APP 能否重试的判据）：
  - `9999` = 调 ACC 失败、**远端未生效**，可以重试
  - `9998` = **ACC 已受理、本地回写失败**，不可简单重试；同时开 `EMPLOYEE_CARD_STATUS_UNSYNCED` 工单（`BIZ_KEY = 卡号:目标状态`）
  - **ACC 自己的业务码原样透出**（如 `1002 参数校验失败：卡号不存在`）
- ⚠️ **ACC 用「HTTP 4xx + 业务错误体」表达参数被拒**（2026-09-11 真机实测 `400 Bad Request: {"retCode":"1002",...}`）。`RestTemplate` 对 4xx 抛 `HttpClientErrorException`，业务体在 `getResponseBodyAsString()` 里。因此 **MUST 在 `catch (RuntimeException)` 之前单独接 `HttpClientErrorException`** 并把 ACC 的码/文案透出，否则会统一降级成 `9999`、把「卡号不存在」这类可自助修正的原因吞掉。已修（见 ADR-D13）。
- ⚠️ **`OPEN_TMS` 曾是死代码**：`applyActivationResult` 里 set 了，但 mapper 的 `update` 没有该列，第一次激活实测 `CARD_STATUS` 变 1 而 `OPEN_TMS` 仍 NULL。**这类「Java 侧 set 了但 mapper 没有该列」的缺陷编译与走查都发现不了，MUST 靠落库回查**。

## 员工码与 ITP 用户的挂接（ADR-D14）
`USER_ACC_EMPLOYEE_CARD.THIRD_USER_ID` 此前**全表 NULL**（`updateThirdUserId` / `selectActiveByPhone` 零调用点）。现在：
- **开户成功后按手机号挂接**：`EmployeeCardPersistenceService.attachEmployeeCardsQuietly(thirdUserId, msisdn)`（ADR-D33 收尾时从 `RegistrationCommitService` 迁来），**MUST 放在 `confirmReservation` 之后（即事务提交之后）**——挂接是补充关联、不是开户的成功条件，放事务内会让挂接失败把一次成功的开户整体回滚。该方法整体 `try/catch` 兜底、**NEVER 向外抛异常、NEVER 带 `@Transactional`**（逐卡独立 CAS，包事务会让一张卡失败连带回滚已挂好的其它卡），影响 0 行只记 WARN（正常情形：卡已挂给别人、或非正常态）。CAS 条件是 `CARD_STATUS = 1 AND (THIRD_USER_ID IS NULL OR = 本人)`。
- **换号时同步迁移员工码手机号**：`updatePhoneByThirdUserId`，**在事务内且刻意不 catch**（理由见前文 if8a_76 一节）。
- 两条分支均已端到端实跑通过（2026-09-11）。

## 错误码
`account-server/src/main/java/.../constant/AccountErrorCodeEnum.java` 共 **25 个**码。除常用的 `0000` / `8001`(参数) / `8004`(未找到) / `9999`(系统) 外，销户前置校验相关的有 `8023`「存在未支付或扣费失败的订单」、`8024`、`8501` / `8502` 等。**改动返回码 MUST 先读该枚举全文**，NEVER 凭印象用码。

## Service 层现状（2026-09-11 已完成六轮拆分）
| 类 | 行数 | 说明 |
|---|---|---|
| ~~`AccountApplicationServiceImpl`~~ | **已删除** | 第六轮（ADR-D25）整类删除：它已退化成「销户 + 查询 + HCE + 两个转发」的杂物间。两个转发方法一并去掉，`RequestApplicationController` / `TaskController` 改直连实现方 |
| `AccountCancelServiceImpl` | 190 | **IF8A-42 销户**（第六轮新增）：未结清校验（`8023` / `8024` fail-closed）+ 短事务落库 + 归档尝试；**不带 `@Transactional`** |
| `AccountProfileServiceImpl` | 187 | **账户资料**（第六轮新增）：`queryUserInfo` / `queryCardTypeByCardId` / `updateHceData`；只读写 `USER_ITP_REG_INFO` 自身字段、**不碰任何状态机** |
| `AccountRegistrationServiceImpl` | 262 | **IF8A-01 APP 渠道开户发号**（2.0.63 新增，**已改完但未部署**）：只有这一个入口，含发号编排、校验、`buildRegInfo`、默认渠道推导、亲情卡判定；**不带 `@Transactional`**。渠道无关的三步委托 `RegistrationCommitService`。**支付宝出行开卡已于 ADR-D31 拆出，NEVER 加回本类** |
| `AlipayTripRegistrationServiceImpl` | 204 | **支付宝出行开卡**（2.0.63 / ADR-D31 从上一行拆出，逐行照搬、行为不变）。**保留但不在链路上**（ADR-D15，权威实现在 alipay-account-server）；唯一调用方 `FepAlipayTripRequestApplicationController`。`buildRegInfo` / `validateRequest` 与 IF8A-01 **真实分叉、NEVER 合并** |
| `RegistrationCommitServiceImpl` | 130 | **渠道无关的开户收口**（2.0.63 / ADR-D31 新增）：`registerRideStatus`（RPC，持 `TicketClient`）、`persistRegistration`（`TransactionTemplate` 短事务写注册行 + 流水行）、`normalizeIssueOrgCode`，私有 `buildTicketRequest` / `buildRegLog` 收在此。**判据：只放开户提交动作本身及其入参归一化**；`isDayPassCard` 与 `attachEmployeeCardsQuietly` 已按此判据移出（后者迁到 `EmployeeCardPersistenceService`，**NEVER 迁回**）；**NEVER 把渠道 if-else 挪进来**（会退化成 ADR-D25 删掉的杂物间） |
| `PayChannelServiceImpl` | 580 | **支付通道的 APP 契约面**（2.0.60 新增；ADR-D34 起只剩 5 个入口）：IF8A-23 / 24 / 77 / 75 解绑 + 钱包 `requestAgreeRelease`；注入 3 个 Mapper + `PaySignClient` + `AccountArchiveService`。**IF8A-77 自 2.0.61 起不带 `@Transactional`**（体内有 RPC，见 ADR-D22），2.0.63 起额外做一次**允许失败**的 `PAY_ACCOUNT_ID` 回写（私有 `syncPayAccountIdToChannelQuietly`，**故意不与业务写同事务**，见 ADR-D30；它是 IF8A-77 的内部步骤、**NEVER 挪到对内契约面**）。**NEVER 把 `queryPayChannelByContractNo` / `syncPayAccountId` 迁回本类** |
| `PayChannelInternalServiceImpl` | 124 | **支付通道的对内契约面**（ADR-D34 从上一行按调用方切出，逐行照搬、行为不变）：`queryPayChannelByContractNo`（按签约号反查通道）+ `syncPayAccountId`（ADR-D32 支付域回写），**只被 pay-sign-server 调**；只注入 `UserPayChannelMapper`、两个方法都不带 `@Transactional`。**不是 Facade**：与上一行零依赖、各持一份 mapper。端点在 `controller/internal/PayChannelInternalController`，两个 URL（`/queryPayChannelByContractNo` 与 `/internal/payChannel/syncPayAccountId`）**硬编码在 `rpc/AccountClient.java:130` / `:156`，前缀不一致是既有事实，改一个字节即 404 且编译与单测都发现不了** |
| `PhoneChangeServiceImpl` | 374 | **换号 + 显示账号同步补偿**，从上面搬出，行为未变 |
| `AccountArchiveServiceImpl` | 105 | **销户归档**（2.0.59 新增）：三条件判定 + `OPER_TYPE=3` 快照 + 物理删除；**两个入口异常语义不同，见下** |
| `CardPoolAllocationServiceImpl` | 165 | **卡池预占 / 确认 / 释放 + HCE 取卡**，纯出网协作者、无业务策略 |
| `EmployeeCardServiceImpl` | **393** | 员工码通知 / 查询 / 激活禁用 / 信息变更；**只留业务策略**（校验、状态白名单、ACC 错误码归类、工单、批次切分）；事件日志改调 `EmployeeCardPersistenceService.recordEvent`，**不再持有 `UserAccEmployeeCardLogMapper`** |
| `EmployeeCardOutboundServiceImpl` | 191 | **员工码出网协作者**（2.0.58 新增）：APP 批量注册、ACC 资料查询、ACC 激活请求 + 报文骨架 + `RestTemplate`；10 个 `@Value` 已收成 `EmployeeCardOutboundProperties`（注入 10 → 2，配置键未变，ADR-D26） |
| `EmployeeCardPersistenceServiceImpl` | 158 | 只放需要独立短事务的落库动作，含 `refreshProfileFromAcc`；**`USER_ACC_EMPLOYEE_CARD_LOG` 的唯一写入点**（对外 `recordEvent`，内部 `insertLog`） |
| `AccountRequestVerifier` | 130 | **无接口的裸 `@Component`**，零调用点 |

> ⚠️ 拆分**只搬位置、没改行为**：`updatePhone` 仍不带 `@Transactional`、`updatePhoneLocally` 内仍无 RPC、员工码手机号同步仍在同一事务内且不被 catch。**第六轮起 `AccountApplicationService` 已删除**，`RequestApplicationController`（换号）与 `TaskController`（补偿）直接注入 `PhoneChangeService`，结果类型 `PhoneChangeService.SignSyncCompensateResult` 也随之搬入实现方接口。**NEVER 再造转发型 service**。
> ⚠️ **支付通道已于 2.0.60 拆成 `PayChannelService`**（第四轮）：6 个入口连同 4 个 `validate*`、`buildUserPayChannel`、`isDuplicateKeyViolation`、`markRollbackOnly`、`WALLET_PAYMENT_CHANNEL`、`OPER_TYPE_REMOVE_PAY_CHANNEL` 与 `PaySignClient` 整段搬走，**逐行照搬、行为不变**；这 6 个方法同时从 `AccountApplicationService` 接口删除，`RequestApplicationController` 改注入 `PayChannelService`，**NEVER 加回原接口**。剩下仍偏大的只有开户发号一块。**销户归档已于 2.0.59 拆成 `AccountArchiveService`**：`archiveIfLastChannelRemoved`（解绑侧，**在调用方事务内、不吞异常、失败整单回滚**）与 `tryArchiveAfterCancel`（销户侧，**自开短事务、吞异常只 warn**）**是两个方法而不是一个 + 开关**，因为两侧异常语义必须不同；**NEVER 合并成一个方法**，合并必然破坏其中一边。归档内部「先 `for update` 取锁、后 count」的顺序照旧不可颠倒。
> ✅ `applyAccInfo` 的两份逐字重复已收口：`EmployeeCardServiceImpl` 那份连同它自己的 `update` 一起上移为 `EmployeeCardPersistenceService.refreshProfileFromAcc`。
> ✅ **`insertLog` 的两份逐字重复也已收口**（2.0.63，ADR-D28）：统一为 `EmployeeCardPersistenceService.recordEvent`，`EmployeeCardServiceImpl` 三个调用点改为委托、并交回 `UserAccEmployeeCardLogMapper` 注入（构造参数 5 → 4）。`recordEvent` **故意不带 `@Transactional`**：事务外调（APP 注册失败 / 落库失败留痕）自动提交、痕迹不被上层失败带走；`updateEmployeeInfo` 里调则按 REQUIRED 并入主事务。**NEVER 给它加 `@Transactional`，更 NEVER 加 `REQUIRES_NEW`** —— 后者会让主表回滚而日志留下，那是改行为不是重构。
> ✅ **开户已按渠道拆成三个类**（2.0.63，ADR-D31）：`AccountRegistrationServiceImpl`（IF8A-01）/ `AlipayTripRegistrationServiceImpl`（支付宝出行）/ `RegistrationCommitServiceImpl`（渠道无关收口）。**顺序很关键：先抽收口、再拆渠道** —— 直接对半劈会把 ADR-D29 刚合并掉的 `buildRegLog` / `buildTicketRequest` 重新分成两份，**拆分本身就是重复的来源**。`FepAlipayTripRequestApplicationController` 改注入 `AlipayTripRegistrationService`，`AccountRegistrationService` 接口**已删掉 `alipayTripRequestApplication`**。**NEVER 把任一渠道入口合回另一个类**。
> ✅ **开户发号两条渠道的流水与乘车状态组装已收口**（2.0.63，ADR-D29；ADR-D31 起这两个方法在 `RegistrationCommitServiceImpl` 内）：删掉 `buildAlipayTripRegLog` / `buildAlipayTicketRequest` 两份**逐字节相同**的副本。`buildTicketRequest` 组装的 `RegisterRideStatusReqDTO` 在 `model` 模块、是跨模块契约，**留两份副本时加字段漏改不会编译失败、只会让 ticket-server 收到 null**，因此**全仓库只准有这一处组装**。**NEVER 因为「支付宝要单独一份」再复制回去**；要按渠道分叉 MUST 在方法内按 `regInfo` 字段判断。反过来，两渠道的 `buildRegInfo` 与 `validateRequest` **入参 DTO 类型不同、字段集合真实分叉，属正当分化、NEVER 合并**（差异逐条记在 `AlipayTripRegistrationServiceImpl#buildRegInfo` 的 Javadoc 里）。
> ✅ **账户域已无「事务内发起 RPC」**（2.0.61）：`requestUpdateChannelDefaultContract`（IF8A-77）的 `@Transactional` 已删除——它体内要调 `paySignClient.querySignInfoBySeq`，而写操作只有一条 UPDATE、单语句自身原子，不需要事务。同批用脚本扫过全模块所有 `@Transactional` 方法体，`*Client.` / `employeeCardOutboundService.` / `restTemplate.` **零命中**。**NEVER 把该注解加回去**；将来这里要写第二张表，MUST 用 `TransactionTemplate` 只包两条写、RPC 留在事务外。见 ADR-D22。
> ✅ **员工码出网已收口到 `EmployeeCardOutboundService`（2.0.58）**：`postFormData` / APP 注册 / ACC 查询 / ACC 激活请求与 8 个配置项 + `RestTemplate` 全部搬走，`EmployeeCardServiceImpl` 561 → 403 行、不再持有任何出网地址。**「4xx 透传 ACC 业务码、5xx 与超时归 9999」这条判断留在 `activateEmployeeCard` 的 catch 块里，NEVER 挪进出网协作者** —— 挪过去就等于让「参数被拒」和「远端不可用」在调用方眼里变成同一件事。

## 编码约束
- **`USER_ITP_REG_INFO.DEL_YN` 的极性是反直觉的**，取值与全部过滤点见上方「状态列取值全集」。**行号会漂，按方法名定位，NEVER 记行号。**
- **销户分两段，不要只看一段**：IF8A-42 先原地标记（`DEL_YN=0` + `DEL_THIRD_USER_ID` + `UN_REG_TMS`，`updateCancelByThirdUserId`），记录仍在原表；等 IF8A-75 把**最后一个签约渠道**解绑掉，`requestRemovePayChannel` 的 `archiveUserInfoIfLastChannelRemoved` 才做归档——往 `USER_ITP_REG_LOG` 写 `OPER_TYPE=3` 快照后 `deleteCanceledByThirdUserId` 物理删原表行（WHERE 带 `DEL_YN = 0`，不误删并发新开户）。**「用户信息历史表」就是 `USER_ITP_REG_LOG`**（用户 2026-09-08 裁决复用），因此 **NEVER 新建 `*_HISTORY` 表**——同一事实两处存储、既有查询都不认。代价是该表只有 6 个业务列，原表 19 列中的 `ITP_CARD_TYPE` / `USER_NAME` / `USER_ID` / `CARD_ISSUE_CODE` / `REG_TMS` 等 13 列归档后不可恢复，已被接受。
  - ⚠️ `archiveUserInfoIfLastChannelRemoved` 内部**先 `for update` 取锁、后 count**，这个顺序不可颠倒。
- **归档有两个触发点，NEVER 只在解绑侧调**（2026-09-09 修复）：除 `requestRemovePayChannel` 外，`userCancel` 的**两条出口**（正常销户提交后、以及「无有效开户记录」的幂等分支）也各调一次 `tryArchiveAfterCancel`。原因是实测存在**反序场景**——用户先把支付通道全解绑、之后才销户（`00522946`：通道 10:07 / 10:45 已删完，14:24 才销户），此时 75 那条路径永不再触发，归档三条件明明全满足却没有代码去检查，记录以 `DEL_YN=0` 永久残留。补上销户侧后，对残留数据**重复调一次 IF8A-42 即可收口**（实测 14:50:33 归档成功）。`tryArchiveAfterCancel` 吞异常只记 warn：归档幂等、失败下次再试，**NEVER** 让归档失败把已成功的销户翻成失败。
- **`requestRemovePayChannel` 的 `cardType` 映射 MUST 在参数校验之后做**（2026-09-09 修复）：原先方法第一行就是 `CardTypeMapping.toIssueCardType(request.getCardType().trim())`，`cardType` 为 null 直接 NPE，冒到全局异常处理器后 `retCode` 退化成 UUID，调用方（解约回调）只能判失败并回滚。已发生事故：支付宝已解约、本地 `APP_USER_PAY_CHANNEL` 与 `APP_PAY_SIGN_INFO` 全部回退。现在先 `validateRemovePayChannelRequest` 返回 `8001` + 明确原因，再做映射。
- **归档时点 NEVER 提前到 42**：42 时支付通道还没解绑，删掉原表行会让 75 解约成功分支回调清通道返 `8004`，且 `selectAnyByThirdUserIdAndCardIdAndCardType`（忽略 `DEL_YN` 的兜底）同时失效，通道永久残留。触发归档 **MUST** 先确认 `UserPayChannelMapper.countByThirdUserId` 为 0（thirdUserId 全量口径），且该用户所有开户记录都是 `DEL_YN=0`。
- **`USER_ITP_REG_LOG.OPER_TYPE` 字典**见上方「状态列取值全集」。新增取值 **MUST** 同步常量区与本文件两处。
- **account-server 的依赖注入一律走构造器，`main` 下 `@Autowired` 已归零**（ADR-D37）：14 个类共 45 处字段注入改成构造器注入、依赖字段全部 `private final`；**单构造器不写 `@Autowired`**（Spring 4.3+ 自动注入，样板 `controller/task/TaskController`）。**NEVER 在本模块新增 `@Autowired` 字段**，也 **NEVER 引 Lombok `@RequiredArgsConstructor`**（新增依赖）。连带两条：①单测 MUST 用 `new XxxServiceImpl(mockA, mockB, ...)` 装配，**NEVER 再用 `ReflectionTestUtils.setField` 注依赖**（全模块只剩 `AccountCancelServiceTest` 的 2 处，且只用来翻 `@Value` 开关 `checkUnsettledBeforeCancel`，那个字段**刻意不加 final、刻意不进构造器**）；②「缺 Bean」从运行时首次调用提前到**启动即失败**，因此**部署后 MUST 先确认端口在听**（2.0.47 有过「Pod 2/2 Running 但端口不监听」的先例）。
- **配置项优先 `@ConfigurationProperties`，`main` 下只剩 2 处 `@Value`**：`EmployeeCardOutboundProperties`（前缀 `employee-card`，ADR-D26）与 `ItpSignProperties`（前缀 `itp`，ADR-D37，收 `providerId` / `charset` / `format` / `signKey`）。刻意保留 `@Value` 的两处是 `AccountCancelServiceImpl.checkUnsettledBeforeCancel` 与 `EmployeeCardServiceImpl.appBatchSize`，原因见上一条与 ADR-D18。**⚠️ `itp.signKey` 仍带明文默认值**（代码与 `application.properties` **两处都有**），上线前 MUST 改 `${ITP_SIGN_KEY:}` 并轮换，**只改一处等于没改**。
- **`requestAgreeRelease` → `requestRemovePayChannel` 是类内自调用，Spring 事务代理不生效**（ADR-D37 复核）：被调方法的 `@Transactional` 在这条路径上被忽略，真正开事务的是 `requestAgreeRelease` 自己，当前正确**纯粹因为两者事务配置一字不差**。**NEVER 删外层注解**（以为「委托的那个有」），**NEVER 只改其中一个的传播级别或 rollbackFor**。
- **`AccountRequestVerifier` 用的是 fastjson 1，不是 Fastjson2**，与 AGENTS.md §5.1 冲突但**刻意不换**：`buildSignSource` 靠 `SerializerFeature.MapSortField` 决定 `bizData` 的序列化字节，换库即改变签名源串、已发出的 sign 全部失配（§5.2 安全红线）。要换 MUST 与上游同批改并端到端比对签名。
- **account-server 已装配 5 个 RPC Client**：`CardPoolClient` / `SecurityClient` 在 `CardPoolAllocationServiceImpl`，`TicketClient` 在 `RegistrationCommitServiceImpl`（2.0.63 / ADR-D31 起，此前在 `AccountRegistrationServiceImpl`），`GateTxnPayClient` 在 `AccountCancelServiceImpl`，`PaySignClient` 在 `PayChannelServiceImpl` 与 `PhoneChangeServiceImpl` 两处。需要在账户侧发起这些调用**直接复用现有字段或注入对应协作者**，**NEVER** 另加 `@EnableRpcXxx` 或自建 WebClient。`PaySignClient` 已用于 `querySignInfoBySeq` 与 `updatePaySignDisplayAccount`。
  - ⚠️ `service.paySign.url` 在仓库 properties 里写的是 `http://127.0.0.1:9096`，**在 K8s 内等于打到自身 Pod**。判断线上真实取值 **MUST 查 Deployment env**，仓库配置不等于线上（AGENTS.md §8）。
  - ⚠️ **员工码链路是唯一不走 `rpc` 模块的出网**：它对 APP / ACC 用的是 `RestTemplate` + `employee-card.*-url`（历史形态，对端不是 ITP 内部服务）。这份 `RestTemplate` 自 2.0.58 起**只存在于 `EmployeeCardOutboundServiceImpl`**，配置项自 ADR-D26 起**只存在于 `EmployeeCardOutboundProperties`**，**NEVER 在别处再 new 一个 RestTemplate 或再用 `@Value` 读一遍这些键**。
- **同一接口编号可能已在别处实现**：`requestAgreeRelease` 已由 `PayChannelServiceImpl` 实现（2.0.60 前在 `AccountApplicationServiceImpl`）（语义=删除支付通道，直接委托 `requestRemovePayChannel`，方法体 9 行），链路是 `fep-app-server/AppAccountController` → `rpc/AccountClient`。接到「解绑 / 移除签约」类需求 **MUST** 先 grep 方法名确认是否已有实现，**NEVER** 在 pay-sign 侧再造一份同名端点。
- 卡类型判断 **MUST** 复用 `model` 模块的 `CardTypeCodeEnum` / `CardTypeMapping`，**NEVER** 在业务层硬编码卡类型字符串。
- 支付通道的"默认通道"与 pay-sign 的签约状态是两套数据：改动默认通道 **MUST** 同步确认 `APP_PAY_SIGN_INFO` 是否存在有效签约，否则会出现"有默认通道但无签约"的脏状态。
- 员工卡通知是 ACC 单向推送，无回执重试机制；新增字段 **MUST** 保证对旧报文向后兼容（缺字段不报错）。

## 测试现状
- `account-server/src/test/` 下有 **2 个测试类、15 条用例**：`AccountCardPoolAllocationTest`（开户发号编排，8 条；2.0.63 起被测类是 `AccountRegistrationServiceImpl`）与 `EmployeeCardBackwardTransitionTest`（ACC 状态通知逆向跃迁留痕，7 条，2.0.62 新增）。**跑法 `mise exec -- mvn -o test`**。
  - ⚠️ **拆分后的装配套路：真实协作者 + mock 注进协作者**。`AccountCardPoolAllocationTest` 里 `cardPoolClient` 要 `setField` 进真实的 `CardPoolAllocationServiceImpl`，`ticketClient` / `userItpRegLogMapper` / `transactionTemplate` 要 `setField` 进真实的 `RegistrationCommitServiceImpl`（ADR-D31）。**`userItpRegInfoMapper` 两处都要注** —— 渠道 service 用它查重、commit service 用它落库；只注一处时 7 条测试全返 `9001`（NPE 被兜底成 SYSTEM_ERROR），**这是装配漏注、不是断言写错**。
- 换号事务重排、补偿任务、异常工单、员工码激活/禁用、员工码挂接**仍无单元测试**，验证方式是「合成数据 + 集群内 curl + 落库回查」，实测记录见 ADR-D13 / ADR-D14 / ADR-D15。
- ⚠️ **构建默认 `-DskipTests`，因此测试挂了不会拦住发版**：`AccountCardPoolAllocationTest` 自 2.0.56 第一轮拆分起就因 `setField(service, "cardPoolClient", ...)` 找不到字段而全量报错，直到 2.0.62 才被发现修好（见 ADR-D23）。**改完代码 MUST 至少跑一次 `mvn -o test`**，NEVER 只依赖 `mvn clean package -DskipTests` 的 BUILD SUCCESS。
- ⚠️ **「Java 侧 set 了但 mapper 没有该列」这类缺陷编译与走查都发现不了，MUST 靠落库回查**（`OPEN_TMS` 已发生过一次）。

## 待确认 / 未闭合（改动前先看这里，别重新推导）
- **20 个端点全部无鉴权**，`AccountRequestVerifier` 零调用点。上线前 MUST 恢复。
- **支付宝出行开卡有两套实现**，权威在 alipay-account-server，account-server 那套保留不用。见 ADR-D15。
- ~~支付宝换号是否要同步支付域显示账号~~ → **用户 2026-09-11 裁定：不需要，NEVER 添加**（支付宝走自有代扣、支付域无签约行，推过去必然 FAIL 并造出永远重推不成的 `FAILED` + 异常工单）。判据见 `AlipayAccountServiceImpl.updatePhone` Javadoc 与 ADR-D15 旁注。
- **员工码逆向跃迁（`4→1`、`2→3`）无告警留痕**，待甲方确认 ACC 是否会下发。
- ~~**异常工单没有关单流程、没有后台入口**，`CLOSED` 无代码写入点~~ → **2026-09-11 / 2.0.57 已补后端两个端点**（`GET /page/exception-ticket/list` + `POST /page/exception-ticket/close`，CAS 只接 `OPEN`）。剩下两项**仍未闭合**：①**前端页面未建**（按用户决定交前端同学）；②**关单端点无鉴权** —— 用户 2026-09-11 选择「对齐现状，暂不加鉴权」，与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」冲突，**属有意为之的临时降级，上线前 MUST 补齐**（形态对齐 `AccountRequestVerifier`，NEVER 自造签名）。在此之前 `CLOSED_BY` 只是线索、**NEVER 当审计凭据**。
- **`buildAlipayTripRegInfo` 与 `buildRegInfo` 有三处刻意差异**，2026-09-11 实测重新分级：
  - **`COMPANION_FLAG` 缺字段 —— 真阻塞，卡在支付宝侧报文**。支付宝请求 DTO 没有该字段，而 IF8A-77 按 `COMPANION_FLAG='C'` 定位（`selectByThirdUserIdAndCardIssueCodeAndCompanionFlag`），所以支付宝开的卡到不了 IF8A-77。实测影响面：全库 `COMPANION_FLAG='C'` 仅 **12 行、全部 `ISSUE_ORG_CODE=0008`（成都地铁）/ `CARD_TYPE=0441`**，即 IF8A-77 目前只服务这 12 张卡。**MUST 先由支付宝渠道在报文里给出该标识，NEVER 在本侧硬填 'C'**（那等于把本人卡并进亲情卡集合）。
  - ~~`CARD_ISSUE_CODE` 未归一化，待甲方对齐~~ → **已撤回，不是阻塞项**。该列语义是**发行渠道码**（`"00" + IssueChannelCodeEnum.code`），机构原值在 `ISSUE_ORG_CODE`；支付宝出行机构码 `0007` 归一化后正是 `"00"+ALIPAY("07")`=`0007`，**与原值逐字相同**。全库 32 行该列单一取值 `0001`（青岛 5412 / 成都 0008 两个 NORMAL 机构的归一化结果），**零行需迁移**。只在支付宝上送 `0007` 以外机构码时才分叉。要改直接改成调 `toIssueChannelCode4`。
  - `HCE_DATA` 留空 —— 本方法只接 `cardId`，没有 HCE 分配环节，属设计。
- **`AccountApplicationServiceImpl` 已整类删除**（1904 行 → 0，六轮拆出 `PhoneChangeServiceImpl` / `CardPoolAllocationServiceImpl` / `AccountArchiveServiceImpl` / `PayChannelServiceImpl` / `AccountRegistrationServiceImpl` / `AccountCancelServiceImpl` / `AccountProfileServiceImpl`；第五轮拆出的开户块随后又按渠道再拆成三个类，见 ADR-D31）。**NEVER 重建这个类或任何「转发型」service** —— 第六轮删掉的两个转发方法就是它退化成杂物间的起点；`applyAccInfo` 重复实现已收口。**⚠️ 第五轮（2.0.63）只有编译与单测证据，镜像未构建未部署**（用户 2026-09-11 通知服务器不可部署），补验步骤见 ADR-D24。**`EmployeeCardServiceImpl` 已从 561 行降到 393 行**（拆出 `EmployeeCardOutboundServiceImpl`，再交回日志 mapper）。
- ~~账户域仍无任何单元测试（`account-server/src/test` 不存在）~~ —— **这句话是错的，已于 2026-09-11 更正**：`src/test` 一直存在（`AccountCardPoolAllocationTest`），只是构建带 `-DskipTests` 从不执行，且它自 2.0.56 起已被拆分改坏。详见 ADR-D23。

## 参考原始文档
- `docs/接口规范文档/青岛地铁电子员工卡接口技术规格说明书22.docx`
- `docs/接口规范文档/青岛地铁电子员工卡接口描述.docx`
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`（IF8A-01/23/24/25/77、if8a_76 表117/118）
- 领域决策与实测记录：`docs/domain/decisions.md` ADR-D2 / D3 / D4 / D8 / D12 / **D13**（异常工单）/ **D14**（员工码挂接）/ **D15**（支付宝两套实现收口）
- 支付宝渠道全貌：`docs/business/alipay-channel.md`

## 附：account-server 源码注释知识抽取（2026-09-16，阶段一）

抽取范围：`account-server/src/main/java/**/*.java`（70 个文件、8109 行）与
`account-server/src/main/resources/mapper/*.xml`（8 个文件、880 行）的全部注释。本阶段只读代码、不改源文件。
归档三类（契约与判据 / 决策理由 / 陷阱），墓碑注释单列文末。丢弃复述方法名参数名的普通 Javadoc、`{@inheritDoc}`、空 Javadoc。
每条给出 `类名.方法名` + 文件路径:行号；MUST / NEVER 原文保留，ADR 编号原样保留。
下文路径省略公共前缀 `account-server/src/main/java/com/chinasofti/huateng/account/`（写作 `<J>/`）
与 `account-server/src/main/resources/mapper/`（写作 `<X>/`）。

### 一、开户（IF8A-01 / 支付宝出行开卡 / 销户与归档）

#### 契约与判据

- **IF8A-01 编排顺序与「本方法故意不带 `@Transactional`」** —— `AccountRegistrationServiceImpl.requestApplication`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:105-113`）：「卡池预占、安全服务发号、ticket-server 注册乘车状态、
  卡池确认 / 释放全是 RPC，AGENTS.md §5.2 禁止把网络调用包在事务里。编排顺序按同节『先调远端、后改本地』：
  预占卡号 → 注册乘车状态 → 短事务落库 → 确认预占」；「落库失败时乘车状态留在远端等重推（cardId 由卡池按 businessId 幂等发放，
  重推拿到的是同一张卡号，不会产生第二条乘车状态）；**任何失败分支都不 release 预占**（ADR-D52）」，
  滞留预占「一律由 `sys_job` 107『卡池维护』的超时回收兜底」。
- **两行落库必须一起成立** —— `RegistrationCommitService.persistRegistration`（`<J>/service/RegistrationCommitService.java:44-50`）：
  「两条写必须一起成立（流水行是注册行的凭证），因此这里**确实需要事务**——与 ADR-D30 里『展示列回写不包事务』是相反的情形，
  判据同样是『两条写是否必须一起成立』。实现用 `TransactionTemplate` 而非 `@Transactional`，
  以保证调用方的 RPC **不被**卷进这个事务。」
- **注册乘车状态返回 null 即失败** —— `RegistrationCommitService.registerRideStatus`（`<J>/service/RegistrationCommitService.java:33-41`）：
  「**RPC，MUST 在事务外调用**」「按 AGENTS.md §5.2『先调远端、后改本地』，调用方 MUST 在本方法成功后才落库；
  失败时 MUST 释放卡池预占」；返回值「**`null` 表示不可用**，调用方 MUST 视为失败」。
- **撞唯一索引兜底的返回字段契约** —— `AccountRegistrationServiceImpl.handleDuplicateRegistration`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:265-268`）：「返回字段 **MUST** 与前置查重分支逐字段一致
  （`cardId` / `cardType` / `signType="00"` / `sign=""`）：APP 侧对这两条路径用同一段解析代码。
  回查为空时（冲突后另一条并发把该行销户了）只填错误码，**NEVER** 回填本次未落库的 `regInfo.cardId`。」
- **卡池发号的幂等边界** —— `AccountRegistrationServiceImpl.buildBusinessId`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:335-341`）：「单卡场景（`companionFlag` 非 Y/C）用 `thirdUserId:票种`，
  重复请求拿到同一张卡号，上游重推不会额外消耗号段。同行票 / 第三方票（Y/C）按业务定义『每次请求都给一张新卡』，
  因此追加一次性 UUID —— 这类请求 **不具备幂等性**，重推会多发一张卡，这是业务要求而非缺陷。」
- **HCE 票种不进卡池** —— `AccountRegistrationServiceImpl.allocateCard`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:314-320`）：「`03`（HCE卡）和 `04`（新版HCE卡）由安全服务生成 HCE 卡数据
  并返回逻辑卡号，不进卡池、也没有预占可确认；其余票种一律从 card-pool-server 预占。**本方法内部全是 RPC，MUST 在事务外调用。**」
- **`DEL_YN` 极性与判活入口** —— `UserItpRegInfo.delYn` / `isActive` / `isCanceled`
  （`<J>/entity/UserItpRegInfo.java:45-51`、`:180-198`）：「删除标志。**极性反直觉：`1`=有效、`0`=已注销**」，
  「判活 **MUST** 走 `isActive()` / `isCanceled()`，**NEVER** 在业务代码里裸写 `getDelYn() != 1`」；
  且 `isCanceled()`「**与 `!isActive()` 不等价**：`delYn` 为 null 时两者都返回 false / true 各一次。
  销户归档的三条件判定要求『该用户所有记录都**确实**是注销态』，因此 MUST 用本方法而不是取 `isActive()` 的反」。
- **`CARD_ISSUE_CODE` 与 `ISSUE_ORG_CODE` 的语义分工** —— `UserItpRegInfo.cardIssueCode` / `issueOrgCode`
  （`<J>/entity/UserItpRegInfo.java:74-87`）：`CARD_ISSUE_CODE`「仅 `0001`(正常渠道) / `0007`(支付宝出行)」，
  「由 `CardIssueOrgEnum.toIssueChannelCode4` 从 APP 上送的机构码归一而来；码体的『发行渠道位』是 industry-data-server
  对本值取右 2 位（`07` / `01`）。**NEVER 把 APP 原值直接写进这里**——那会让码体落到非法渠道值，见 B14」；
  `ISSUE_ORG_CODE` 是「APP 开户上送的 4 位原值」「只作留痕与后续统计用，**不参与码体拼装**」。
- **未知发卡机构码只告警不拒绝** —— `RegistrationCommitService.normalizeIssueOrgCode`
  （`<J>/service/RegistrationCommitService.java:56-60`）：「留存 APP / 渠道上送的发卡机构码原值；未知机构码打 ERROR 但**不拒绝开户**。」
- **销户契约** —— `AccountCancelService.userCancel`（`<J>/service/AccountCancelService.java:17-27`）：
  「APP 顺序是 IF8A-35 → IF8A-42 → IF8A-75，本接口执行时支付渠道尚未解绑，因此只改 `USER_ITP_REG_INFO` 与写 `USER_ITP_REG_LOG`，
  **不删 `USER_PAY_CHANNEL`**。落库前会调 IF8A-35 校验未结清订单（可用 `app.user-cancel.check-unsettled` 关闭）」；
  「已注销用户重复调用返回 `0000`（幂等），**NEVER** 返回 8004」；「**不校验『进行中行程』**——用户 2026-09-11 裁决，见 ADR-D20」。
- **销户前置校验 fail-closed** —— `AccountCancelServiceImpl.checkUnsettledOrders`
  （`<J>/service/impl/AccountCancelServiceImpl.java:140-146`、`:157`、`:162`）：「**只校验账务，NEVER 加『进行中行程 / 未出站』校验**：
  用户 2026-09-11 明确裁决『销户不校验进行中行程』，见 ADR-D20。进站未出站的场景由本方法的 `8023` 欠费分支间接兜住；
  账户域也不应反向直读行程域的 `QRCODE_STATUS`」；「fail-closed：查不到账务状态时 NEVER 放行销户，否则欠费用户注销后无法追缴」；
  「IF8A-35 查询未执行时两个数量恒为 0，MUST 先判 retCode，NEVER 直接当成『无欠费』」。
- **`USER_ITP_REG_LOG.OPER_TYPE` 取值表** —— `AccountCancelServiceImpl.OPER_TYPE_CANCEL`
  （`<J>/service/impl/AccountCancelServiceImpl.java:35-39`）：「该列无字典表也无 DDL 注释，现存唯一用法是解约写 1
  （见 `PayChannelServiceImpl.requestRemovePayChannel`，2026-09-11 第四轮拆分后常量 1 也随之搬去那边），销户取 2，销户归档取 3。
  新增取值 **MUST** 在此登记并同步 `docs/business/account-employee-card.md`」；解约侧同一条登记在
  `PayChannelServiceImpl`（`<J>/service/impl/PayChannelServiceImpl.java:55-58`，「新增取值 **MUST** 两处同步」），
  归档侧在 `AccountArchiveServiceImpl`（`<J>/service/impl/AccountArchiveServiceImpl.java:36`）。
- **归档三条件与判定顺序** —— `AccountArchiveService.archiveIfLastChannelRemoved`（`<J>/service/AccountArchiveService.java:23-29`）：
  「触发条件三个同时满足，任一不满足即原样返回：①该用户在 `APP_USER_PAY_CHANNEL` 已无任何通道；②`USER_ITP_REG_INFO` 还有记录；
  ③这些记录**全部**是注销态（`DEL_YN = 0`，IF8A-42 已执行）」；`ArchiveDecision.decide`（`<J>/domain/ArchiveDecision.java:57-66`）：
  「三个条件的**顺序不可调换**，与调用方的取数顺序对应：先确认有记录（否则后两步无意义），再看通道是否清空（这一步在调用方是持锁后的 count），
  最后才逐行看注销状态」，入参说明「该用户的**全部**开户记录（不是『有效』口径 —— 归档发生在 `DEL_YN` 已置 0 之后，用有效口径查必然是空集）」。
- **归档是本域派生规则、不由支付域驱动** —— `AccountArchiveService`（`<J>/service/AccountArchiveService.java:6-8`）：
  「**归档是本域自己算出来的派生规则，不是被谁远程驱动的一次状态迁移** ——『最后一个通道解绑 ⇒ 销户归档』由 account-server 判定，
  pay-sign-server 只负责把『通道已解绑』这个事实送到。判据见 `docs/domain/README.md` §三第 4 条，
  **NEVER 改成由支付域调一个『请归档』的端点**」；「归档本身幂等：三条件任一不满足即原样返回、不动数据，因此重复调用安全」。
- **销户后补一次归档的反序场景** —— `AccountArchiveService.tryArchiveAfterCancel`（`<J>/service/AccountArchiveService.java:33-41`）：
  「实测存在**反序场景**——用户先把通道全解绑、之后才销户（`00522946`：通道 10:07/10:45 删完，14:24 才销户），
  此时 `archiveIfLastChannelRemoved` 那条路径永远不会再被触发，归档三条件明明全满足却没有代码去检查，记录以 `DEL_YN=0` 永久残留」；
  「**NEVER 让本方法抛异常**：归档幂等、下次调用会重试，翻掉已成功的销户会让 APP 陷入重试」。
- **支付宝出行开卡与 alipay-account-server 双写风险** —— `AlipayTripRegistrationService`（`<J>/service/AlipayTripRegistrationService.java:9-14`）
  与 `FepAlipayTripRequestApplicationController`（`<J>/controller/ci/channel/FepAlipayTripRequestApplicationController.java:12-28`）：
  「两套的入口 URL 与 DTO 完全相同，但落表与查重键不同——本实现落 `USER_ITP_REG_INFO`，那套落 `ALIPAY_USER_INFO`，**互相看不见**。
  因此 **NEVER 把任何模块的 `service.account.url` 指过来处理支付宝开卡**，否则同一 `thirdUserId` 会在两边各开一次户」，
  Controller 侧补记「两套查重键互不相交……属真实双写风险」与「全仓库 grep `accountClient.alipayTripRequestApplication` 零调用点（2026-09-11 核实）」。

#### 决策理由

- **为什么按渠道拆开户实现（ADR-D31）** —— `AccountRegistrationServiceImpl` 类注释
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:32-39`）：「**支付宝出行开卡已搬到 `AlipayTripRegistrationServiceImpl`**，
  本类只留 APP 渠道。**NEVER 把任何渠道的开卡入口加回本类** —— 此前两个渠道塞在一个类里、靠方法名前缀区分，
  直接后果是产生过两对逐字节重复的 helper（ADR-D29 清理）」；对应 `AlipayTripRegistrationServiceImpl:28-31` 记「逐行照搬、行为不变」。
- **为什么两个 `buildRegInfo` 不合并** —— `AccountRegistrationServiceImpl.buildRegInfo`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:383-388`）：「与支付宝出行渠道那份**入参 DTO 类型不同、字段集合真实分叉**
  （`COMPANION_FLAG` / `HCE_DATA` / `CARD_ISSUE_CODE` 归一化），属正当分化，**NEVER 合并成一个方法** ——
  合并只能靠 `instanceof` 或再造中间 DTO，两者都更差」；
  支付宝侧三处刻意差异见 `AlipayTripRegistrationServiceImpl.buildRegInfo`（`<J>/service/impl/AlipayTripRegistrationServiceImpl.java:214-244`）：
  `COMPANION_FLAG` 留空导致「**支付宝出行开的卡走不到 IF8A-77**。要让它能走，MUST 先由支付宝渠道在报文里给出该标识，
  **NEVER 在这里硬填 'C'**——那等于把本人卡错误并入亲情卡集合」；`CARD_ISSUE_CODE` 存原值一项已「**已撤回**……
  **真要改就直接改成调 `toIssueChannelCode4`，NEVER 再把它当成需要甲方决策的阻塞项**」。
- **唯一索引 `UK_UIRI_ACTIVE_USER_CARDTYPE` 用函数索引承载多卡语义** —— `AccountRegistrationServiceImpl.handleDuplicateRegistration`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:247-263`）：「真正的防线是数据库唯一索引 `UK_UIRI_ACTIVE_USER_CARDTYPE`，
  本方法只负责把索引抛出的冲突翻译成业务响应」，「**该索引已于 2026-09-14 在 AFCITPDB 建好并回查**（`UNIQUE` /
  `FUNCTION-BASED NORMAL` / `VALID`，两列表达式与 schema 文件一致），因此本分支现在是可达的 —— 本段此前写『尚未执行、
  本分支不可达』，**已作废、NEVER 回退**。**NEVER** 把索引改成普通索引或删掉本方法」，
  并注明「**本分支至今没有被真实请求走到过**……唯一索引防的是『两条都插进来』，而当前编排下更早暴露的是『共享预占被兄弟请求释放』，
  两者是不同的缺陷，**NEVER 把本分支的存在当成并发已闭环的证据**」（ADR-D52）；
  索引谓词与查重口径的对齐要求写在 `<X>/UserItpRegInfoMapper.xml:70-78`：「本人主卡的查重口径，MUST 与唯一索引
  `UK_UIRI_ACTIVE_USER_CARDTYPE` 的谓词逐字对齐：索引刻意把 `COMPANION_FLAG` 为 Y（同行）/ C（第三方代开）的行排除在唯一性之外（多卡合法），
  因此查重也 MUST 排除它们，NEVER 只按 `THIRD_USER_ID + CARD_TYPE` 取 `REG_TMS` 最新一条」，
  未补该条件前「①返给 APP 的是别人的卡号；②本人主卡已销户而 C 行仍有效时，该用户再也开不了户」。
- **「按业务键幂等的远端资源 NEVER 在失败分支回滚」（ADR-D52）** —— 四个失败分支都逐条写明：
  `AccountRegistrationServiceImpl.requestApplication`（`<J>/service/impl/AccountRegistrationServiceImpl.java:158-160`、`:208-210`）
  「NEVER 在这里 releaseReservation：预占按 businessId 幂等、是并发请求共享的，释放会把兄弟请求正要 confirm 的卡号抽走（ADR-D52 实测）。
  滞留的预占交给 sys_job 107『卡池维护』的超时回收」；同文件 `:270-274`「**本方法 NEVER releaseReservation**（2026-09-14 / ADR-D52 起，
  此前会释放）。撞唯一索引恰恰证明**兄弟请求已经落库成功**，而单卡场景下两条请求共享同一个 `businessId` ⇒ 同一个 `reservationId` ⇒
  **同一张卡号**……在这里释放等于把已发出去的卡号退回池子」；支付宝渠道同款在
  `AlipayTripRegistrationServiceImpl`（`<J>/service/impl/AlipayTripRegistrationServiceImpl.java:120-122`、`:159`）。
- **confirm 失败 MUST NOT 返成功、MUST 开工单** —— `AccountRegistrationServiceImpl.requestApplication`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:185-187`）：「账户表已落库、卡号已归属该用户，但池子那边不是 ASSIGNED
  （很可能被并发的兄弟请求 release 回 AVAILABLE），同一卡号随时会被再发给别人。NEVER 返 0000（ADR-D52 实测过那样 APP 完全不知情）；
  也 NEVER 在这里去改卡池状态——分不清『该对齐』还是『该真释放』」；
  支付宝渠道同款 `AlipayTripRegistrationServiceImpl:134-135`「返回 false 意味着『账户表已把卡号发出去、池子那边却不是 ASSIGNED』，
  MUST NOT 返成功（ADR-D52）」。
- **工单开立注入 Mapper 而不是运营 service** —— `AccountRegistrationServiceImpl` 字段注释
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:83-89`）：「直接注入 Mapper 而不是 `AccountExceptionTicketService`：
  那个接口只有运营侧的 `list` / `close`，且其类注释明确写着 **NEVER 让自动流程调 close**；开单在本项目一直是各业务实现自己 insert……
  **NEVER 为了『统一』把开单加进那个运营接口**」；工单幂等键见同文件 `:222-224`「`BIZ_KEY` 取 `reservationId`，
  配合 `UK_ACCT_EXC_TICKET_TYPE_KEY (TICKET_TYPE, BIZ_KEY)` 天然幂等：APP 重推同一笔时预占 id 不变，只会有一张工单」。
- **归档两个入口的异常语义刻意不同** —— `AccountArchiveService`（`<J>/service/AccountArchiveService.java:10-17`）：
  「**两个入口的异常语义刻意不同，NEVER 合并成一个方法**（这是 2026-09-09 两次线上修复留下的形状）：
  `archiveIfLastChannelRemoved` —— 解绑侧（IF8A-75）用，**在调用方事务内执行、不吞异常**，归档失败 MUST 让整个删通道操作回滚
  （用户 2026-09-08 裁决：NEVER 留『通道已删、归档没做』的半成品）；`tryArchiveAfterCancel` —— 销户侧（IF8A-42）用，
  **自开短事务、吞异常只记 warn**，归档失败 NEVER 把已成功的销户翻成失败。把两者统一成一种异常策略，必然破坏其中一边」；
  销户侧的选用约束另见 `AccountCancelServiceImpl:47-49`「**销户侧只用 tryArchiveAfterCancel**……NEVER 换成解绑侧那个不吞异常的入口」，
  解绑侧见 `PayChannelServiceImpl:70-72`「**只用同事务入口 archiveIfLastChannelRemoved**：解绑侧要求归档失败即整单回滚，
  NEVER 换成吞异常的 tryArchiveAfterCancel」。
- **归档必须先取锁再 count** —— `AccountArchiveServiceImpl.archiveIfLastChannelRemoved`
  （`<J>/service/impl/AccountArchiveServiceImpl.java:56-63`）：「**三步的顺序不可调换。**先用 `for update` 锁住该用户的开户记录，
  再数剩余支付通道……若先 count 后取锁，两个事务会各自看到对方未提交的渠道仍存在、双方都跳过归档，用户信息永久残留且没有补偿路径」；
  「为什么落在『最后一个渠道解绑完』而不是 IF8A-42：APP 顺序是 35 → 42 → 75，42 时支付通道还没解绑，
  那时删掉开户记录会让 75 的解约成功分支回调 `requestRemovePayChannel` 时查不到记录（返 8004），
  且 `selectAnyByThirdUserIdAndCardIdAndCardType` 兜底也失效」；同一条并发保护在
  `UserItpRegInfoMapper.selectAnyListByThirdUserIdForUpdate`（`<J>/mapper/UserItpRegInfoMapper.java:110-113`）与
  `<X>/UserItpRegInfoMapper.xml:144-155`（含「`for update` 也刻意不带 wait N / nowait……加了超时会让第二个请求以 ORA-30006 失败、
  被调用方 catch 成回滚 + 上游重推，比多等一会儿更糟」）各留一份。
- **`Propagation.MANDATORY` 不是可选项** —— `AccountArchiveServiceImpl.archiveIfLastChannelRemoved`
  （`<J>/service/impl/AccountArchiveServiceImpl.java:64-68`）：「本方法第一步的 `for update` 只有在调用方已开事务时才持锁到提交；
  autocommit 下锁随语句结束即释放，上面那段并发保护会**静默失效、不报任何错**。声明 MANDATORY 能让『没有外层事务』在运行时立刻暴露，
  而不是退化成没有互斥的版本。**NEVER 改成 REQUIRED** —— 那会让漏开事务的调用点自己开一个新事务、看起来正常，
  实际把 count 与锁拆到了两个事务里。」
- **归档「先删再写日志」的顺序** —— `AccountArchiveServiceImpl`（`<J>/service/impl/AccountArchiveServiceImpl.java:86-88`）：
  「MUST 先删再写日志、NEVER 反过来：反序时 deleted==0（口径不一致 / DEL_YN 幽灵值）会留下 N 条 OPER_TYPE=3 的归档日志却没删任何行，
  事后从日志看『已归档』、库里行还在，且这条路径与 logSkipped 的幽灵行 WARN 互斥、走不到一起，等于彻底无人知晓。」
- **幽灵行只 WARN 不抛（ADR-D41）** —— `AccountArchiveServiceImpl` 删除后校验与 `logSkipped`
  （`<J>/service/impl/AccountArchiveServiceImpl.java:91-95`、`:120-128`）：「这里 MUST 只 WARN、NEVER 抛：
  另一条入口 `PayChannelServiceImpl.requestRemovePayChannel` 是带 `@Transactional` 的跨 Bean 调用，抛出会把『删支付通道』一起回滚 ——
  而那一步的远端（pay-sign 解约）已经收口，回滚只会制造新的不一致」；「`DEL_YN` 既非 1 也非 0」的行「对所有 `DEL_YN = 1` 的查询不可见、
  `deleteCanceledByThirdUserId`（条件 `DEL_YN = 0`）也删不掉，该用户**永久无法归档且没有自愈路径**，因此 MUST 打 WARN 并留下主键（ADR-D41）」，
  「**NEVER 在这里顺手把幽灵行改成 0 或 1**」；同一条判据在 `ArchiveDecision`（`<J>/domain/ArchiveDecision.java:18-21`）也写了一份。
- **销户注销 0 行 MUST 抛异常** —— `AccountCancelServiceImpl.doCancel`
  （`<J>/service/impl/AccountCancelServiceImpl.java:182-186`）：「**注销影响 0 行 MUST 抛异常**：调用方刚查到非空 `activeCards`，
  0 行只可能是这一瞬被并发销户。抛出会让 `transactionTemplate` 连同下面 N 条 `OPER_TYPE=2` 日志一起回滚，
  外层 catch 把它翻成 9999 —— 上游重推时会走到『无有效票卡』分支拿到确定答复。**NEVER 改成只记日志后继续**：
  那会留下『日志说销过户、`DEL_YN` 却没变』的痕迹，且对外仍返 0000，事后连排查线索都是错的。」
- **`ArchiveDecision` / `PhoneChangeRule` / `ChannelBindingRule` 为什么是纯函数** —— `ArchiveDecision`
  （`<J>/domain/ArchiveDecision.java:9-16`）：「这条规则跨 `USER_ITP_REG_INFO` 与 `APP_USER_PAY_CHANNEL` 两张表，
  此前散落在 `AccountArchiveServiceImpl.archiveIfLastChannelRemoved` 的三个提前 return 里……没有一处能被称为规则的定义点，
  也无法在不 mock 三个 mapper 的前提下测试。形态照抄 `pay-sign-server` 的 `paysign/domain/SignStatusTransition`（ADR-D40）」，
  「**本类 NEVER 依赖任何 mapper / Spring Bean**：它是纯函数，取数由调用方负责」。
- **`isDuplicateKeyViolation` 的私有副本是有意重复** —— `AccountRegistrationServiceImpl.isDuplicateKeyViolation`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:301-303`）：「与 `PayChannelServiceImpl` / `PhoneChangeServiceImpl` 的同名方法同源，
  按项目『NEVER 主动新建工具类』的约定各类保留一份私有副本」；`PhoneChangeServiceImpl:418-419` 补记「**有意的重复**：它只有 6 行、
  不依赖任何成员，为它新建工具类违反 AGENTS.md §5.1『NEVER 主动创建新的工具类』」。
- **`03` 同值异义** —— `AccountRegistrationServiceImpl.ALIPAY_PAYMENT_CHANNEL`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:52-58`）：「**同值异义警告**：
  `CardPoolAllocationServiceImpl.APP_CARD_TYPE_HCE` 也是 `"03"`，但那是 APP 上送的**票种**码（HCE 卡）。两者取值相同、含义无关，
  全局 grep `"03"` 会同时命中。**NEVER 把两者合并、互相引用，或据『另一处也是 03』推断本处语义。**」
- **卡池业务类型 NEVER 与其它场景复用** —— `AccountRegistrationServiceImpl.CARD_POOL_BUSINESS_TYPE`
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:62-64`）：「与 `LOGIC_CARD_POOL_CARD.BUSINESS_TYPE` 对应。
  该值参与卡池的归属唯一约束，**NEVER** 与其它场景复用同一取值。」

#### 陷阱

- **类内自调用让 `MANDATORY` 静默失效** —— `AccountArchiveServiceImpl.tryArchiveAfterCancel`
  （`<J>/service/impl/AccountArchiveServiceImpl.java:151-161`）：「**这里是类内自调用，`archiveIfLastChannelRemoved` 上的
  `Propagation.MANDATORY` 在这条路径上不生效**（ADR-D38 复核记录）：lambda 里的 `archiveIfLastChannelRemoved(...)` 是普通 Java 调用、
  不经过 AOP 代理……当前行为正确**纯粹因为 `transactionTemplate` 已经真的开了事务**」；
  「因此 **NEVER 删掉这里的 `transactionTemplate`**……那样不会像预期的那样报错，而是**静默退化成 autocommit**……
  且编译、单测、运行时都不报任何错」。
- **`ORA-01843` 与结束边界** —— `UserItpRegInfoMapper.countGroupByCardType`（`<X>/UserItpRegInfoMapper.xml:223-226`）：
  「入参是 yyyy-MM-dd 字符串、REG_TMS 是 TIMESTAMP：MUST 显式 TO_DATE，直接比较会走会话 NLS 隐式转换、报 ORA-01843 无效的月份（ADR-D97）。
  结束边界用 TO_DATE(end) + 1 的开区间，等价于『含结束日全天』；写成小于等于 TO_DATE(end) 会静默丢掉结束日 00:00 之后的全部记录。」
- **`where 1 = 1` + 全可选 `<if>` 被 Druid 判成恒真条件** —— 同一语句（`<X>/UserItpRegInfoMapper.xml:227-229`）：
  「条件用 where 标签、NEVER 退回恒真条件打头：两个日期都不传时，恒真条件会成为唯一谓词，被 Druid WallFilter 判成注入
  （select alway true condition not allow），该查询整条失败。」
- **SQL 正文注释与 XML 注释里的连续减号** —— `<X>/AccountExceptionTicketMapper.xml:12-17`：
  「SQL 正文内 NEVER 写行注释或块注释：Druid WallFilter 会判定为注入，该语句静默失效、只在 Oracle 运行期暴露。说明一律写在这里。
  另注意本注释块自身：XML 规范禁止注释文本里出现两个连续的减号，写了会让 MyBatis 解析 mapper 失败 ⇒ sqlSessionFactory 建不起来 ⇒
  整个服务起不来（2026-09-11 实测，account-server 2.0.47 首次部署即因此启动失败）」；
  同款告示另见 `<X>/TerminationTimeSummaryMapper.xml:7-8`。
- **`selectKey` 与 `useGeneratedKeys`** —— `<X>/UserPhoneChangeLogMapper.xml:9-15`：
  「NEVER 加 useGeneratedKeys="true" keyProperty="id" —— Oracle 的 getGeneratedKeys 在不声明返回列时取不到值，
  MyBatis 会抛 MyBatisSystemException；由于 updatePhone 原先带 @Transactional，异常会把前面已成功的 USER_ITP_REG_INFO 更新一起回滚，
  表现为接口恒返回 9999、库里毫无痕迹（2026-09-09 实测并修复；alipay-account-server 侧的副本漏改，2026-09-11 才对齐）」；
  工单表同款在 `<X>/AccountExceptionTicketMapper.xml:9-10` 与 `AccountExceptionTicketMapper.insert`（`<J>/mapper/AccountExceptionTicketMapper.java:29-30`）。
- **占位符 MUST 带 `jdbcType`** —— `<X>/UserPhoneChangeLogMapper.xml:22-23`：
  「每个占位符 MUST 带 jdbcType，NEVER 省略。本模块没有配 `mybatis.configuration.jdbc-type-for-null`
  （application.properties 里只有 mapper-locations，公共构件 mybatis-adaptor 也没兜）」。
- **`ROWNUM` 必须套在已排序的子查询外层** —— `<X>/UserPhoneChangeLogMapper.xml:129-131`：
  「Oracle 的 ROWNUM 在 ORDER BY 之前求值，写成同层 `WHERE ROWNUM <= n ... ORDER BY ID` 会先随机截断再排序，
  表现为『每次捞到的不是最旧的那批』，且旧行可能长期饿死」；工单查询同款在 `<X>/AccountExceptionTicketMapper.xml:55-57`（「勿简化」）。
- **`Del_Yn_*_Filter` 两个片段自带 `and`** —— `<X>/UserItpRegInfoMapper.xml:32-46`：
  「DEL_YN 过滤条件的唯一定义点……本 mapper 内 NEVER 再手写该字面量 —— 极性写反不报错，编译与单测都发现不了，
  只会表现为『查不到』或『多注销了一批行』」；「两个片段都自带 and，MUST 接在已有 where 条件之后使用；
  放在首个条件位置会渲染出『where and』这种只在运行时才炸的语法错，UserItpRegInfoMapperSqlTest 已对全部语句加了断言」；
  「`Del_Yn_Active_Filter` 同时充当 `updateCancelByThirdUserId` 的 CAS 前置条件，NEVER 改成参数化（如 DEL_YN = #{delYn}）——
  那等于把状态机白名单交给调用方」；「要放宽查询口径……MUST 先把 CAS 那条拆成独立片段再动，直接改本片段会让重复销户不再返 0 行，
  而是覆盖首次销户的 UN_REG_TMS / DEL_THIRD_USER_ID」。
- **销户与归档语句的极性陷阱** —— `<X>/UserItpRegInfoMapper.xml:326-329`（销户）：
  「这个 include 同时就是本状态机的 CAS 前置条件，NEVER 删」「NEVER 在此顺带清 THIRD_PAY_ID / CHANNEL / REQ_CONTRACT_NO ——
  IF8A-75 解绑时还要用」；`:344-346`（归档删除）：「NEVER 去掉这个 include，也 NEVER 换成 Del_Yn_Active_Filter（极性反了就是删错一批行）；
  更 NEVER 在支付通道尚未清空时调用 —— 原表行一旦删除，selectAnyByThirdUserIdAndCardIdAndCardType 那条兜底就失效，残留通道再也清不掉」；
  Java 侧同款在 `UserItpRegInfoMapper.deleteCanceledByThirdUserId`（`<J>/mapper/UserItpRegInfoMapper.java:119-127`）。
- **`hexEncode` 返回 null 的成因** —— `CardPoolAllocationServiceImpl.encodeIptUserId`
  （`<J>/service/impl/CardPoolAllocationServiceImpl.java:193-196`）：「**非十进制数字时返回 `null`**，由调用方按『HCE 发号失败』处理。
  IF8A-01 入口只校验了 `hasText`，不保证是数字，直接 `parseLong` 会抛 NumberFormatException 被上层统一 catch 成 9999，日志里看不出真实原因。」
- **安全服务的成功码不是 `0000`** —— `CardPoolAllocationServiceImpl.requestHceCardData`
  （`<J>/service/impl/CardPoolAllocationServiceImpl.java:178-180`）：「安全服务走 ResultVO 骨架（SecurityClient.buildBaseResponse
  把 code 映射成 retCode），成功只有 200、失败是 500 / 400，**从不回 0000**。因此这里 MUST 单码判定，
  NEVER 改成 AccResultCode.isSuccess —— 那是 ACC 出向的双码口径，在这里等于凭空放宽成功集合。」
- **`model` 模块 DTO 加字段漏改不会编译失败** —— `RegistrationCommitServiceImpl.buildTicketRequest`
  （`<J>/service/impl/RegistrationCommitServiceImpl.java:70-76`）：「`RegisterRideStatusReqDTO` 在 `model` 模块、是跨模块契约，
  加字段时漏改**不会编译失败**、只会让 ticket-server 收到 null，因此**全仓库只准有这一处组装**。」
- **`AccountErrorCodeEnum` 两套失败语义并存** —— `AccountErrorCodeEnum`（`<J>/constant/AccountErrorCodeEnum.java:6-9`）：
  「**本枚举里同时存在两套『失败』语义，这是历史现状、不是笔误**：开户 / 支付通道 / 销户链路的系统类失败用 `SYSTEM_ERROR`（`9001`），
  而**员工码链路用 `FAIL`**（`9999`）。ADR-D36 收口字面量时**刻意没有对齐取值**——这些码已经发给 APP 与 ACC，改值属改对外契约，
  MUST 先确认下游没在判这些码。**NEVER 为了『看起来整齐』把 9999 改成 9001。**」调用点同款约束见
  `RequestApplicationController`（`<J>/controller/ci/app/RequestApplicationController.java:155-157`）。

### 二、支付通道与签约同步（IF8A-23 / 24 / 75 / 77、显示账号同步）

#### 契约与判据

- **多卡用户 MUST 按 cardId + cardType 精确定位** —— `PayChannelServiceImpl.requestAddPayChannel` / `requestSetDefaultPayChannel`
  / `doRemovePayChannel`（`<J>/service/impl/PayChannelServiceImpl.java:107-109`、`:239-240`、`:434-435`）：
  「钱包渠道要求请求里的卡与开户信息严格一致，且同一 thirdUserId 可能有多条有效开户记录（多卡），因此 MUST 按 cardId + cardType 精确定位，
  NEVER 取『最新一条』再比对」；「同一 thirdUserId 允许存在多条有效开户记录（多卡），MUST 按 cardId + cardType 精确定位，
  NEVER 用 selectActiveByThirdUserId 取『最新一条』再比对——多卡用户必然张冠李戴」；非钱包渠道的卡信息比对「历史上是关闭的」
  （被注释的原实现保留在 `:132-139`）。
- **钱包渠道的 `REQ_CONTRACT_NO` NEVER 置 null** —— `PayChannelServiceImpl.requestAddPayChannel`
  （`<J>/service/impl/PayChannelServiceImpl.java:162-167`）：「2026-09-15 起 NEVER 再把钱包的 REQ_CONTRACT_NO 置 null。
  原实现在这里写 null，前提是『钱包不签约、扣款只靠 thirdPayId』；该前提已被支付中心实测推翻（§1.1 requestPay 的 withholding 场景
  强制要求 requestSignSeq，只送 payUserId 时网关返 code=9999『代扣签约请求流水号不能为空』），而支付域取 requestSignSeq 的路径就是
  account-server 回答的 reqContractNo（PaymentDomainServiceImpl.applyAccountUserView）。置 null 等于把钱包扣款所需的签约流水号在源头擦掉。」
- **已销户仍要能解绑：MUST 忽略 `DEL_YN` 再查一次** —— `PayChannelServiceImpl.doRemovePayChannel`
  （`<J>/service/impl/PayChannelServiceImpl.java:439-442`）：「IF8A-42 销户把 DEL_YN 置 0（0=已注销，1=有效，极性反直觉），
  而 APP 顺序是 35→42→75：走到 IF8A-75 强制解绑、由解约成功分支回调本接口清理支付通道时，开户记录已是注销态。
  此时若直接返回 8004，支付渠道已解约而本地 APP_USER_PAY_CHANNEL 残留，且无法自愈，因此 MUST 忽略 DEL_YN 再查一次，把清理动作放行」；
  该查询的使用约束在 `UserItpRegInfoMapper.selectAnyByThirdUserIdAndCardIdAndCardType`（`<J>/mapper/UserItpRegInfoMapper.java:27-32`）：
  「常规链路 **MUST** 用 `selectActiveByThirdUserIdAndCardIdAndCardType`，**NEVER** 用本方法绕过有效性校验——
  调用方必须自己判断 `delYn` 并明确接受已注销记录。」
- **删除影响 0 行的两种成因与处置** —— `PayChannelServiceImpl.doRemovePayChannel`
  （`<J>/service/impl/PayChannelServiceImpl.java:464-495`）：「MUST 接住影响行数、NEVER 只发语句就返 0000：0 行有两种成因 ——
  ①本地本来就没有这条通道行（pay-sign 解约重推的第二次调用，属正常幂等）；②cardType 经 CardTypeMapping 映射后与库里不一致、
  或 channel 取值不同，属真缺陷、本地会残留通道行且无补偿路径」；「这里 MUST 仍返 0000、NEVER 改成返错：调用方 pay-sign 的解约成功分支会重推，
  返错会让它反复重试并把已解约的签约卡在非终态」；「因此 MUST 当场再查一次该用户的通道条数把两种成因分开 —— 删除语句的 WHERE 是
  (THIRD_USER_ID, CARD_TYPE, CHANNEL) 精确等值（本表主键），删完还剩行就只能是键不匹配。这一次多余的 SELECT 只发生在 0 行分支，
  正常路径零开销」；成因②「MUST 打 ERROR：真触发时该用户的支付通道已经清不掉了，且上游会收到 0000、不会重试」，
  且「本分支至今 NEVER 被真实数据触发过，属预防性护栏」。
- **通道行与默认通道字段是两处状态** —— `PayChannelServiceImpl.doRemovePayChannel`
  （`<J>/service/impl/PayChannelServiceImpl.java:496-499`）：「这里与上面的 removed 刻意解耦：APP_USER_PAY_CHANNEL 的行与
  USER_ITP_REG_INFO 上的默认通道字段是两处状态，只剩字段没有行时清掉字段才是自愈，NEVER 因为 removed==0 就跳过」；
  「WHERE 是主键 ID，上面刚查到这一行，0 行只可能是这一瞬被并发销户归档删除」。
- **`APP_USER_PAY_CHANNEL` 没有状态机，`STATUS` 恒为 `'ACTIVE'`** —— `PayChannelServiceImpl.buildPayChannel`
  （`<J>/service/impl/PayChannelServiceImpl.java:773-783`）：「**该表没有状态机，`STATUS` 恒为 `'ACTIVE'`，这是有意保留的现状**：
  全表生命周期只有『插入』与『物理删除』……mapper 里也**故意没有任何 UPDATE 语句**，因此 `UPDATE_TMS` 恒等于 `CREATE_TMS`」；
  「2026-09-11 评估过『改成软删（解绑置 `INACTIVE` 保留行）』，**结论是不改**：本表所有读取点目前都不带 `STATUS` 过滤，
  一旦改软删，解绑后的通道仍会被查出来当有效通道用——那是比『少一列可用状态』严重得多的静默缺陷。
  **MUST 先把读取侧全部加上过滤条件、再引入软删**，**NEVER 只在写入侧单方面改状态**。」
- **`CARD_TYPE` 在通道表只存 4 位发卡码** —— 同方法（`<J>/service/impl/PayChannelServiceImpl.java:784-796`）：
  「**CARD_TYPE 在本表只存 4 位发卡码（044X），NEVER 存 APP 的 2 位码**。这里直接归一，不依赖调用方在 insert 前补一次
  `setCardType(issueCardType)` —— 那种写法下，『本方法返回的对象已经是对的』是**假的**……删除侧是按 4 位码**精确等值**匹配的，
  于是这条通道行永远删不掉、上游还会收到 `0000`」；「2026-09-14 实测库里有 1 行 2 位码残留（`THIRD_USER_ID=00522888` / `CARD_TYPE=02`，
  2026-06-24 落库）。**但它不是『删除侧漏删』的证据**……它证明的是**另一件事** —— 这张表历史上确实被写进过 2 位码，
  所以本方法的归一 MUST 保留。那一行本身归属不明（通道在、开户记录不在），**NEVER 擅自把它改成 0441**：
  没有开户记录就无法确定它真实的票种，猜一个值等于把『归属不明』这个事实抹掉。」
- **`PAY_ACCOUNT_ID` 与 `THIRD_PAY_ID` 不是同一个东西** —— `UserPayChannel.payAccountId`（`<J>/entity/UserPayChannel.java:16-26`）：
  「**与 `thirdPayId` 不是同一个东西，NEVER 混用**：`THIRD_PAY_ID` 是 APP 加通道时上送的第三方支付标识（本域自有）；
  本列的值来自**支付中心签约回调的 `payUserId`**，由支付域的 `APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID` 同步而来」；
  「**当前只有 IF8A-77 会回写它**，签约成功时支付域不回调账户域，因此新签约的通道行在走过 IF8A-77 之前该列为 `null`、页面显示 `-`」。
- **对内两个入口的返回码契约** —— `PayChannelInternalService.queryPayChannelByContractNo` / `syncPayAccountId`
  （`<J>/service/PayChannelInternalService.java:39-44`、`:56-65`）：「`APP_PAY_SIGN_INFO.CARD_ID` / `CARD_TYPE` 在本项目里**全库为 NULL**
  （签约链路不写这两列），而 `APP_TERMINATION_REQUEST` 的同名列是 NOT NULL。IF8A-75 补建解约申请时只能从 `APP_USER_PAY_CHANNEL` 取，
  其 `REQ_CONTRACT_NO` 即签约流水号」「未找到返回 8004，**NEVER** 返回 0000 带空 cardId——调用方靠 retCode 判定」；
  「**本方法只写 `APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID` 一列。NEVER 顺手改 `USER_ITP_REG_INFO.THIRD_PAY_ID`** ——
  那一列是 IF8A-77『更换默认支付方式』这个**业务动作**的产物，签约成功时用户尚未做出该选择，在这里写它等于替用户决定了默认支付方式」；
  「**命中 0 行返回 8004 而非失败**：『签约先于加通道』是合法时序……调用方（支付域）MUST 把本接口整体当**允许失败**处理。」
- **`ChannelBindingRule` 的三条 NEVER 与文案即契约** —— `ChannelBindingRule`（`<J>/domain/ChannelBindingRule.java:14-23`）：
  「**返回的字符串就是 APP 侧看到的 8001 retMsg，NEVER 改动措辞**——它是对外可见行为，改一个字就是改契约。返回 `null` 表示校验通过」；
  「**NEVER 把 IF8A-77 的字段集合并进来**：它校验的是 `cardIssueCode` / `regSignSeq`，与本类的 cardId / cardType 不是同一组，
  硬凑会得到一个带开关的四不像」；「**NEVER 在本类里查库或调 RPC**」；
  四要素顺序（`:54-58`）「顺序 thirdUserId → cardId → cardType → channel **是有意固定的**：多个字段同时为空时，
  APP 只会看到第一条文案，改顺序等于改对外行为」；钱包分支（`:78-82`）「**钱包分支 MUST 排在四要素之后**：
  它要读 `channel` 判断是不是钱包，四要素没过时 `channel` 可能为空，先判钱包会把『channel不能为空』这条文案吃掉」。
- **钱包通道码 `0B` 的唯一定义点** —— `ChannelBindingRule.WALLET_CHANNEL`（`<J>/domain/ChannelBindingRule.java:27-34`）：
  「本常量是账户域内该编码的**唯一定义点**……**NEVER 在别处再写一份 `"0B"` 字面量**」；
  「与 `pay-sign-server` 的 `PaySignWorkflow.WALLET_PAYMENT_VENDOR` **不是同一个东西**：那个是支付厂商（vendor），这个是通道（channel），
  两者恰好同值属巧合，**NEVER 因此把它们收口成一个**。」
- **`SIGN_SYNC_STATUS` 的取值与白名单** —— `PhoneChangeServiceImpl.SIGN_SYNC_STATUS_PENDING`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:38-43`）：「`USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS` 的初始态：
  显示账号变更事实待投递给支付域。取值集合 PENDING / SUCCESS / FAILED，白名单与 CAS 见 UserPhoneChangeLogMapper.xml。
  改动取值 MUST 全局 grep，NEVER 只改一处」；三条 CAS 的语义见 `UserPhoneChangeLogMapper`
  （`<J>/mapper/UserPhoneChangeLogMapper.java:26-61`）：「影响行数；0 表示状态已被别人改走，调用方 MUST 检查而非无条件当成功」、
  「NEVER 允许从 `SUCCESS` 改成 `FAILED` —— 已送达的事实不能被迟到的失败覆盖」、
  「**NEVER 为『拒绝』新增状态值**」「调用方 **MUST 在本方法之后立即开工单**：该行此后不再被扫表捞到」；
  SQL 侧同款在 `<X>/UserPhoneChangeLogMapper.xml:63-107`（含「`SIGN_SYNC_STATUS` 为 NULL 的历史行（改造前的记录）也不会被匹配，
  这是有意的——那些行没有待投递的事实」「影响 0 行时调用方 MUST 记 WARN 日志，NEVER 无条件当成功」）。
- **`SIGN_SYNC_RESULT` 长度与截断** —— `PhoneChangeServiceImpl.SIGN_SYNC_RESULT_MAX_LEN` / `truncate`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:46`、`:400`）：「`USER_PHONE_CHANGE_LOG.SIGN_SYNC_RESULT` 是 VARCHAR2(1024 CHAR)」；
  「超长在此截断，NEVER 让落状态因超长而失败」。
- **补偿重试上限与单批条数** —— `PhoneChangeServiceImpl.SIGN_SYNC_MAX_RETRY` / `SIGN_SYNC_SCAN_LIMIT`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:49-63`）：「达到即留在 FAILED 不再重推，等人工介入。不设上限等于对一个恒定失败的下游
  无限重试：每轮扫表都会捞到同一批行，RPC 量随时间线性堆积，且真正的故障被淹没在重复日志里」；
  「补偿由 web-admin 的 Quartz 任务同步调用，整批耗时 = 条数 × 单次 RPC 往返；不限量会让一次调度长时间占住线程
  （虚拟线程 pin 风险见 AGENTS.md §5.2）。没处理完的行留给下一次调度，NEVER 靠加大批量来『一次清完』」。
- **换号前置条件的三条 NEVER** —— `PhoneChangeRule`（`<J>/domain/PhoneChangeRule.java:16-29`）：
  「**NEVER 在本类里查库或调 RPC**」；「**NEVER 给 `decide` 补 `isActive()` 判断**……补一层 Java 判断不是『更严』，
  而是**把权威从 SQL 挪到 Java、且两处口径会各自漂移**；真要收紧 MUST 先改 SQL 口径。注意 `requestAddPayChannel` 那条链路**确实**多判了
  `isActive()`，两处不一致是**已知的、有意保留的现状**」；「`UNCHANGED` 与 `NO_ACTIVE_USER` **MUST 保持可区分**：
  前者对 APP 返成功（换号是幂等的，重复提交同一号码不该报错），后者返失败。**NEVER 合并成一个 boolean**」；
  判据细节（`:56-67`）「『新旧号相同』的判据是 `oldMsisdn != null && oldMsisdn.equals(newMsisdn)`，**NEVER 改成 `Objects.equals`**：
  库里 `MSISDN` 为 NULL 的历史行必须走 `PROCEED`（把号补上）」，
  「**多卡用户口径**……**NEVER 拿本方法的 UNCHANGED 当『所有卡都已是新号』的判据**」。
- **换号 MUST 同步员工码手机号且同事务** —— `PhoneChangeServiceImpl.updatePhoneLocally`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:196-199`）：「员工码行上也存着手机号，且 selectActiveByPhone 是『按手机号找员工码』的唯一入口，
  这里不同步就会出现『用户已是新号、员工码还留着旧号』，之后按新号再也找不到本人的卡。与上面的 MSISDN 同属账户域本地表，
  **MUST 放在同一事务内**：失败就一起回滚，NEVER catch 掉——那会留下一个没人会去修的静默不一致。影响 0 行是正常的（该用户没有员工码）。」
- **`countByThirdUserId` MUST 在 delete 之后** —— `UserPayChannelMapper.countByThirdUserId`
  （`<J>/mapper/UserPayChannelMapper.java:30-34`）：「口径是 thirdUserId 全量，不带 cardType / cardId——销户是用户级动作，
  只要该用户还有任何一条支付通道就不算解绑干净。**MUST 在 delete 之后调用**，同一事务内读到的是删除后的结果。」
- **`updatePayAccountIdByReqContractNo` 返回 0 行不是失败** —— `UserPayChannelMapper.updatePayAccountIdByReqContractNo`
  （`<J>/mapper/UserPayChannelMapper.java:40-53`）：「**返回 0 行不是失败**：该签约流水在本表可能压根没有对应通道行……
  调用方 MUST 只记日志、**NEVER 因此让 IF8A-77 返回失败** —— 这一列是展示用的补充信息，不是业务结果。」

#### 决策理由

- **对内契约面按调用方切、不按渠道也不按读写（ADR-D34）** —— `PayChannelInternalService`（`<J>/service/PayChannelInternalService.java:9-29`）：
  「拆分维度是**契约面（Interface Segregation）**，不是渠道、也不是读写：**不能按渠道切**：原类里根本没有渠道 if-else，
  只有 IF8A-23 一处判钱包 `0B`，切出来的两个类会有一个是空壳。**不能按读写切**：IF8A-77 一次请求里既跨域读签约信息、又写两张表，
  按读写切会把一个业务动作劈成两半。**按调用方切才成立**：这两个入口的**唯一**调用方是支付域……其余 5 个入口的调用方是 APP。
  两组的鉴权要求、报文口径、变更节奏都不同」；「**本接口不是 Facade**：它**不**转发给 `PayChannelService`，两者各自持 `UserPayChannelMapper`、
  互不依赖。**NEVER 把本接口改成对 `PayChannelService` 的包装** —— 那样切分就只剩命名、依赖方向反而多一条」；
  「**收益（不是『更整洁』这种说法）**：上线前补鉴权时只需在 `controller/internal/PayChannelInternalController` 一处加拦截，
  不必在 APP 端点上开例外；对齐 recon 的 `ReconInternalController` 现有形态」。
- **IF8A-77 故意不带 `@Transactional`** —— `PayChannelServiceImpl.requestUpdateChannelDefaultContract`
  （`<J>/service/impl/PayChannelServiceImpl.java:297-310`）：「**本方法故意不带 `@Transactional`**：方法体内要调 pay-sign 的
  `querySignInfoBySeq`（RPC），而 AGENTS.md §5.2 禁止事务包住网络调用——事务内调远端会把行锁持有时长拉长到对端响应时长，
  2026-08-26 生产已因此出过 8 分钟锁等待事故」；「去掉事务是安全的：写操作只有 `updateChannelDefaultContractById` **一条业务 UPDATE**，
  单语句自身就是原子的……**NEVER 因为『看起来该有事务』把注解加回来**；若将来这里真的要写第二张表**且两条写必须一起成立**，
  MUST 用 `TransactionTemplate` 只把两条写包进去，并把 RPC 留在事务之外」；
  「**2.0.63 新增的 `PAY_ACCOUNT_ID` 回写不属于上述情形**……**允许失败、允许影响 0 行**，因此**故意放在业务 UPDATE 成功之后、
  且不与之同事务**。把它包进事务反而更糟：一个展示列写失败会连带回滚已经成立的默认支付方式变更」（ADR-D30）。
- **两个平级入口各自开事务、私有实现不加注解（ADR-D39）** —— `PayChannelServiceImpl.requestAgreeRelease` / `doRemovePayChannel`
  （`<J>/service/impl/PayChannelServiceImpl.java:385-411`）：「**本方法 MUST 保留自己的 `@Transactional`**：它与 `requestRemovePayChannel`
  是两个**平级入口**……此前本方法直接调 `requestRemovePayChannel(request)`，那是类内自调用、不过 AOP 代理，
  被调方法上的注解在该路径上被完全忽略，正确性只靠『两处注解参数一字不差』这个巧合维持。**NEVER 退回让两个 public 入口互相调用**」；
  「**本方法 NEVER 加 `@Transactional`**：它是 private、只可能被同类的两个 public 入口调用，加注解也不会经过代理、纯属误导……
  现在这是一条**可以被违反、且违反后行为会真实改变**的约定，而不是『改了其中一个就静默失效』的陷阱」。
- **afterCommit 安排 `PAY_ACCOUNT_ID` 回查回写（ADR-D30 / ADR-D55）** —— `PayChannelServiceImpl.scheduleBackfillPayAccountId`
  （`<J>/service/impl/PayChannelServiceImpl.java:577-596`）：「**为什么必须在 afterCommit、NEVER 直接在 `requestAddPayChannel` 里同步调**：
  该方法带 `@Transactional`，而 AGENTS.md §5.2 明令『`@Transactional` 方法内 NEVER 发起任何 RPC / 网络调用』…… 放到 afterCommit 时
  通道行已提交、行锁已释放，这次 RPC 不再压住任何锁」；「**为什么需要这一步**：支付域的主动回写
  （`/internal/payChannel/syncPayAccountId`）在时序上**必然早于**本方法建出通道行……那次 UPDATE 恒命中 0 行、返 `8004`，
  而它**没有任何重试或补偿**（account-server 全模块无 `@Scheduled`，Quartz 侧也只有改手机号那一个补偿端点）。
  2026-09-14 实测 `00522955`：回写比建行早 0.9 秒，`PAY_ACCOUNT_ID` 因此永久为 NULL」；
  「**无事务时降级为同步执行**：单测直接调 service 不带事务，若只注册同步器就会**静默不执行**（AGENTS.md 记过 `fallbackExecution=false`
  的同款坑）。因此这里显式判断，保证行为可被单测覆盖」。
- **为什么由 account-server 代发起钱包代扣签约** —— `PayChannelServiceImpl.scheduleWalletContractSign`
  （`<J>/service/impl/PayChannelServiceImpl.java:615-635`）：「支付中心 §1.1 requestPay 的 `withholding` 场景**强制要求** `requestSignSeq`
  （2026-09-15 实测：只送 `payUserId` 时网关返 `code=9999「代扣签约请求流水号不能为空」`），而钱包用户此前从不在支付中心签约 ——
  于是钱包渠道的免密扣款从上线起一次都没成功过（`PAY_TXN_DETAIL` 里 `0B` 渠道零条 SUCCESS）」；
  「**为什么由 account-server 代发起、而不是让 APP 显式调 IF8A-16**：那样要改 APP 的对外契约、需甲方配合排期；
  钱包的授权本来就已在钱包侧完成（`thirdPayId` 即凭证），对 APP 再要一次交互没有业务意义。**按用户 2026-09-15 的明确选择落地**」；
  「**签约失败 NEVER 影响本次加通道的成功应答**……**NEVER 在这里把已成功的 IF8A-23 改成失败**，那会让 APP 重推、而重推只会拿到 8021」；
  出向 displayAccount 的处置（`:668-669`）「按用户 2026-09-15 的选择：用 thirdPayId 脱敏后填，NEVER 回显全量（AGENTS.md §5.2 敏感信息）」。
- **afterCommit 里的方法 MUST catch 住一切** —— `PayChannelServiceImpl.doWalletContractSign` / `doBackfillPayAccountId`
  （`<J>/service/impl/PayChannelServiceImpl.java:658-659`、`:701-703`）：「**MUST catch 住一切、NEVER 抛出**：
  本方法运行在 afterCommit，事务已提交、通道行已生效，抛异常回滚不了任何东西，只会让本已成功的 IF8A-23 对上游报错、引来重推」；
  展示列回写失败的定级（`:555-559`、`:709`）「**本方法 NEVER 抛异常、NEVER 影响 IF8A-77 的返回码**……
  把它升级成失败会让一个展示问题冒充业务失败」「支付域查不到或该签约还没拿到付款账号：只是运营页面少一列，NEVER 升级成业务失败」。
- **RPC 三分支处置刻意不同（ADR-D45）** —— `PhoneChangeServiceImpl.syncDisplayAccountToPayDomain`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:220-238`）：「返回值 MUST 落库，NEVER 只打日志就放行 ——
  只打日志会让『手机号已改、支付域仍是旧号』变成无法自愈的静默不一致（这是改造前的实际行为）」；
  「**2026-09-12 起用 `RpcOutcome` 的模式匹配取代 boolean**（ADR-D45）。三个分支的处置**刻意不同，NEVER 合并**：
  `Ok` → 置 SUCCESS，终态。`BizRejected` → **一次即终态 + 立即开工单**。支付域答复了但拒绝（`PaySignAppController:159` 在
  `APP_PAY_SIGN_INFO` UPDATE 影响 0 行时返 FAIL，即『该用户没有签约记录』），重推一万次也不会成功；旧版把它当可重试，
  补偿队列要白跑 `SIGN_SYNC_MAX_RETRY` 轮才开单，期间真实故障被同一条日志淹没。`Unreachable` → 置 FAILED、次数 +1，进补偿队列。
  这才是该重试的一类」；「外层 catch 保留改造前的语义……**NEVER 让它逃出去中断整批补偿**」。
- **不为「业务拒绝」新增工单类型** —— `PhoneChangeServiceImpl.openSignSyncTicketQuietly`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:361-374`）：「两者**刻意共用同一个工单类型** `TYPE_SIGN_SYNC_RETRY_EXHAUSTED`：
  类型是唯一键 `UK_ACCT_EXC_TICKET_TYPE_KEY` 的一部分，新增类型要连带改运营后台的类型口径；而两类对运维是同一个动作
  （核对支付域签约记录后订正并关单），区别写在 `DETAIL` 里就够。**NEVER 为『业务拒绝』新增工单类型**」；
  SQL 侧同款理由在 `<X>/UserPhoneChangeLogMapper.xml:100-103`：「`SIGN_SYNC_STATUS` 的取值集合（PENDING / SUCCESS / FAILED）
  散落在 Java 常量、本文件两处白名单与运营查询里，多一个值就要全局 grep 一遍……NEVER 为它新增状态值」。
- **补偿循环骨架收口到 `OutboxScan`（ADR-D46）** —— `PhoneChangeServiceImpl.compensateSignSync`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:305-315`）：「NEVER 在本类加 `@Scheduled` —— 调度源只有 web-admin 的 `sys_job` 一处；
  也 NEVER 加 `@Transactional` —— 本方法逐条发 RPC」；「**循环骨架已收口到 `OutboxScan#run`**（2026-09-12，ADR-D46）：
  『单条失败 NEVER 中断整批』『每行只计一次』『投递与失败处理抛异常都要兜住』三条不变量在那里有唯一定义，规范见 `docs/domain/outbox.md`。
  **NEVER 把循环抄回本方法**——它已经是第二次被推导了」。
- **开工单判据必须自己补 +1** —— `PhoneChangeServiceImpl.openTicketIfRetryExhausted`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:337-348`）：「判据是 `本次扫到的次数 + 1 >= SIGN_SYNC_MAX_RETRY`……
  **NEVER 改成 `>` 或不补 +1** —— 前者永远开不出单（达上限的行下一轮就被 `selectPendingSignSync` 的 `< maxRetry` 过滤掉、再也扫不到），
  后者会提前一轮开单」；「**整个方法 NEVER 向外抛异常**……开单因其它原因失败时 MUST 把原因写回 `SIGN_SYNC_RESULT`：
  该行已达上限、下一轮不再被 `selectPendingSignSync` 扫到，只留一条 ERROR 日志会让它彻底失联」。
- **换号本地写用 `TransactionTemplate` 而不是注解** —— `PhoneChangeServiceImpl.updatePhone`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:128-138`、`:85-87`）：「**本方法故意不带 `@Transactional`**：尾部要向 pay-sign 投递
  『显示账号已变更』这一事实（一次出网 HTTP）。事务内发起 RPC 会让行级排他锁的持有时长等于对端响应时长，是 2026-08-26 生产事故的形态」；
  「投递失败 NEVER 回滚本地手机号变更 —— 手机号已改是既成事实……反之若因投递失败而回滚，用户会看到换号失败，
  而下次重试仍会遇到同一个不可用的下游」；「**NEVER** 给 `updatePhone` 加 `@Transactional` —— 它尾部要发 RPC」。
- **换号类的三条不变量** —— `PhoneChangeServiceImpl` 类注释（`<J>/service/impl/PhoneChangeServiceImpl.java:29-31`）：
  「其中三条不变量 **NEVER 破坏**：①`updatePhone` 不带 `@Transactional`；②`updatePhoneLocally` 内不发起任何 RPC；
  ③员工码手机号同步 MUST 留在同一事务内且不被 catch。」
- **员工码状态机只有一个写入方** —— `PhoneChangeServiceImpl` 字段注释（`<J>/service/impl/PhoneChangeServiceImpl.java:70-73`）：
  「员工码的状态机与 ACC 交互归 `EmployeeCardService`，**NEVER 在本类里改 `CARD_STATUS`**，否则状态机就有了第二个写入方。」
- **异常工单存在的意义** —— `PhoneChangeServiceImpl` 字段注释（`<J>/service/impl/PhoneChangeServiceImpl.java:77-79`）：
  「补偿重推达上限的行在这里留一条 OPEN 记录。没有它那些行只是静静停在 `FAILED` 且不再被扫表捞取 —— 有进无出、无人知晓。」
- **对内实现不注入 `PaySignClient`** —— `PayChannelInternalServiceImpl` 类注释（`<J>/service/impl/PayChannelInternalServiceImpl.java:26-29`）：
  「**本类只持 `UserPayChannelMapper` 一个协作者，且两个方法都不带 `@Transactional`**：一个是单条 select、一个是单条 update，
  单语句自身原子（同 ADR-D22 / ADR-D32 的判断）。**NEVER 给本类加事务注解**，也 NEVER 在这里注入 `PaySignClient` ——
  对内契约面反过来调支付域会立刻造出一条新的双向边」；行为不变的判据（`:21-24`）「`PayChannelInternalContractTest` 里针对这两个入口的
  11 个用例**未改一行**即在本类上通过，这就是『行为不变』的判据」。
- **同名私有回写方法留在 APP 契约面** —— `PayChannelInternalServiceImpl.syncPayAccountId`
  （`<J>/service/impl/PayChannelInternalServiceImpl.java:89-92`）：「复用 ADR-D30 已有的 `updatePayAccountIdByReqContractNo`，
  **不新增 mapper 语句**。与 `PayChannelServiceImpl.syncPayAccountIdToChannelQuietly` 的差别只在异常语义：那个是 IF8A-77 内的
  『附带回写、吞异常』，这个是**独立入口**，异常要变成 retCode 让支付域看见。**那个私有方法留在 APP 契约面、NEVER 迁到本类** ——
  它是 IF8A-77 的组成部分。」
- **IF8A-77 刻意不走 `ChannelBindingRule`** —— `PayChannelServiceImpl.validateUpdateChannelDefaultContractRequest`
  （`<J>/service/impl/PayChannelServiceImpl.java:742-744`）：「IF8A-77 的字段集合是 `cardIssueCode` / `regSignSeq`，与四要素不是同一组，
  **刻意不走 `ChannelBindingRule`**——硬凑会得到一个带开关的四不像。」

#### 陷阱

- **`cardType` 为 null 导致 NPE → UUID retCode（2026-09-09 事故）** —— `PayChannelServiceImpl.doRemovePayChannel`
  （`<J>/service/impl/PayChannelServiceImpl.java:416-419`）：「先校验再做卡类型映射：cardType 为 null 时 toIssueCardType(cardType.trim())
  会抛 NPE，冒到全局异常处理器后 retCode 退化成 UUID，调用方（解约回调）只能判定为失败并回滚。已发生事故：2026-09-09 解约回调
  cardId/cardType 均为 null，此处 NPE 导致支付宝已解约、本地 APP_USER_PAY_CHANNEL 与 APP_PAY_SIGN_INFO 全部回滚，且每次重试都在同一行炸。」
- **收口日志口径与 ERROR 相互抵消** —— 同方法（`<J>/service/impl/PayChannelServiceImpl.java:458-461`、`:522-523`）：
  「『0 行且该用户仍有通道行』= 键不匹配、本地残留。收口日志 MUST 据此改口径，NEVER 在这种情况下还打『删除支付通道成功』——
  2026-09-14 实测过原写法：同一次调用里 :467 的 ERROR 说『本地将残留通道』、紧接着收口又说『成功』，
  排查的人先看到后者就会判定没事，等于把这条 ERROR 的价值抵消掉」；「仍返 0000……但收口日志 MUST 与上面那条 ERROR 口径一致，NEVER 说『成功』」。
- **catch 掉异常后 `rollbackFor` 不会触发** —— `PayChannelServiceImpl` 三处
  （`<J>/service/impl/PayChannelServiceImpl.java:193-195`、`:284-289`、`:534-535`）：「catch 掉异常后 rollbackFor 不会触发，
  MUST 显式标记回滚：通道行已 insert（148 行）而钱包默认通道 update（156 行）抛异常时，不标记就会提交『通道已加、默认通道未设』的半成品，
  同时对上游报 SYSTEM_ERROR；上游重推只会拿到『通道已存在』，该用户永久停在半成品状态」；
  「本方法当前只有一条业务 UPDATE，标记与不标记的可观察差异极小，但语义上必须对齐——『对上游报 SYSTEM_ERROR』与『库里已提交改动』
  NEVER 同时成立。这里是 ADR-D38 的落点：不标记时，一旦将来在 260 行之后追加第二条写，失败就会静默提交前半段，且编译与单测都发现不了」；
  「catch 掉异常后 rollbackFor 不会触发，MUST 显式标记回滚，否则『删了通道但归档失败』会被提交。用户 2026-09-08 裁决：
  归档失败即整体回滚返错误码，让上游重试，NEVER 留半成品状态」。
- **`MapperAspectToTrace` 把异常换类型，单层 catch 捕不到** —— `PayChannelServiceImpl.isDuplicateKeyViolation`
  （`<J>/service/impl/PayChannelServiceImpl.java:203-209`）、`AccountRegistrationServiceImpl:293-299`、`PhoneChangeServiceImpl:410-416`：
  「**MUST** 逐层遍历 cause，**NEVER** 直接 `catch (DuplicateKeyException)`：`MapperAspectToTrace`
  （`resource/micro/web/src/main/java/com/chinasofti/huateng/micro/monitor/trace/MapperAspectToTrace.java:51`）把 mapper 抛出的任何异常
  统一包成 `RuntimeException`，单层类型判断在本项目里捕不到。」
- **`REQ_CONTRACT_NO` 无唯一索引、命中多行是数据不干净** —— `PayChannelServiceImpl.syncPayAccountIdToChannelQuietly`
  （`<J>/service/impl/PayChannelServiceImpl.java:568-569`）与 `PayChannelInternalServiceImpl`（`:115`）：
  「REQ_CONTRACT_NO 上没有唯一索引，一条签约流水理论上可能落在多行通道上。真发生说明数据已经不干净（同一签约流水被复用），
  MUST 告警而不是当成正常」；只读侧的取值口径见 `UserPayChannelMapper.selectByReqContractNo`（`<J>/mapper/UserPayChannelMapper.java:22-26`）。
- **状态回写自身失败会中断整批补偿** —— `PhoneChangeServiceImpl.markFailedQuietly`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:286-292`）：「**本方法自身 NEVER 向外抛异常**……若这条 UPDATE 自己抛了
  `DataAccessException`（连接被回收、锁等待超时等），异常会逃出 catch 块并中断**整批**补偿，与『单条失败 NEVER 中断整批』的约定相反。
  落库失败只记 ERROR，让本行下一轮再试。」
- **多卡用户的旧号只代表其中一张卡** —— `PhoneChangeServiceImpl.updatePhoneLocally`
  （`<J>/service/impl/PhoneChangeServiceImpl.java:176-179`）：「这里取 `selectActiveByThirdUserId` 的『最新一条』的 MSISDN 当 OLD_MSISDN，
  而 `updateMsisdnByThirdUserId` 是按 THIRD_USER_ID 全量改。若该用户名下各行原本手机号不一致（历史脏数据），日志里的旧号只代表其中一张卡，
  NEVER 拿它当『所有卡的旧号』做补偿或回溯依据；真要逐卡留痕，MUST 改成按行记录、而不是在这里换查询方法。」
- **新增列的上线顺序：既有语句刻意不查该列** —— `<X>/UserPayChannelMapper.xml:17-21`、`:57-58`：
  「本语句刻意不查 PAY_ACCOUNT_ID：调用方（IF8A-23 查重、IF8A-24 设默认）都不读这个列。PAY_ACCOUNT_ID 是 ADR-D30 新增列，
  其 DDL 与 account-server 镜像的上线顺序无法保证，把它塞进这些既有语句会让『列还没建』时连 IF8A-23/24 一起报 ORA-00904」；
  insert 侧「不写 PAY_ACCOUNT_ID：加通道时这个值还不存在（签约成功后才由支付域回推……），写进来只会是 null，
  却让本语句多依赖一个新列」。
- **补偿端点不能同步跑在请求线程上** —— `TaskController.compensateExecutor` / `phoneSignSyncCompensate`
  （`<J>/controller/task/TaskController.java:32-38`、`:81-82`、`:112-114`）：「单批最多扫 `SIGN_SYNC_SCAN_LIMIT` 行、逐行发 RPC，
  下游慢时整批可达数十分钟；全服务默认开启虚拟线程而 ojdbc8 大量方法是 `synchronized`，长阻塞会 pin 住载体线程（AGENTS.md §5.2）」；
  「**立即返回『已受理』，批处理交后台单线程执行**……上一批未跑完时直接返回『进行中』，**NEVER 改成同步等待**」；
  「提交失败（如 `@PreDestroy` 已 shutdown 后仍有请求进来抛 RejectedExecutionException）时上面的 finally 永不执行，标志会停在 true，
  此后本实例的补偿永久停摆且无告警。MUST 在这里复位。」

### 三、电子员工卡（IF3A / ACC 状态通知）

#### 契约与判据

- **激活 / 禁用的顺序与返回码全集** —— `EmployeeCardServiceImpl.activateEmployeeCard`
  （`<J>/service/impl/EmployeeCardServiceImpl.java:176-193`）：「**本方法 NEVER 加 `@Transactional`**：中间那次
  `EmployeeCardOutboundService#requestActivation` 是对 ACC 的同步 HTTP，事务包住它会让员工码行的排他锁持有到对端响应为止
  （AGENTS.md §5.2 已记 2026-08-26 生产事故）。顺序固定为『校验前置状态 → 事务外调 ACC → ACC 成功后由
  `EmployeeCardPersistenceService#applyActivationResult` 在独立短事务里提交本地两条写』。若 ACC 已受理而本地写失败，
  MUST 落一张异常工单，NEVER 只打日志就返回成功」；返回码「`0000` 成功；`8001` 参数非法；`8004` 员工码不存在；
  `2002` 当前状态不允许；`9998` ACC 已受理但本地回写失败；`9999` 调用 ACC 失败（连不上 / 超时 / 5xx，可重试）；
  **其余码为 ACC 原样透传**——ACC 用『HTTP 4xx + 业务错误体』表达参数被拒（实测 `1002 参数校验失败：卡号不存在。`），这类码不可重试」；
  接口侧白名单见 `EmployeeCardService.activateEmployeeCard`（`<J>/service/EmployeeCardService.java:22-23`）：
  「前置状态是**白名单**：`actionFlag=1`（激活）只接受 `CARD_STATUS=3` 未激活，`actionFlag=0`（禁用）只接受 `CARD_STATUS=1` 正常，其余一律拒绝。」
- **`actionFlag` 与 `CARD_STATUS` 是两套编码** —— `EmployeeCardServiceImpl`（`<J>/service/impl/EmployeeCardServiceImpl.java:251-252`）：
  「actionFlag 是 APP 侧的入向约定（1 激活 / 0 禁用），CARD_STATUS 是本地列的取值，两套编码 MUST 保持分开：
  actionFlag=1 对应的目标状态是 NORMAL(1) 而非『同一个 1』。」
- **`CARD_STATUS` 四个取值的唯一定义点** —— `EmployeeCardStatus`（`<J>/domain/EmployeeCardStatus.java:4-16`）：
  「`USER_ACC_EMPLOYEE_CARD.CARD_STATUS` 的四个取值 —— **该列语义的唯一定义点**」「**数字取值由 ACC 定义，NEVER 自行调整**；
  也 **NEVER 依赖 `ordinal()`**（声明顺序与编码无关），一律用 `code()`」「SQL 侧的字面量**无法**由本枚举收口
  （mapper XML 里的 `CARD_STATUS = 1` 仍是硬编码），改动取值时 MUST 连 `UserAccEmployeeCardMapper.xml` 一起改」；
  正常生命周期见 `EmployeeCardPersistenceServiceImpl.detectBackwardTransition`（`<J>/service/impl/EmployeeCardPersistenceServiceImpl.java:97-98`）：
  「正常生命周期是 `3 未启用 -> 1 启用 <-> 2 禁用 -> 4 注销`，因此两类可疑：①从终态 `4` 回到任何非注销状态；
  ②从已开通过的 `1` / `2` 回到起始态 `3`」。
- **`EVENT_TYPE` 四个取值与落库字符串逐字一致** —— `EmployeeCardEvent`（`<J>/domain/EmployeeCardEvent.java:4-14`）：
  「`USER_ACC_EMPLOYEE_CARD_LOG.EVENT_TYPE` 的四个取值 —— **该列语义的唯一定义点**」「枚举名与落库字符串**刻意逐字一致**，落库取 `name()`。
  **NEVER 改名**：这列已有历史数据，改名等于把历史行与新行割成两类。」
- **ACC 出向的双成功码判据** —— `AccResultCode`（`<J>/domain/AccResultCode.java:7-23`）：
  「ACC 出向响应『算不算成功』的**唯一判据**」「ACC 侧同一批接口回过两种成功码 —— ITP 报文体系的 `0000` 与 `ResultVO` 体系的 `200`……
  双重否定漏一个分支就变成『把成功当失败』或反之，而两者都不会报错」；
  「**NEVER 把本类用在安全服务（acc-security-server）的响应上**：那条链路走 `SecurityClient.buildBaseResponse`，
  只会回 `ResultVO` 的码（成功 `200`、失败 `500` / `400`），从不回 `0000`。在那里额外放行 `0000` 等于凭空扩大成功集合」；
  「两个取值都**复用已有常量**……**NEVER 在本类里重新写字面量**」；
  「注意有一处调用点的语义是『**有码且不是成功码**才拒绝』……那里 MUST 保留 `StringUtils.hasText` 的前置判断，
  **NEVER 直接换成 `!isSuccess(...)`** —— 会把『没回码』从放行变成拒绝」（`:30-36`）。
- **员工码挂接只能在开户时做，且幂等** —— `EmployeeCardPersistenceService.attachEmployeeCardsQuietly`
  （`<J>/service/EmployeeCardPersistenceService.java:92-104`）：「**为什么挂接只能在开户时做**：`USER_ACC_EMPLOYEE_CARD` 的行是
  ACC 通知先建的……那一刻 ITP 侧还不知道这张卡属于哪个 APP 用户，所以 `THIRD_USER_ID` 只能在『ITP 用户出现』的时刻反向补，
  而开户正是这个时刻。手机号是员工码与 ITP 用户两边唯一的共有键」；
  「**MUST 在开户事务提交之后调用，本方法 NEVER 抛异常、NEVER 带 `@Transactional`**……多卡时每张卡是独立 CAS，
  包进一个事务会让一张卡失败连带回滚已挂好的其它卡」；
  「**幂等**：`updateThirdUserId` 的 WHERE 含『`THIRD_USER_ID` 为空或已等于目标值』，因此重复开户重放不会把别人的卡抢过来；
  影响 0 行 = 该卡已被**别的**用户占用或不在正常态，只记 WARN，**NEVER 改成强行覆盖**」；
  mapper 侧 CAS 语义见 `UserAccEmployeeCardMapper.updateThirdUserId`（`<J>/mapper/UserAccEmployeeCardMapper.java:36-42`）：
  「影响 0 行 = 卡不在正常态，或已被**别的**用户占用——调用方 MUST 当失败处理，NEVER 忽略返回值，
  否则会把别人的员工码静默算到当前用户名下。」
- **「活跃」员工码的口径是 `CARD_STATUS = 1`** —— `UserAccEmployeeCardMapper.selectActiveByPhone`
  （`<J>/mapper/UserAccEmployeeCardMapper.java:15-25`）：「『活跃』的口径是 `CARD_STATUS = 1`（正常），**不是『非注销』**：
  2 禁用、3 未激活、4 注销都不返回。换号链路靠它判断旧号名下有没有需要跟着迁移的员工码，改这个判据 MUST 同步
  `UserAccEmployeeCardMapper.xml`」；「**返回行的 `photoUrl` 恒为 `null`**……**调用方 NEVER 读这些行的 photoUrl**，要照片改走 `selectByCardNo`」。
- **换号时员工码手机号全量改、不带状态过滤** —— `UserAccEmployeeCardMapper.updatePhoneByThirdUserId`
  （`<J>/mapper/UserAccEmployeeCardMapper.java:46-54`）：「按 `THIRD_USER_ID` 全量改……**不带 `CARD_STATUS` 过滤**：
  已禁用 / 未激活的卡也要跟着迁，否则重新激活后手机号还是旧的。影响 0 行是正常情况（该用户名下没有员工码），调用方 **NEVER 当失败处理**。」
- **异常工单的载体与幂等键** —— `AccountExceptionTicket`（`<J>/entity/AccountExceptionTicket.java:5-13`、`:17-34`）：
  「只接住**本域**无法自愈的情况……**其它域 NEVER 写这张表** —— 跨域共享一张工单表会让 owner 失焦，各域应建自己的同类表，
  判据见 `docs/domain/README.md` 的 owner 规则」；「幂等靠 `UK_ACCT_EXC_TICKET_TYPE_KEY (TICKET_TYPE, BIZ_KEY)`：
  同一笔业务在同一类型下**只会有一张工单**，补偿任务每 5 分钟重扫也不会刷出重复记录」；三类工单的 `BIZ_KEY` 口径分别是
  `USER_PHONE_CHANGE_LOG.ID`、`卡号:目标状态`（如 `1234567890:1`）、`reservationId`；
  `CARD_POOL_CONFIRM_REJECTED`（2026-09-14 新增，ADR-D52）「这类不一致**自愈不了**：账户表已经把卡号发给用户，
  而池子那边可能已被并发的兄弟请求 `release` 回 `AVAILABLE`，下一个开户请求就能把同一张卡号发给别人。
  处置动作在库外（人工核对后把池子那行对齐到 `ASSIGNED`），**NEVER 让任何自动流程去改卡池状态** ——
  分不清『该对齐』还是『该真的释放』」；`CLOSED` 状态「关单只由人工触发，NEVER 由补偿任务自动关」。
- **APP 批量注册结果的判定方式** —— `EmployeeCardOutboundService.AppRegisterResult`
  （`<J>/service/EmployeeCardOutboundService.java:23-28`、`:36-41`）：「空 map 表示整批成功。**NEVER 用『返回 null』表示成功** ——
  调用方是按卡号逐张判定的，null 会让整批被当成成功放过去」；「**本方法从不抛异常**：地址未配置、无响应、调用异常一律折算成
  『整批失败 + 原因』返回，因此调用方 **MUST** 逐张检查 `AppRegisterResult#failureReasonOf`，**NEVER 假定『没抛异常就是成功』**
  （AGENTS.md §5.2 已记两起同型事故）」；
  ACC 查询失败一律返 null（`:45-49`），激活地址未配置时（`:53`）「调用方 MUST 直接返回失败，NEVER 空跑 `requestActivation`」。

#### 决策理由

- **业务策略与出网协作者的边界** —— `EmployeeCardServiceImpl` 类注释（`<J>/service/impl/EmployeeCardServiceImpl.java:40-46`）：
  「本类保留的是**业务策略**：入参校验、状态白名单、ACC 错误码的透传与归类、异常工单与批次切分。**NEVER 把这些搬进出网协作者**，
  也 **NEVER 在本类里重新持有 `RestTemplate` 或出网地址**」「事件日志的落库统一走 `EmployeeCardPersistenceService#recordEvent`（2.0.63 收口），
  **NEVER 在本类重新注入 `UserAccEmployeeCardLogMapper`**——那会让同一段 insert 又出现两份」；
  反向约束在 `EmployeeCardOutboundService`（`<J>/service/EmployeeCardOutboundService.java:11-19`）：
  「本接口**只做报文组装、HTTP 调用与响应解析，不含任何业务策略**……判据与 `CardPoolAllocationService` 一致，
  见 `docs/domain/decisions.md` ADR-D16 约束 3」「三个出网地址与报文公共字段都收在实现类里，**NEVER 让调用方再持有一份**」；
  批次切分策略（`<J>/service/impl/EmployeeCardServiceImpl.java:57`）「APP 注册的单批条数。这是本类的**批次切分策略**，NEVER 挪进出网协作者」。
- **ACC 的 4xx MUST 透传、不能归 9999** —— `EmployeeCardServiceImpl.activateEmployeeCard`
  （`<J>/service/impl/EmployeeCardServiceImpl.java:223-227`）：「ACC 用『HTTP 4xx + 业务错误体』表达参数被拒，2026-09-11 真机实测：
  400 Bad Request: {"retCode":"1002","retMsg":"参数校验失败：卡号不存在。"} 这类失败 MUST 把 ACC 的业务码与文案透出去，
  NEVER 归到 9999 —— 9999 的语义是『调用失败、远端未生效、可重试』，而参数被拒重试永远不会成功，归错码会让上游陷入没有出口的重复请求。
  5xx 与连不上 / 超时仍走下面的 9999（那才是真的可重试）」；出网侧对应约束
  （`<J>/service/EmployeeCardOutboundService.java:56-63`）「**异常一律原样抛出、本方法不做任何归类**……
  **NEVER 在这里把 `HttpClientErrorException` 吞掉或折算成返回值**，那会让『参数被拒』和『远端不可用』在调用方眼里变成同一件事」。
- **状态合法性用枚举白名单、不用区间判断** —— `EmployeeCardServiceImpl.validateCard`
  （`<J>/service/impl/EmployeeCardServiceImpl.java:380-381`）：「MUST 用枚举白名单、NEVER 退回 `< 1 || > 4` 区间判断：
  区间写法依赖『编码连续』这个偶然事实，ACC 一旦新增非连续取值就会静默放行、落库成一个本地没人认识的状态」；
  同一理由在 `EmployeeCardStatus.isKnown`（`<J>/domain/EmployeeCardStatus.java:49-54`）重复一份。
- **逆向跃迁只留痕、NEVER 据此拒绝** —— `EmployeeCardPersistenceServiceImpl.detectBackwardTransition`
  （`<J>/service/impl/EmployeeCardPersistenceServiceImpl.java:100-102`）：「**只留痕、NEVER 据此拒绝**：ACC 是权威发卡方，
  拒绝它的通知会造成『ACC 已注销、本地仍启用』这类永久不一致，比放行更糟（与 `applyActivationResult` 的白名单是两类语义，NEVER 混同）。
  本告警的用途是让这类通知**可被事后发现**，据此再去向甲方确认业务含义。」
- **落库服务里两个方法刻意不带事务** —— `EmployeeCardPersistenceService`（`<J>/service/EmployeeCardPersistenceService.java:10-16`）：
  「调用方 MUST 在事务外完成远端调用，远端成功后再调本接口提交本地写，NEVER 把网络调用包进事务（见 AGENTS.md §5.2）」；
  「**两个例外方法各自在 Javadoc 里写明了『为什么不带事务』，NEVER 顺手给它们补上**：`recordEvent`（单条 INSERT，
  要按调用方语义决定跟不跟着回滚）与 `attachEmployeeCardsQuietly`（逐卡独立 CAS + 吞异常，包事务会让一张卡失败拖垮整批）」；
  `recordEvent` 的双语义（`:70-73`）「调用方在事务内调（如 `updateEmployeeInfo`）会按 REQUIRED 加入调用方事务、随其一起回滚；
  调用方不在事务内调（如 APP 注册失败留痕）则自动提交、**NEVER 因为上层业务失败而丢掉这条痕迹**」。
- **`recordEvent` 的事件类型从 String 换成枚举** —— `EmployeeCardPersistenceService.recordEvent`
  （`<J>/service/EmployeeCardPersistenceService.java:77-79`）：「（2026-09-12 由 `String` 改成枚举，写错一个字母即编译失败；
  **NEVER 改回 String** —— 此前 6 处裸字面量，拼错只会在日志表里多出一个没人查得到的事件类型，编译与单测都发现不了）。」
- **两类「影响 0 行」刻意分开处理** —— `EmployeeCardPersistenceService.saveFromStatusNotify` 与 `refreshProfileFromAcc`
  （`<J>/service/EmployeeCardPersistenceService.java:22-25`、`:53-56`）：「**UPDATE 影响 0 行时抛 `IllegalStateException`**：
  本方法先按 cardNo 查到了行，0 行只可能是并发删除……**NEVER 改成只记日志**——那会连带写出一条『处理成功』的事件日志」；
  「**UPDATE 影响 0 行只记 WARN、NEVER 抛**：本方法挂在只读的员工码查询链路上，抛出会让查询退化成全局异常处理器的 UUID retCode；
  资料回填是尽力而为的旁路……这与 `saveFromStatusNotify`『0 行即抛』是**两类语义，NEVER 统一**」；
  实现侧的两段展开在 `EmployeeCardPersistenceServiceImpl`（`<J>/service/impl/EmployeeCardPersistenceServiceImpl.java:79-83`、`:160-163`）。
- **工单开立自己吞异常** —— `EmployeeCardServiceImpl.openActivationTicketQuietly`
  （`<J>/service/impl/EmployeeCardServiceImpl.java:271-281`）：「幂等靠 `UK_ACCT_EXC_TICKET_TYPE_KEY`，同卡同目标状态重复触发只会有一张；
  本方法 **NEVER 抛异常**，工单开立失败也只记 ERROR，避免掩盖上一层的真实失败原因」；
  `thirdUserId` 入参「**真实数据下当前恒为 `null`**……照样传是为了等挂接链路补齐后自动生效，**NEVER 因为『反正是空』就删掉这个参数**」。
- **出网配置收成一个对象** —— `EmployeeCardOutboundProperties`（`<J>/model/EmployeeCardOutboundProperties.java:9-18`）：
  「2026-09-11 由 10 个散落的 `@Value`（8 个报文/地址项 + 2 个超时）收拢成一个对象。**配置键一个字都没改**，
  因此 K8s Deployment 的 env 与 `application.properties` 都不用动」「**这是全仓第一处 `@ConfigurationProperties`**……
  若团队不接受这种写法，回退方式是把字段改回 `@Value` 逐个注入，**NEVER 为了回退去改配置键名**」；
  「注意本前缀下还有 `employee-card.app-batch-size`，它**刻意不在本类里** —— 那是 `EmployeeCardServiceImpl` 的批次切分策略，
  不属于『出网』这件事（见 ADR-D18）」。

#### 陷阱

- **单卡失败必须隔离，否则整批既不落库也不进 failList** —— `EmployeeCardServiceImpl.processAppBatch`
  （`<J>/service/impl/EmployeeCardServiceImpl.java:118-120`）：「每张卡整体包一层：recordEvent 自己也会 insert，
  它抛异常会逃出循环并逃出无顶层 catch 的 notifyEmployeeCardStatus，导致『已在 APP 注册成功的其余卡既不落库也不进 failList』，
  ACC 只收到全局异常处理器的 UUID retCode 并整批重推。MUST 做单卡失败隔离。」
- **资料更新 0 行继续往下走会写出错的成功痕迹** —— `EmployeeCardServiceImpl.updateEmployeeInfo`
  （`<J>/service/impl/EmployeeCardServiceImpl.java:326-328`）：「`updateEmployeeInfo` 的 WHERE 是主键 ID，上面刚按 cardNo 查到这一行，
  0 行只可能是这一瞬被并发删除。MUST 返失败让 ACC 重推，NEVER 继续往下写『处理成功』的 log 记录并返 0000 ——
  那会让『资料没落库』彻底无人知晓」；落库侧同款在 `EmployeeCardPersistenceServiceImpl`（`:79-83`、`:131-134`）
  「NEVER 改成只记日志后 return true —— ACC 侧状态已变更，本地静默停在旧值就再也没人发现」。
- **ACC 报文可能不带 `cardNo` / `cardStatus`，覆盖成 null 会撞 NOT NULL** —— `EmployeeCardPersistenceServiceImpl.refreshProfileFromAcc`
  （`<J>/service/impl/EmployeeCardPersistenceServiceImpl.java:145-148`）：「这条路径只为补齐姓名等资料列，入口（IF3A 查询）**没有**走
  validateCard，因此 ACC 报文可能不带 cardNo / cardStatus。而 applyAccInfo 会无条件覆盖这两列，它们在 USER_ACC_EMPLOYEE_CARD 上都是
  NOT NULL，覆盖成 null 会让 UPDATE 抛约束异常、一路冒到全局异常处理器（retCode 退化成 UUID）。MUST 保留原值。」
- **`parseObject` 返回 null 时的 NPE 会被吞成「远端业务拒绝」** —— `EmployeeCardOutboundServiceImpl.parseAppFailList`
  （`<J>/service/impl/EmployeeCardOutboundServiceImpl.java:79-80`）：「响应不是 JSON 对象（纯文本 / JSON 数组 / null 字面量）时
  parseObject 返回 null，直接 getString 会 NPE 并被下方 catch 吞成『远端业务拒绝』，整批失败原因不可辨。」
- **`hasText` 前置判断不能简化** —— `EmployeeCardOutboundServiceImpl`（`<J>/service/impl/EmployeeCardOutboundServiceImpl.java:159-160`）：
  「MUST 保留 hasText 前置：ACC 有的响应不带任何码，本方法的历史语义是『没回码就当成功』。
  NEVER 简化成 !AccResultCode.isSuccess(resultCode) —— 那会把『没回码』从放行改成拒绝。」
- **未配置地址时 `RestTemplate` 抛的异常没有业务语义** —— `EmployeeCardOutboundServiceImpl`
  （`<J>/service/impl/EmployeeCardOutboundServiceImpl.java:186-187`）：「与 registerToApp / queryFromAcc 同口径先判地址：
  未配置时 RestTemplate 抛的是无业务语义的 IllegalArgumentException，调用方看不出『是没配地址』还是『ACC 拒绝了』。」
- **CLOB 列 `PHOTO_URL` 不能顺手用全字段清单** —— `<X>/UserAccEmployeeCardMapper.xml:30-36`、`:48-52`：
  「PHOTO_URL 是 CLOB、实测单行可达 250KB 量级：按手机号一次可能返回多张卡，全字段查询会把几百 KB 的 base64 照片整批拉进 JVM 再全部丢掉。
  ojdbc 读 CLOB 走的是 synchronized 方法，在虚拟线程上还会 pin 载体线程（AGENTS.md §5.2）。新增查询若不需要照片，MUST 复用本清单；
  NEVER 图省事直接用 BaseColumnList」「resultMap 未映射到的列留 null，调用方 MUST 不读 photoUrl；要照片请改走 selectByCardNo」。

### 四、卡池预占（reserve / confirm / release）

#### 契约与判据

- **协作者只做 RPC、不含业务策略** —— `CardPoolAllocationService`（`<J>/service/CardPoolAllocationService.java:6-11`）：
  「本接口**只做 RPC 与日志，不含任何业务策略** —— 『哪种票种走卡池、业务流水号怎么拼、同行票是否幂等』仍留在
  `AccountApplicationServiceImpl.allocateCard`，**NEVER 把这些判断挪进来**，否则支付宝出行与 IF8A-01 两条链路的差异会被埋进公共协作者」；
  「所有方法都是出网调用，**MUST 在事务外调用**（AGENTS.md §5.2）」；
  实现侧（`<J>/service/impl/CardPoolAllocationServiceImpl.java:24`）「本类**不带 `@Transactional` 也 NEVER 加**：每个方法都是出网调用」。
- **confirm 的返回值 MUST 被检查，失败 MUST 开工单且 NEVER 返成功** —— `CardPoolAllocationService.confirmReservation`
  （`<J>/service/CardPoolAllocationService.java:26-38`）：「确认预占。失败只打 ERROR，**NEVER 抛出** —— 卡号已发给用户，回滚开户才是错的」
  「**调用方 MUST 显式检查返回值**（AGENTS.md §5.2 关于返回 boolean 的方法）。返 `false` 意味着『账户表已把卡号发出去，
  池子那边却不是 `ASSIGNED`』—— 卡号随时可能被再发给另一个用户，**NEVER 在这种情况下对 APP 返成功**，
  MUST 开 `AccountExceptionTicket.TYPE_CARD_POOL_CONFIRM_REJECTED` 工单转人工。2026-09-14 之前本方法返 `void`、只打一行 ERROR，
  实测并发下真的漏出了一个『账户表有效、池子 AVAILABLE』的卡号且 APP 收到 `0000`，见 ADR-D52」；
  实现侧（`<J>/service/impl/CardPoolAllocationServiceImpl.java:96-101`）补记「返 `true` 也包含『本次没有预占可确认』（HCE 发号不进卡池）
  这一正常跳过分支，**NEVER 把它当失败**」。
- **`reserveFromPool` 返回 null 的三种原因已分级** —— `CardPoolAllocationServiceImpl.reserveFromPool`
  （`<J>/service/impl/CardPoolAllocationServiceImpl.java:61-62`）：「调用方 MUST 在事务外调用：这是 RPC。返回 `null` 只表示
  『本次拿不到卡号』，三种原因已按 `CardPoolOutcome` 分级打日志，NEVER 在上层再统一翻译成『卡池耗尽』。」
- **发号结果记录的字段语义** —— `CardPoolAllocationService.CardAllocation`（`<J>/service/CardPoolAllocationService.java:86-92`）：
  「`reservationId` 卡池预占标识，仅走卡池发号时非空；为空表示无预占可确认 / 释放」「`businessId` 预占时使用的业务流水号，
  确认与释放都要带上供服务端校验归属」。

#### 决策理由

- **失败分支 NEVER release（ADR-D52），且任何以 `reservationId` 为键的 CAS 都挡不住** —— `CardPoolAllocationService.releaseReservation`
  （`<J>/service/CardPoolAllocationService.java:42-56`）：「**⚠️ NEVER 在『本次请求失败』的分支里调本方法**（2026-09-14 / ADR-D52）。
  `reserveFromPool` 按 `businessId` 幂等，**同一用户同一票种的并发请求拿到的是同一个 `reservationId`**，即预占是这批请求**共享**的、
  不是本请求私有的。于是失败方一 release 就把兄弟请求正要 confirm 的那张卡抽走 —— 实测后果是成功方 confirm 被拒、
  卡号回到 `AVAILABLE` 却已写进账户表。而**任何以 `reservationId` 为条件的 CAS 都挡不住这件事**（兄弟持有的是同一个 id），
  请求内也拿不到『有没有兄弟正要 confirm』的信息，因此唯一正确的做法是**不释放**，交给 `sys_job` 107『卡池维护』
  （`cardPoolQuartzTask.runMaintenance()`，cron `0 0/5 * * * ?`）的预占超时回收」；
  「本方法**保留**是因为『明确不该占着这张卡』的场景仍需要它（如运维显式回收），**NEVER 因为开户链路不再调用就删掉**」；
  实现侧（`<J>/service/impl/CardPoolAllocationServiceImpl.java:96-98`）「确认失败不回滚已提交的开户数据——卡号已发给用户，回滚才是错的；
  但**结果 MUST 回给调用方**：未确认的预占会被卡池的超时回收扫回 AVAILABLE，此时同一卡号可能被二次发放，
  所以除了打 ERROR，还要让上层开工单 + 不返成功（ADR-D52）」。
- **票种码 `03` / `04` 的常量不与支付渠道码合并** —— `CardPoolAllocationServiceImpl.APP_CARD_TYPE_HCE`
  （`<J>/service/impl/CardPoolAllocationServiceImpl.java:32-41`）：「**与 `AccountRegistrationServiceImpl.ALIPAY_PAYMENT_CHANNEL` 的 `"03"`
  是两套互不相干的编码**：那个 `03` 是**支付渠道**（支付宝），这个是**票种**。此前两处都是裸字面量，读代码时无法分辨，
  全局 grep `"03"` 也会把两者混在一起。**NEVER 把这两个常量合并或互相引用。**」；
  「APP 口径与发行口径的映射表在 `model` 的 `CardTypeMapping.ISSUE_CARD_TYPES`，那里的 `NFC_BUCKET_CARD_TYPES` 是 private、
  且只服务查询链路的聚合桶语义，**NEVER 为了复用它去改 `model` 的可见性**（本处要的是『是不是 HCE』，不是『查哪些票种』）」。
- **接口方法不假定调用方已判空** —— `CardPoolAllocationServiceImpl`（`<J>/service/impl/CardPoolAllocationServiceImpl.java:155`）：
  「本方法是接口方法、两条开户链路共用，NEVER 假定调用方已判空。」

#### 陷阱

- **HCE 发号走 `ResultVO` 单码判定** —— 见 §一「陷阱」中 `CardPoolAllocationServiceImpl.requestHceCardData`（`:178-180`）同条。
- **`encodeIptUserId` 非数字返回 null** —— 见 §一「陷阱」中同名条目（`:189-196`）。

### 五、运营后台 page 接口

#### 契约与判据

- **`null` 与「查不到」不是一回事** —— `ItpUserQueryService.search` / `payChannels`（`<J>/service/ItpUserQueryService.java:25-43`）：
  「按查询类型检索开户记录（含有效与已注销，不含已归档物理删除的行），返回**脱敏后**的视图」
  「**`null` 表示查询类型不受支持**（调用方据此返回参数错误）。**NEVER 把 `null` 与『查不到』混为一谈** —— 查不到是空列表」；
  「渠道视图列表；**`null` 表示没找到有效票种注册信息**」；controller 侧复述在
  `ItpUserPageController`（`<J>/controller/page/ItpUserPageController.java:27-28`）。
- **批量导入查询的条数上限** —— `ItpUserPageController.MAX_BATCH_CARD_IDS`
  （`<J>/controller/page/ItpUserPageController.java:34`、`:79-82`）：「批量导入查询的单批卡号上限：远低于 Oracle IN 列表 1000 的硬顶，
  留出防呆余量」「单批上限 500 条，超出直接报错，防 IN 列表过长拖垮 Oracle」；
  mapper 侧同款在 `UserItpRegInfoMapper.selectListByCardIds`（`<J>/mapper/UserItpRegInfoMapper.java:64-66`）与
  `<X>/UserItpRegInfoMapper.xml:203-207`。
- **运营查询三条语句刻意不带 `DEL_YN` 过滤** —— `UserItpRegInfoMapper.selectListByThirdUserId` 等
  （`<J>/mapper/UserItpRegInfoMapper.java:46-61`）：「运营查询专用：按用户查全部开户记录（含已注销），**不带 `DEL_YN` 过滤、不加锁**……
  归档判断仍 MUST 用带锁版本」「注销未归档期间同一卡号可能与重新开户的有效记录并存，故返回 `List`」；
  XML 侧（`<X>/UserItpRegInfoMapper.xml:173-178`）「NEVER 给它们 include Del_Yn 片段，也 NEVER 手写字面量 ——
  delYnLiteralsStayInsideTheTwoFragments 会拦。已归档行（物理删除）这里查不到，属预期口径」。
- **解约时间聚合 MUST 双键配对** —— `ItpUserQueryServiceImpl.fillTerminationTimes`
  （`<J>/service/impl/ItpUserQueryServiceImpl.java:68-72`）：「双键配对是刻意的：卡号会回收再分配，仅按 cardId 回填会把旧主的解约记录
  串到新主头上。空集合 MUST 跳过调用（`in ()` 会运行时才炸）」；
  mapper 侧（`<J>/mapper/TerminationTimeSummaryMapper.java:23-31`）「从未解绑过的卡不会出现在结果里」
  「调用方 MUST 保证非空（空集合应直接跳过调用，否则 `in ()` 会在运行时才炸）」；
  SQL 侧（`<X>/TerminationTimeSummaryMapper.xml:19-23`）「COMPLETE_TIME 只统计 SUCCESS 的最近一次（解绑成功日期）——FAILED 也会写
  COMPLETE_TIME，不加状态过滤会把失败的完成时间误认为解绑成功」。
- **跨域只读切片放 page 包、不放 entity 包** —— `TerminationTimeSummary`（`<J>/page/TerminationTimeSummary.java:5-10`）：
  「这是 pay-sign 域表的只读切片（同库跨域直读，见 ADR-D30 的本地读口径），不是 account 域实体，因此放在 page 包而不是 entity 包」；
  mapper 侧（`<J>/mapper/TerminationTimeSummaryMapper.java:10-17`）「**NEVER 在本 mapper 写 `APP_TERMINATION_REQUEST`**
  （insert/update/delete 一律不允许）——写路径的权威在 pay-sign-server」，XML 内同款告示（`<X>/TerminationTimeSummaryMapper.xml:6`）。
- **运营视图只展示、不当业务判据** —— `ItpPayChannelView`（`<J>/page/ItpPayChannelView.java:8-14`）：
  「**全部字段都取自本地 `APP_USER_PAY_CHANNEL`**：ADR-D30 已把这个页面的跨域读整段删掉，`status` 直接是通道行的 `STATUS` 列、
  `terminationReady` 由 `REQ_CONTRACT_NO` 非空 + `STATUS=ACTIVE` 本地推导。**NEVER 因为『这两个值语义上属于支付域』
  就把 `PaySignClient` 注回只读路径**」「只用于运营页展示，NEVER 当作业务判据回写任何状态」；
  `ItpUserSearchView`（`<J>/page/ItpUserSearchView.java:5`）「不返回证件号和 HCE 卡数据」。
- **工单列表 MUST 带 limit、关单是 CAS** —— `AccountExceptionTicketMapper.selectByCondition` / `close`
  （`<J>/mapper/AccountExceptionTicketMapper.java:34-55`）：「**MUST 带 `limit`**：这张表只增不删，没有归档任务，
  全量捞出会随时间无界增长」；「白名单只有 `OPEN` 一个前置状态，因此重复关单返回 0 而不是静默成功 ——
  调用方 **MUST** 据此把『工单不存在 / 已关闭』与『本次关单成功』区分开，**NEVER 无条件返回成功**，
  否则两个运营同时点关单时都会看到成功、无法追溯是谁处理的」；
  service 层收敛规则（`<J>/service/AccountExceptionTicketService.java:20-34`）「条数上限由本层收敛：`limit <= 0` 取 50，
  超过上限一律收敛到上限。这张表只增不删、没有归档任务，**NEVER 放开成全量查询**」
  「**调用方 MUST 显式检查返回值**（AGENTS.md §5.2 关于返回 boolean 的方法）：把 `false` 当成成功会让『工单不存在』
  『已被别人关掉』『本次关单成功』三种情况无法区分」；controller 侧（`<J>/controller/page/AccountExceptionTicketPageController.java:63-76`）
  「**NEVER 改成『影响 0 行也返回 ok』**，那会让『工单不存在』『已被别人关掉』和『本次关单成功』三种情况在页面上无法区分」
  「MUST 显式检查返回值：false 表示工单不存在或已不是 OPEN，NEVER 当成成功」。
- **关单是本域唯一人工出口、当前无鉴权** —— `AccountExceptionTicketPageController`
  （`<J>/controller/page/AccountExceptionTicketPageController.java:25-34`）：「**关单是本域唯一的人工出口**……处置动作（核对支付域展示账号、
  订正员工码状态）在库外，关单只表示『有人看过并处理完了』。因此 **NEVER 让任何自动流程调用关单**，也 **NEVER** 在这里顺手做补偿重推 ——
  重推入口是 `/phoneSignSyncCompensate`」；「**本控制器当前没有鉴权**：用户 2026-09-11 明确选择『对齐现状，暂不加鉴权，
  与其他 20 个端点一致』。这与 AGENTS.md §5.2……冲突，是**有意为之的临时降级，上线前 MUST 补齐**（形态对齐 `AccountRequestVerifier`，
  NEVER 自造签名）。在此之前任何网络可达方都能按 ID 关掉任意工单，`CLOSED_BY` 只是线索、不是审计凭据」；
  `closedBy` 入参（`:69`）「当前无鉴权，这个值完全由调用方自述，MUST 只当线索」；
  SQL 侧同款（`<X>/AccountExceptionTicketMapper.xml:83-88`）「NEVER 放宽成『非 CLOSED 即可关』，也 NEVER 允许 CLOSED 再改回 OPEN」
  「这列只能当线索、NEVER 当审计凭据，上线前 MUST 补鉴权」。
- **注册量统计只读聚合、含已注销** —— `UserItpRegInfoMapper.countGroupByCardType`（`<J>/mapper/UserItpRegInfoMapper.java:132-136`）：
  「只读聚合，不带 `DEL_YN` 过滤（统计口径含有效与已注销）。日期参数为闭区间字符串（`yyyy-MM-dd` 或时间戳），由调用方保证格式」；
  视图（`<J>/page/RegStatView.java:3-11`）「只读聚合 `USER_ITP_REG_INFO`，不含任何个人信息字段」「卡类型中文名
  （由 CardTypeCodeEnum 解析，未知编码原样回退）」。

#### 决策理由

- **运营查询整段从 controller 下沉，且 N+1 跨域 RPC 已删（ADR-D30）** —— `ItpUserQueryService`
  （`<J>/service/ItpUserQueryService.java:12-21`）：「原先那个 controller 不只是透传：它自己按查询类型分派、按渠道逐条调支付域 RPC、
  并组装脱敏视图与 `terminationReady`，属于 AGENTS.md §3.3 禁止的『controller 写业务逻辑』。**NEVER 把 Mapper 或 `PaySignClient`
  注回 controller**」；「**2.0.63 起本接口的实现内没有任何跨域 RPC**（ADR-D30）……**NEVER 为了『查得更全』把 RPC 加回来** ——
  运营列表页每行一次跨域 HTTP 只换一个展示字段，代价与收益不成比例；覆盖率问题的正解是补齐回写点，不是在读路径上兜底」；
  实现侧（`<J>/service/impl/ItpUserQueryServiceImpl.java:29-35`）补记解约时间列「用 `TerminationTimeSummaryMapper` 对同库的
  `APP_TERMINATION_REQUEST` 做一次聚合只读查询（两个时间列换一条 SQL）」。
- **工单查询与关单从 controller 下沉** —— `AccountExceptionTicketService`（`<J>/service/AccountExceptionTicketService.java:10-12`）：
  「那里原本直接注入 Mapper，关单这种**状态变更**走 controller 直连 mapper 违反 AGENTS.md §3.3（controller 只做参数校验与路由）。
  查询的条数收敛与关单的 CAS 结果判定都是业务规则，属于本层」；
  实现类不带事务的理由（`<J>/service/impl/AccountExceptionTicketServiceImpl.java:20-21`）「查询是单条 select，关单是单条 CAS UPDATE，
  单语句自身原子，没有第二个需要一起回滚的写。**NEVER 因为『像是写操作』就加事务注解**」。
- **判活必须走实体方法** —— `ItpUserQueryServiceImpl`（`<J>/service/impl/ItpUserQueryServiceImpl.java:152`）：
  「判活 MUST 走实体方法（DEL_YN 极性反直觉：1=有效、0=已注销），NEVER 裸比 getDelYn()」；
  脱敏口径（`:141`）「运营查询只返回脱敏手机号和姓名，避免页面暴露完整个人信息」。

#### 陷阱

- **运营口径三条查询与归档物理删除的关系** —— `ItpUserQueryServiceImpl.search`（`<J>/service/impl/ItpUserQueryServiceImpl.java:54`）：
  「运营口径含有效 + 已注销（已归档物理删除的行查不到，属预期），故用不带 DEL_YN 过滤的三条。」
- **`ROWNUM` 与工单查询**、**`where` 标签与恒真条件**、**XML 注释连续减号** —— 见 §一「陷阱」中对应条目
  （`<X>/AccountExceptionTicketMapper.xml:55-57`、`<X>/UserItpRegInfoMapper.xml:227-229`、`<X>/AccountExceptionTicketMapper.xml:15-17`）。

### 六、持久层与 mapper

#### 契约与判据

- **`DEL_YN` 过滤条件的唯一定义点** —— `<X>/UserItpRegInfoMapper.xml:32-50`（`Del_Yn_Active_Filter` / `Del_Yn_Canceled_Filter`）：
  「DEL_YN 过滤条件的唯一定义点。语义 1=有效、0=已注销（见 account-server-schema.sql 列注释），与直觉相反，
  因此本 mapper 内 NEVER 再手写该字面量」「命名沿用仓库既有的过滤片段约定（gate-txn-pay-server 的 Debit_Result_Filter / Recon_Exp_Filter）」。
- **销户语句的幂等语义与返回值口径** —— `UserItpRegInfoMapper.updateCancelByThirdUserId`
  （`<J>/mapper/UserItpRegInfoMapper.java:90-98`）：「`DEL_YN` 的语义是 **1 = 有效、0 = 已注销**（见 `account-server-schema.sql` 的列注释），
  与直觉相反，改动前 **MUST** 先确认」「一个 `thirdUserId` 可能有多条有效记录（多卡），本语句一次覆盖全部，因此返回值是被注销的票卡数。
  WHERE 带 `DEL_YN = 1` 使其天然幂等：重复调用返回 0 而不是报错，调用方 **MUST** 把 0 当成『已注销』而非失败。」
- **归档删除的前置条件** —— `UserItpRegInfoMapper.deleteCanceledByThirdUserId`（`<J>/mapper/UserItpRegInfoMapper.java:119-126`）：
  「调用前 **MUST** 已把这些行的快照写入 `USER_ITP_REG_LOG`，且 **MUST** 已确认该用户在 `APP_USER_PAY_CHANNEL` 中不再有任何支付通道——
  原表行删掉之后，`selectAnyByThirdUserIdAndCardIdAndCardType` 那条『忽略 DEL_YN 再查一次』的兜底就失效了，
  残留的支付通道将无法再被清理。」
- **带锁查询 NEVER 用于常规查询** —— `UserItpRegInfoMapper.selectAnyListByThirdUserIdForUpdate`
  （`<J>/mapper/UserItpRegInfoMapper.java:103-115`）：「与 `selectActiveListByThirdUserId` 的区别有两点：不带 `DEL_YN = 1`
  （归档发生在 IF8A-42 置注销态之后，用有效口径查必然是空集），以及带 `for update`」「本方法持锁到事务结束，**NEVER** 用于常规查询。」
- **换号日志主键先取序列后回填** —— `UserPhoneChangeLogMapper.insert`（`<J>/mapper/UserPhoneChangeLogMapper.java:17-22`）：
  「主键由 `<selectKey order="BEFORE">` 先取 `SEQ_USER_PHONE_CHANGE_LOG.NEXTVAL` 再回填到 `record.id`，
  调用方 insert 后可直接用 `UserPhoneChangeLog#getId()` 定位本行去落投递状态。**NEVER 改回 useGeneratedKeys**，见 mapper XML 注释。」
- **补偿扫表只回填五个字段** —— `UserPhoneChangeLogMapper.selectPendingSignSync`（`<J>/mapper/UserPhoneChangeLogMapper.java:67-77`）：
  「只回填 `id` / `thirdUserId` / `newMsisdn` / `signSyncStatus` / `signSyncRetryCount` 五个字段 —— 重推只需要这些，
  NEVER 为此把整行捞出来」「`SIGN_SYNC_STATUS` 为 NULL 的历史行永不被捞取，见 mapper XML 注释」；
  实体侧（`<J>/entity/UserPhoneChangeLog.java:59-62`）「NULL 表示本行早于 2026-09-11 的改造，补偿扫描 NEVER 捞取」。
- **`SIGN_SYNC_RETRY_COUNT` 显式写 0、不依赖列默认值** —— `<X>/UserPhoneChangeLogMapper.xml:19`：
  「SIGN_SYNC_RETRY_COUNT 显式写 0，NEVER 依赖列 DEFAULT——本表历史行该列可能为 NULL」（详见 ADR-D8）；
  累加侧（`:82`）「RETRY_COUNT 用 NVL 兜底，历史行该列为 NULL 时也能正确累加」。
- **工单表只增不删** —— `AccountExceptionTicketMapper`（`<J>/mapper/AccountExceptionTicketMapper.java:13-19`）：
  「**关单只能由人工触发**：`close` 的唯一调用方是运营后台的关单端点，**NEVER 让补偿任务或任何自动流程调它** ——
  工单存在的意义就是『本域已经自愈不了、必须有人看过』，自动关单等于把告警静音」「2026-09-11 之前本接口只有 `insert`、
  注释写着『刻意不提供 update / close』，那是因为当时关单只能人工连库 `UPDATE`；现补齐查询与 CAS 关单两个方法，
  『不自动关单』这一条约束不变」；开单幂等（`:25-27`）「同一笔重复开单会抛 `DuplicateKeyException`，调用方 **MUST** 捕获并当成
  『已有工单』正常继续，**NEVER 让它冒泡打断补偿批次** —— 补偿任务每 5 分钟重扫，达上限的行每轮都会走到这里」。

#### 决策理由

- **刻意不给 `DEL_YN` 建 Java 枚举（ADR-D41）** —— `<X>/UserItpRegInfoMapper.xml:36-38`：
  「本项目刻意不给 DEL_YN 建 Java 枚举（理由见 docs/domain/decisions.md ADR-D41）：判断点绝大多数在 SQL 里、
  Java 枚举对它们不可达，所以收口只能落在这两个片段上。」
- **状态机白名单的权威一律在 SQL 的 WHERE 里（同 ADR-D40）** —— `<X>/UserItpRegInfoMapper.xml:41-46`：
  「`Del_Yn_Active_Filter` 同时充当 `updateCancelByThirdUserId` 的 CAS 前置条件，NEVER 改成参数化（如 DEL_YN = #{delYn}）——
  那等于把状态机白名单交给调用方，本项目的白名单权威一律在 SQL 的 WHERE 里（同 ADR-D40）」；
  「『查询口径』与『CAS 前置条件』共用同一个片段是刻意的（只留一个字面量），代价是二者不能各自演进」。
- **`markSignSyncRejected` 复用「置到上限」而不新增状态值** —— `<X>/UserPhoneChangeLogMapper.xml:96-99`：
  「与 markSignSyncFailed 的唯一区别是 RETRY_COUNT 直接置成上限而不是 +1。终态靠 selectPendingSignSync 的
  NVL(RETRY_COUNT,0) 小于 maxRetry 过滤实现，因此置到上限等于『不再被扫到』，与自然重试耗尽走同一条机制。」
- **补偿调度源只有 web-admin 一处** —— `<X>/UserPhoneChangeLogMapper.xml:118-120`：
  「补偿扫表：捞出待重推的行，供 web-admin 的 Quartz 任务经 /phoneSignSyncCompensate 触发。
  account-server 侧 NEVER 加 @Scheduled —— 调度统一由 web-admin 前台可配的 sys_job 承担（用户 2026-09-11 要求，
  见 docs/architecture/web-server.md §七）」；端点侧的不鉴权前提在
  `TaskController`（`<J>/controller/task/TaskController.java:20-22`、`:76-79`）「本类的端点一律**不收业务入参**：
  扫描范围由 account-server 自己决定。这是它们可以暂不鉴权的唯一前提，**NEVER 给它们加『按流水号 / 按用户』这类外部可控参数**，
  否则就变成裸暴露的单笔数据操作接口，必须先有鉴权（AGENTS.md §5.2）」。
- **SQL 语句已有断言测试钉住** —— `<X>/UserItpRegInfoMapper.xml:39-40`、`:207`、`:222`：
  「UserItpRegInfoMapperSqlTest 已对全部语句加了断言」「不落 DEL_YN 字面量，由 delYnLiteralsStayInsideTheTwoFragments 钉住」。

#### 陷阱

- **两个过滤片段自带 `and`**、**`selectKey` 不能换 `useGeneratedKeys`**、**占位符必须带 `jdbcType`**、
  **`ROWNUM` 必须套在已排序子查询外层**、**SQL 正文注释与 XML 注释里的连续减号** —— 五条均已在 §一「陷阱」逐条列出，
  定位串分别是 `<X>/UserItpRegInfoMapper.xml:39-40`、`<X>/UserPhoneChangeLogMapper.xml:9-15`、`<X>/UserPhoneChangeLogMapper.xml:22-23`、
  `<X>/UserPhoneChangeLogMapper.xml:129-131`、`<X>/AccountExceptionTicketMapper.xml:12-17`。
- **入向验签配置的明文默认值** —— `ItpSignProperties`（`<J>/model/ItpSignProperties.java:16-23`、`:38-41`）：
  「**⚠️ `signKey` 仍带明文默认值，这是本轮刻意保留的既有缺陷**（用户 2026-09-11 裁定『先收成对象、真值暂不动』）。
  它违反 AGENTS.md §5.2『敏感项 MUST 写 `${ENV_VAR:}`（空默认值）』。**上线前 MUST 改成 `${ITP_SIGN_KEY:}` 并轮换该密钥**；
  改的时候连带把 `account-server/src/main/resources/application.properties` 里的同名真值一起清掉——
  **两处都写了真值，只改一处等于没改**」「**NEVER 把这个前缀下的键搬到别的类里用 `@Value` 再读一遍**：
  那会让『同一个 signKey 有两个读取点』，轮换密钥时必漏一处」。
- **验签器用的是 fastjson 1，换库即改签名字节** —— `AccountRequestVerifier`（`<J>/service/AccountRequestVerifier.java:20-28`）：
  「**⚠️ 本类当前没有任何调用点**（ADR-D35 / ADR-D37 复核：全模块 grep 只命中 `RequestApplicationController` 的构造器参数），
  因此账户域 24 个端点全部裸暴露」「**⚠️ 本类用的是 fastjson 1（`com.alibaba.fastjson`），而 AGENTS.md §5.1 要求统一 Fastjson2**。
  ADR-D37 **刻意没有替换**：`buildSignSource` 用 `SerializerFeature.MapSortField` 决定 `bizData` 的序列化字节，
  换库会改变签名源串 ⇒ 已发出的 sign 全部失配。这属 §5.2『安全红线：NEVER 擅自修改现有加密/签名逻辑』，
  **要换 MUST 与上游同批改并端到端比对签名**。」
- **对内端点当前无鉴权、URL 一个字都不能改** —— `PayChannelInternalController`
  （`<J>/controller/internal/PayChannelInternalController.java:21-26`、`:60-64`）：「**⚠️ 两个 URL 一个字都不能改**：
  `AccountClient.queryPayChannelByContractNo`（`rpc/.../AccountClient.java:130`）与 `AccountClient.syncPayAccountId`（同文件 :156）
  是硬编码路径，改这里等于让支付域两条链路同时 404，而 **404 会被上游 catch 成『远端不可用』、不会有编译期或单测报错**」；
  「**⚠️ 本端点是状态变更型接口但当前无鉴权**，与 AGENTS.md §5.2 冲突，属**有意为之的临时降级**（对齐 recon 的 `X-Recon-Token`
  已删除现状）……**上线前 MUST 补齐**」；只读端点（`:44-45`）「它会返回 cardId，补入向验签时 MUST 一并覆盖本端点」；
  内部票卡数据端点同款在 `CardDataInternalController`（`<J>/controller/internal/CardDataInternalController.java:34-39`）
  「本类**刻意不加类级 `@RequestMapping`**，搬迁后路径与搬迁前逐字节相同；加任何前缀都会让 ticket-server / fep-dev / face-pay /
  collect-pay 四个模块同时 404，而且**编译与单测都发现不了**」。
- **签约三字段漏 set 会让下游每次读到 null** —— `AccountProfileServiceImpl.queryUserInfo`
  （`<J>/service/impl/AccountProfileServiceImpl.java:119-122`）：「签约信息 MUST 一并返回：出站扣费链路
  （ticket-server → fep-dev-server → gate-txn-pay-server → pay-sign-server）靠这三个字段定位免密扣款的签约协议。
  原先漏了这三个 set，调用方 GateTicketHandler.applyActualCardType 每次都读到 null，
  只能由 pay-sign-server 再回查一次 account-server 兜底（2026-08-26 修复）。」

### 墓碑注释清单（建议转为断言测试）

以下注释的唯一作用是**禁止回退到某个已被推翻的做法或已搬走的位置**，正文不重复收录。
「可否断言化」按「能否写成不依赖运行环境的自动化断言（单测 / ArchUnit 式结构断言 / SQL 文本断言）」判断。

| # | 位置 | 它想禁止的事 | 可否写成断言测试 |
|---|---|---|---|
| 1 | `<J>/service/impl/AccountRegistrationServiceImpl.java:32-39` | 把任何渠道的开卡入口加回本类（ADR-D31，此前产生两对逐字节重复 helper） | 可：结构断言「本类不含 alipay 前缀方法 / 不依赖支付宝请求 DTO」 |
| 2 | `<J>/service/impl/AccountRegistrationServiceImpl.java:253-255` | 回退成「索引尚未执行、本分支不可达」的旧记载 | 否：需查库回执，属 ADR 记录 |
| 3 | `<J>/service/impl/AccountRegistrationServiceImpl.java:257-263` | 把撞唯一索引分支的存在当成「并发已闭环」的证据 | 否：属结论性告示 |
| 4 | `<J>/service/impl/AccountRegistrationServiceImpl.java:270-274` | 在 `handleDuplicateRegistration` 里 releaseReservation（ADR-D52） | 可：单测断言该分支零调用 `cardPoolClient.release*` |
| 5 | `<J>/service/impl/AccountRegistrationServiceImpl.java:83-89` | 把开单动作加进运营用的 `AccountExceptionTicketService` | 可：结构断言「自动流程类不依赖该 service」 |
| 6 | `<J>/service/RegistrationCommitService.java:23-29` | 把已移出的 `isDayPassCard` / `attachEmployeeCardsQuietly` 加回本接口 | 可：结构断言接口方法名集合 |
| 7 | `<J>/service/impl/RegistrationCommitServiceImpl.java:23-24` | 把 `attachEmployeeCardsQuietly` 迁回本类 | 可：同上 |
| 8 | `<J>/service/RegistrationCommitService.java:14-16` | 把渠道 if-else 挪进渠道无关的收口接口（ADR-D25 删掉的杂物间来源） | 部分：可断言不依赖渠道枚举，逻辑难断言 |
| 9 | `<J>/service/impl/AlipayTripRegistrationServiceImpl.java:228-239` | 回退成「`CARD_ISSUE_CODE` 差异待甲方对齐、属阻塞项」 | 否：属已撤回的结论 |
| 10 | `<J>/service/impl/AlipayTripRegistrationServiceImpl.java:28-31` | 把渠道无关三步在本类重新实现（ADR-D29 清掉的重复） | 可：结构断言本类委托给 `RegistrationCommitService` |
| 11 | `<J>/service/impl/AccountArchiveServiceImpl.java:29-30` | 让 Controller 直接依赖归档实现类 | 可：结构断言 controller 包不依赖 `AccountArchiveService*` |
| 12 | `<J>/service/impl/AccountArchiveServiceImpl.java:158-161` | 删掉 `tryArchiveAfterCancel` 里的 `transactionTemplate`、改成直接自调用 | 可：单测在无事务下断言仍开事务（或断言字段存在） |
| 13 | `<J>/service/impl/AccountCancelServiceImpl.java:47-49` | 销户侧换用解绑侧那个不吞异常的归档入口 | 可：单测断言归档抛异常时销户仍返成功 |
| 14 | `<J>/service/impl/AccountCancelServiceImpl.java:56-58` | 给 `userCancel` 加 `@Transactional` | 可：反射断言方法上无该注解 |
| 15 | `<J>/service/impl/PayChannelServiceImpl.java:47-49` | 把开户发号或销户逻辑挪进支付通道类 | 可：结构断言依赖集合 |
| 16 | `<J>/service/impl/PayChannelServiceImpl.java:162-167` | 再把钱包的 `REQ_CONTRACT_NO` 置 null | 可：单测断言钱包分支落库带 reqContractNo |
| 17 | `<J>/service/impl/PayChannelServiceImpl.java:298-305` | 给 IF8A-77 加回 `@Transactional` | 可：反射断言无该注解 |
| 18 | `<J>/service/impl/PayChannelServiceImpl.java:388-393` | 退回让两个 public 入口互相调用（ADR-D39） | 可：反射断言两入口各带 `@Transactional`，私有实现不带 |
| 19 | `<J>/service/impl/PayChannelServiceImpl.java:404-406` | 给 `doRemovePayChannel` 加 `@Transactional` | 可：同上 |
| 20 | `<J>/service/impl/PayChannelServiceImpl.java:790-796` | 把库里那行 `CARD_TYPE='02'` 残留改成 `0441` | 否：属数据处置裁决 |
| 21 | `<J>/service/PayChannelService.java:17-22` | 把两个「调用方是 pay-sign」的入口迁回 APP 契约面（ADR-D34） | 可：结构断言两接口方法名集合 |
| 22 | `<J>/service/PayChannelInternalService.java:23-32` | 把对内接口改成对 `PayChannelService` 的包装；把 APP 入口挪进来 | 可：结构断言无相互依赖 + 方法名集合 |
| 23 | `<J>/service/impl/PayChannelInternalServiceImpl.java:28-29` | 给对内实现加事务注解或注入 `PaySignClient`（会造出双向边） | 可：结构断言字段类型集合 |
| 24 | `<J>/service/impl/PayChannelInternalServiceImpl.java:92` | 把 IF8A-77 内的私有回写方法迁到对内契约面 | 可：结构断言方法归属 |
| 25 | `<J>/service/impl/PhoneChangeServiceImpl.java:311-315` | 把 outbox 扫描循环抄回本方法（ADR-D46，已第二次被推导） | 部分：可断言调用了 `OutboxScan#run` |
| 26 | `<J>/service/impl/PhoneChangeServiceImpl.java:85-87` | 给 `updatePhone` 加 `@Transactional` | 可：反射断言 |
| 27 | `<J>/service/PhoneChangeService.java:12-13` | 把 `SignSyncCompensateResult` 挪出本接口（等于改对外契约） | 可：结构断言类型所在位置 |
| 28 | `<J>/service/impl/EmployeeCardServiceImpl.java:40-47` | 把业务策略搬进出网协作者；在本类重新持有 `RestTemplate` / 出网地址 / 日志 mapper | 可：结构断言字段类型集合 |
| 29 | `<J>/service/impl/EmployeeCardOutboundServiceImpl.java:51-55` | 再往出网实现类加单独的 `@Value` 字段 | 可：反射断言本类无 `@Value` 字段 |
| 30 | `<J>/service/EmployeeCardPersistenceService.java:77-79` | 把 `recordEvent` 的事件类型改回 `String` | 可：反射断言形参类型是 `EmployeeCardEvent` |
| 31 | `<J>/domain/EmployeeCardEvent.java:13-14` | 改枚举名（落库字符串与历史数据绑定） | 可：断言 `name()` 集合等于 4 个既有取值 |
| 32 | `<J>/domain/EmployeeCardStatus.java:12-13` | 自行调整 ACC 定义的数字取值、依赖 `ordinal()` | 可：断言 `code()` 映射表 |
| 33 | `<J>/domain/AccResultCode.java:15-23` | 把 ACC 双码判据用在安全服务响应上；在本类重写字面量 | 部分：可断言常量来源；用法需结构断言 |
| 34 | `<J>/service/CardPoolAllocationService.java:45-56` | 在失败分支调 `releaseReservation`（ADR-D52）；因链路不再调用就删掉该方法 | 可：单测断言失败分支零 release + 方法仍存在 |
| 35 | `<J>/service/impl/CardPoolAllocationServiceImpl.java:34-41` | 把票种码 `03` 与支付渠道码 `03` 两个常量合并或互相引用；为复用去改 `model` 可见性 | 可：结构断言两常量所在类互不引用 |
| 36 | `<J>/service/impl/AccountRegistrationServiceImpl.java:52-58` | 据「另一处也是 03」推断本处语义 | 部分：同上 |
| 37 | `<J>/service/ItpUserQueryService.java:17-21` 与 `<J>/service/impl/ItpUserQueryServiceImpl.java:29-35` | 把 `PaySignClient` 注回运营只读路径（ADR-D30 消掉的 N+1） | 可：结构断言实现类字段不含 `PaySignClient` |
| 38 | `<J>/controller/page/ItpUserPageController.java:23-25` | 把 Mapper 或 RPC Client 注回 controller | 可：结构断言 controller 包不依赖 mapper / client 包 |
| 39 | `<J>/controller/page/AccountExceptionTicketPageController.java:21-23` | 把 Mapper 注回 controller | 可：同上 |
| 40 | `<J>/page/ItpPayChannelView.java:11-12` | 因「语义上属支付域」把 `PaySignClient` 注回只读路径 | 可：同 37 |
| 41 | `<J>/controller/ci/app/RequestApplicationController.java:41-45`、`:51-57` | 把 ADR-D34 / D35 已切走的入口迁回本类 | 可：结构断言 controller 方法名集合 |
| 42 | `<J>/controller/ci/app/RequestApplicationController.java:63-70` | 因「没人用」删掉悬空的 `AccountRequestVerifier` 依赖 | 可：反射断言构造器仍含该参数 |
| 43 | `<J>/service/AccountRequestVerifier.java:20-22` | 因「没人用」删掉本类 | 可：断言类存在 |
| 44 | `<J>/controller/internal/CardDataInternalController.java:41-43` | 把 `queryUserInfo` 挪进对内面（ADR-D35：真·双来源） | 可：结构断言两 controller 的方法归属 |
| 45 | `<J>/service/AccountProfileService.java:14-15`、`<J>/service/AccountCancelService.java:12-13`、`<J>/service/AccountRegistrationService.java:17-18` | 往这三个接口加销户 / 开户 / 查询 / 支付通道方法（防杂物间重现） | 可：结构断言各接口方法名集合 |
| 46 | `<J>/service/AlipayTripRegistrationService.java:16-18` | 把支付宝出行开卡合回 IF8A-01 的类（ADR-D31） | 可：同 1 |
| 47 | `<J>/service/AlipayTripRegistrationService.java:9-14` 与 `<J>/controller/ci/channel/FepAlipayTripRequestApplicationController.java:21-25` | 把任何模块的 `service.account.url` 指到本服务处理支付宝开卡（双写） | 部分：可写配置扫描断言，无法在单测内覆盖集群 env |
| 48 | `<J>/constant/AccountErrorCodeEnum.java:6-9` 与 `<J>/controller/ci/app/RequestApplicationController.java:155-157` | 为「看起来整齐」把员工码链路的 `9999` 改成 `9001` | 可：断言枚举取值 + 断言该端点失败分支返 9999 |
| 49 | `<J>/mapper/AccountExceptionTicketMapper.java:13-19`、`<J>/service/AccountExceptionTicketService.java:14-16`、`<J>/entity/AccountExceptionTicket.java:41` | 让补偿任务或任何自动流程调用关单 | 可：结构断言只有 page controller 依赖 `close` |
| 50 | `<J>/entity/AccountExceptionTicket.java:9-10` | 其它域写这张工单表 | 否：跨模块约束，需全仓扫描 |
| 51 | `<J>/model/EmployeeCardOutboundProperties.java:12-14` | 为回退 `@ConfigurationProperties` 去改配置键名 | 可：断言键名集合（绑定测试） |
| 52 | `<J>/model/ItpSignProperties.java:22-23` | 把 `itp.*` 前缀的键搬到别的类里用 `@Value` 再读一遍 | 可：全仓 grep 式断言 |
| 53 | `<J>/service/AccountRequestVerifier.java:24-28` | 把验签用的 fastjson 1 换成 Fastjson2（会改签名源串字节） | 部分：可断言依赖包名，语义需端到端比对 |
| 54 | `<X>/UserItpRegInfoMapper.xml:34-46`、`:151`、`:175-176`、`:207`、`:222` | 在片段外手写 `DEL_YN` 字面量；给归档查询加有效过滤；改片段参数化 | 可：已有 `UserItpRegInfoMapperSqlTest` / `delYnLiteralsStayInsideTheTwoFragments` |
| 55 | `<X>/UserItpRegInfoMapper.xml:326`、`:344-346` | 删掉销户 / 归档语句里的过滤片段 include，或把归档条件换成有效口径 | 可：SQL 文本断言 |
| 56 | `<X>/UserPhoneChangeLogMapper.xml:9-15`、`<X>/AccountExceptionTicketMapper.xml:9-10` | 把 `selectKey` 改成 `useGeneratedKeys` | 可：SQL 文本断言 |
| 57 | `<X>/UserPhoneChangeLogMapper.xml:100-103`、`<J>/mapper/UserPhoneChangeLogMapper.java:51` | 为「业务拒绝」新增 `SIGN_SYNC_STATUS` 取值 | 可：SQL 文本断言取值集合 |
| 58 | `<X>/AccountExceptionTicketMapper.xml:57` | 简化工单查询的 `ROWNUM` 子查询写法 | 可：SQL 文本断言 |
| 59 | `<X>/UserAccEmployeeCardMapper.xml:35` | 在不需要照片的查询里直接用 `BaseColumnList`（CLOB） | 可：SQL 文本断言 `selectActiveByPhone` 用 `LeanColumnList` |
| 60 | `<X>/TerminationTimeSummaryMapper.xml:6`、`<J>/mapper/TerminationTimeSummaryMapper.java:16-17` | 在该 mapper 里写 `APP_TERMINATION_REQUEST`（写路径权威在 pay-sign） | 可：SQL 文本断言无 insert/update/delete |
| 61 | `<J>/controller/task/TaskController.java:20-22`、`:76-79` | 给补偿端点加「按流水号 / 按用户」这类外部可控参数 | 可：反射断言端点无入参 |
| 62 | `<J>/controller/task/TaskController.java:81-82` | 把补偿触发改成同步等待 | 部分：可断言立即返回（不阻塞） |
| 63 | `<J>/service/impl/PhoneChangeServiceImpl.java:70-73` | 在换号类里改 `CARD_STATUS`（状态机第二个写入方） | 可：结构断言本类不调用状态更新方法 |
| 64 | `<J>/domain/PhoneChangeRule.java:19-25` | 给 `decide` 补 `isActive()` 判断（把权威从 SQL 挪到 Java） | 可：单测断言 `DEL_YN` 由 SQL 口径决定 |
| 65 | `<J>/domain/ChannelBindingRule.java:16-22`、`:27-34` | 改 8001 文案措辞；再写一份 `"0B"` 字面量；把 IF8A-77 字段集合并进来 | 可：单测断言文案常量 + 全仓 grep 断言 |
| 66 | `<J>/service/EmployeeCardPersistenceService.java:14-16` | 给 `recordEvent` / `attachEmployeeCardsQuietly` 补 `@Transactional` | 可：反射断言 |
| 67 | `<J>/service/impl/EmployeeCardPersistenceServiceImpl.java:185` | 同上（实现侧重复告示） | 可：同上 |
| 68 | `<J>/service/AccountArchiveService.java:6-8` | 改成由支付域调一个「请归档」的端点 | 部分：可结构断言无此端点 |
| 69 | `<J>/service/impl/AccountRegistrationServiceImpl.java:62-64` | 让其它场景复用开户的卡池 `BUSINESS_TYPE` 取值 | 可：全仓 grep 断言该常量唯一 |

### 本次抽取中归类存疑的注释（待人工裁决，不进正文结论）

- `<J>/service/RegistrationCommitService.java:36-37` —— 「失败时 MUST 释放卡池预占」与 ADR-D52 的「任何失败分支都不 release」
  在文字上相反（前者是接口 Javadoc 的旧措辞，后者是实现与 ADR 的现行口径）。本次按原文保留、未合并。
- `<J>/service/PhoneChangeService.java:17-27` —— Javadoc 用 `@link AccountApplicationService#updatePhone` 指向一个已删除的类，
  既像契约引用又像墓碑，未归类。
- `<J>/service/impl/EmployeeCardServiceImpl.java:111-113` —— 唯一一条英文注释（ACC cardNo 是员工号、APP 侧做静默开户），
  介于契约与说明之间，未归类。
- `<J>/service/impl/PayChannelServiceImpl.java:132-139` —— 整段被注释掉的卡信息比对实现（非文字注释而是失效代码），
  未按四类归档。
- `<J>/service/impl/AccountRegistrationServiceImpl.java:41-44` 与 `:108-113` —— 同一条「不带事务」的理由在类注释与方法注释各写一遍，
  归入契约与判据一次，未按两条计。
