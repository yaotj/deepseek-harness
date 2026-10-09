---
业务域: 账户开户 / 支付通道 / 电子员工卡
模块: account-server, fep-acc-server, alipay-account-server
---

# 提示词：账户与电子员工卡

## IF8A-01 当前开户规则（2026-09-17，ADR-D122，account-server 2.0.75）

本节取代下文 2026-09-16 注释摘录及旧 ADR 中“Y/C 一律多开”“用户+卡类型两字段唯一”的当前规则描述；历史证据保留，不再作为新开户实现依据。

- `ticketLimit` 必填，只接受去空格后的 `1/2`。`1` 按 `thirdUserId + ISSUE_ORG_CODE + 映射后 CARD_TYPE` 查询有效唯一卡，命中返回 `8002` 和该卡号；`2` 每次请求新卡。主卡查询绝不能返回 `ticketLimit=2` 的同行卡。
- APP `cardIssueCode` 原值存 `ISSUE_ORG_CODE`，非空且至多 16 CHAR，不校验机构字典。`CARD_ISSUE_CODE` 仍是码体发行渠道 0001/0007，不能用于所属方查重。第三方用户标识仍至多 64 CHAR；手机号等原校验保留。参数错误统一 8001。
- 0441 用途：N 主卡通常传 1，Y 同行码通常传 2，C 平台卡按不同所属方及 ticketLimit 处理。服务不新增 N/Y 与 1/2 交叉校验。对外卡类型映射沿用 `02`/`41` → `0441`，不新增直接传 0441 的入口兼容。
- 新卡落库 `TICKET_LIMIT`；历史 41 行未回填。查重和 GLOBAL 函数唯一索引 `UK_UIRI_ACTIVE_USER_CARDTYPE` 同用条件：`DEL_YN=1 AND (TICKET_LIMIT='1' OR (TICKET_LIMIT IS NULL AND NVL(COMPANION_FLAG,'N') NOT IN ('Y','C')))`。旧普通卡参与、旧 Y/C 排除；新 C+1 会被约束。禁止把条件改成所有有效卡。
- 唯一卡卡池业务号是带长度前缀的完整三字段：`UNIQUE:<用户UTF-16长度>:<用户><所属方UTF-16长度>:<所属方>:<映射票种>`；多卡为 `MULTI:<UUID>`，重试也会开新卡。长度前缀避免字段包含冒号时碰撞，不复用旧两字段预占。失败仍不释放共享预占；确认失败仍开异常工单。
- `DuplicateKeyException` 只在传 1 时回查同一组合；只有查到有效且卡号非空的唯一卡才返 8002，查不到返 9001。传 2 不把其他唯一冲突伪装成已开户。
- N/Y/C 原值保留给账户查询、过闸、订单。C 目前还影响钱包累计和公交换乘推送，本次不改。IF8A-77 保留线上 2.0.74 的所属方修复：按 `ISSUE_ORG_CODE` 查 C 卡。
- 已执行独立 `account-server-ticket-limit-migration.sql`：无默认值可空列、1/2 CHECK、三字段 GLOBAL 唯一索引。历史核验摘要一致；不是只改 schema。切换步骤与失败处置见 `docs/ops/account-ticket-limit切换.md`。
- Apifox IF8A-01 须补齐 cardIssueCode/ticketLimit 及 N+1/Y+2/C+1/C+2 示例，工作区未提供 Apifox 配置文件/连接器。

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
- **销户分两段，不要只看一段**：IF8A-42 先原地标记（`DEL_YN=0` + `DEL_THIRD_USER_ID` + `UN_REG_TMS`，`updateCancelByThirdUserId`），记录仍在原表；等 IF8A-75 把**最后一个签约渠道**解绑掉，`requestRemovePayChannel` 链路在 `PayChannelServiceImpl:410` 调 `AccountArchiveServiceImpl.archiveIfLastChannelRemoved`（**旧名 `AccountApplicationServiceImpl.archiveUserInfoIfLastChannelRemoved`，随账户域拆分改名，2026-09-23 核对**）才做归档——往 `USER_ITP_REG_LOG` 写 `OPER_TYPE=3` 快照后 `deleteCanceledByThirdUserId` 物理删原表行（WHERE 带 `DEL_YN = 0`，不误删并发新开户）。**「用户信息历史表」就是 `USER_ITP_REG_LOG`**（用户 2026-09-08 裁决复用），因此 **NEVER 新建 `*_HISTORY` 表**——同一事实两处存储、既有查询都不认。代价是该表只有 6 个业务列，原表 19 列中的 `ITP_CARD_TYPE` / `USER_NAME` / `USER_ID` / `CARD_ISSUE_CODE` / `REG_TMS` 等 13 列归档后不可恢复，已被接受。
  - ⚠️ `AccountArchiveServiceImpl.archiveIfLastChannelRemoved` 内部**先 `for update` 取锁、后 count**，这个顺序不可颠倒。**NEVER 按旧名 `archiveUserInfoIfLastChannelRemoved` 去搜**——该方法随账户域拆分已改名（2026-09-23 核对）。
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
  - **`COMPANION_FLAG` 缺字段 —— 真阻塞，卡在支付宝侧报文**。支付宝请求 DTO 没有该字段，而 IF8A-77 按 `COMPANION_FLAG='C'` 定位（`selectByThirdUserIdAndIssueOrgCodeAndCompanionFlag`），所以支付宝开的卡到不了 IF8A-77。实测影响面：全库 `COMPANION_FLAG='C'` 仅 **12 行、全部 `ISSUE_ORG_CODE=0008`（成都地铁）/ `CARD_TYPE=0441`**，即 IF8A-77 目前只服务这 12 张卡。**MUST 先由支付宝渠道在报文里给出该标识，NEVER 在本侧硬填 'C'**（那等于把本人卡并进亲情卡集合）。
  - **IF8A-77 的定位谓词曾错用 `CARD_ISSUE_CODE`，2026-09-17 / 2.0.74 改为 `ISSUE_ORG_CODE`（ADR-D128），NEVER 回退**。开户时 `AccountRegistrationServiceImpl:314-315` 把 APP 上送的机构码原值写进 `ISSUE_ORG_CODE`、把 `toIssueChannelCode4` 的归一值写进 `CARD_ISSUE_CODE`（上送 `0008` 落库 `0001`），而 IF8A-77 拿上送值去比 `CARD_ISSUE_CODE` **恒 0 行** —— 第三方票的 `CHANNEL` 因此永远补不上，拉码 IF8A-03 一路返 `8001 用户签约渠道不能为空`。**NEVER 改成「先归一再比 `CARD_ISSUE_CODE`」**：`0008` / `0004` / `0020` / `5412` 归一后全是 `0001`，分不出第三方来源、会串到别的机构的卡上。
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
  滞留预占「一律由 `sys_job` 240『卡池数据导入』的超时回收兜底」（2026-09-21 由 107『卡池维护』改号改名）。
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
- **卡池发号的幂等边界（ADR-D122 已替代旧规则）**：只由 ticketLimit 控制，1 使用完整三字段长度前缀键，2 使用每请求 UUID，详见顶部当前规则。
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
  `ISSUE_ORG_CODE` 保留 APP 所属方原值，ADR-D122 起参与唯一卡查重，仍不参与码体拼装。
- **所属方为开放集合**：`normalizeIssueOrgCode` 只去空格，不做字典校验，也不再对未知所属方打 ERROR。
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
  滞留的预占交给 sys_job 240『卡池数据导入』的超时回收」；同文件 `:270-274`「**本方法 NEVER releaseReservation**（2026-09-14 / ADR-D52 起，
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
  请求内也拿不到『有没有兄弟正要 confirm』的信息，因此唯一正确的做法是**不释放**，交给 `sys_job` 240『卡池数据导入』
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

## 附：fep-acc-server 源码注释知识抽取（2026-09-16，阶段一）

> **有并发写入者，引用行号前 MUST 先 grep 现查。** 本节行号是 2026-09-16 抽取时刻的快照。
>
> 抽取范围：`fep-acc-server/src/main/java/**/*.java`（3 个文件）+ `fep-acc-server/src/main/resources/application.properties`。
> 该模块无 DB、仅 2 个接口，**注释总量 7 处（Java 6 处 + properties 1 处）**，其中 4 处是复述类名 / 方法名 / 参数名的普通
> Javadoc，按规则丢弃：`FepAccServer.java:8~10`、`BaseAccController.java:6~8`、`BaseAccController.java:11~17`、
> `EmployeeCardController.java:16~18`。**照实报数量，未拿普通 Javadoc 凑篇幅。**
>
> 定位串写法：`fep-acc-server` + 类名.方法名 + 相对路径:行号。`<J>` =
> `fep-acc-server/src/main/java/com/chinasofti/huateng/fep/acc`。

### 一、两个接口的契约

| # | 定位串 | 注释原文承载的契约 |
|---|---|---|
| 1 | `fep-acc-server` `EmployeeCardController.employeeCardNotify` — `<J>/controller/EmployeeCardController.java:28~36` | 「接收员工码开卡通知。请求以 `multipart/form-data` 提交 **APP 同款公共字段**，业务参数放在 `bizData` 中，例如：`{"cardList":[{"phone":"13800138000","cardNo":"QD20240001","cardStatus":1}]}`」。三条可用信息：①入向 content-type 是 `multipart/form-data`；②公共字段与 APP 侧同形；③`bizData` 是 `cardList` 数组，元素含 `phone` / `cardNo` / `cardStatus`，`cardStatus` 的示例值是数字 `1` |
| 2 | `fep-acc-server` `EmployeeCardController.employeeInfoUpdateNotify` — `<J>/controller/EmployeeCardController.java:44~46` | 「接收员工信息变更通知并**剥离 ACC 公共消息头**」。本模块对这条通知的职责被注释限定为「剥头 + 转发」 |

注释中**没有**出现错误码、重试语义、幂等键或字段长度约定 —— 这四项在本模块注释里为空，**NEVER 据本节推断本模块有错误码映射**。

### 二、转发与路由

| # | 定位串 | 注释原文承载的事实 |
|---|---|---|
| 3 | `fep-acc-server` `application.properties:9` | 「account-server RPC 地址：默认值用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11）。」—— 该默认值的出处是**集群实测**，不是推断值 |

两个接口的转发目标（`AccountClient.notifyEmployeeCardStatus` / `AccountClient.updateEmployeeInfo`）**只体现在代码里，注释未记载**，本节不代笔补写。

### 三、配置与部署（含 jkube goals 被注释这一事实的出处）

| # | 定位串 | 注释原文 / 事实 | 归类 |
|---|---|---|---|
| 4 | `fep-acc-server` `application.properties:10` | 「**NEVER 写 127.0.0.1——在 K8s 里等于打到自己。**」（原文保留，与 §5.2「`service.*.url` 键存在但值指向自身 Pod」同型） | 陷阱（同一行亦列入文末墓碑清单） |
| 5 | **来源是 pom，不是 Java 注释**：`fep-acc-server/pom.xml:78~80` | execution `build-image-after-package` 的 `<phase>package</phase>` 在位，但 `<goal>build</goal>` / `<goal>push</goal>` / `<goal>deploy</goal>` **三行全部包在 XML 注释里**（`<!-- -->`）；`<configuration>`（`namespace itp` / `pushRegistry os-harbor-svc.default.svc.cloudos:443` / `image.prefix` = `fep-acc`，`pom.xml:27`）是完整的。**这是被注释掉的 XML 元素，不是文字注释 —— pom 里没有任何一句话说明「为什么注释掉」。** 后果与出镜像方式（`mvn k8s:build k8s:push`，实测 `itp/fep-acc:1.0.5`）记在 `AGENTS.md` §7，不在本模块任何注释里 | 事实（出处 = pom） |

### 决策理由类：0 条

「为什么只做转发不落库」「为什么 jkube goals 被注释掉」这两条在 `fep-acc-server` 的 **Java 注释与 properties 注释里都没有出处**，本次不代笔补写。

### 墓碑注释清单（建议转为断言测试）

| # | 文件:行号 | 禁止的事 | 能否断言化 |
|---|---|---|---|
| 1 | `fep-acc-server/src/main/resources/application.properties:10` | 把 `service.account.url` 写成 `127.0.0.1`（在 K8s 里等于打到自己） | 部分：可写配置扫描断言（`service.*.url` 不含 `127.0.0.1` / `localhost`），但**无法在单测内覆盖集群 env 覆盖值** |

### 本次抽取中归类存疑的注释（待人工裁决，不进正文结论）

- `<J>/controller/EmployeeCardController.java:44~46` —— 前半句「接收员工信息变更通知」是复述方法名（本应丢弃），后半句「剥离 ACC 公共消息头」是契约。
  本次按整条计入契约一次，未拆成两条。
- `<J>/controller/BaseAccController.java:11~17` —— 归为普通 Javadoc 丢弃，但该方法代码里有一条注释未覆盖的行为：
  `bizData` 为 `null` 或空白时按 `"{}"` 解析。**该行为无任何注释记载**，本次未代笔补写成条目。
- `fep-acc-server/pom.xml:102` —— `<outputDirectory>target</outputDirectory>` 被注释掉一行（失效代码而非文字注释），未按四类归档。

## 附：account-server 源码注释知识抽取（2026-09-16，阶段二）

> **有并发写入者，引用行号前 MUST 先 grep 现查。** 本节行号是 2026-09-16 抽取时刻的快照（仓库 pom `<version>` 当时是 **2.0.73**；
> **NEVER 拿这个号推断线上版本**，判线上 MUST 查 Deployment 的 image tag，见 AGENTS.md §7）。
>
> 本轮定位：阶段一（本文件上一节）已覆盖开户 / 支付通道 / 员工卡 / 卡池 / 运营 page / 持久层六个切面的**主实现类**。
> 本轮用「逐文件枚举注释行 → 减去阶段一定位串命中的行区间」的差集法找漏，覆盖对象是**阶段一未落笔的文件与行段**：
> 5 个 controller 包、11 个 service **接口**头、4 个 domain 取值类、2 个 `@ConfigurationProperties`、
> 6 张实体、5 个 `sql/*.sql`、`application.properties`，以及各实现类内被阶段一跳过的零散行。
> **本轮只补漏，NEVER 与阶段一重复**；同一事实在两处都有注释时（如「不带事务」的理由在接口与实现各一份），本轮只记接口侧那份并注明。
>
> 路径前缀沿用阶段一：`<J>` = `account-server/src/main/java/com/chinasofti/huateng/account/`，
> `<X>` = `account-server/src/main/resources/mapper/`，另加 `<S>` = `account-server/src/main/resources/sql/`。

### 一、controller 层（四个契约面的切分判据）

- **对内契约面「按调用方切、不按业务切」，且 URL 一个字都不能改** —— `CardDataInternalController` 类注释
  （`<J>/controller/internal/CardDataInternalController.java:15-45`）：「票卡数据的**对内契约面**：只被 ITP 内部服务调用，
  报文**不经过 fep-app / fep-acc 接入层**」。两个入口的调用方是 2026-09-11 全仓 grep `accountClient.xxx` 实测的：
  `queryCardTypeByCardId` ← ticket-server（`GateTicketHandler:612`、`CardDataHandler:526/556`）、fep-dev-server
  （`GateTransactionHandler:169`）；`updateHceData` ← ticket-server（`GateTicketHandler:590`）、face-pay-server
  （`F2fHceService:163`）、collect-pay-server（`BomOrderServiceImpl:1648`），两者**零外部调用方**。
  「**为什么单独立类**……混在一起的后果不是『不好看』，而是**补验签时没有落点**——外部面 MUST 走 `AccountRequestVerifier`，
  内部面走的是服务间直连、没有 APP 报文骨架也没有 `sign`，在同一个类上按方法开例外必然遗漏。同 ADR-D34 的判据」；
  「**⚠️ 两个 URL 一个字都不能改**：`AccountClient.queryCardTypeByCardId`（`rpc/.../AccountClient.java:136`，
  **注意它把 `?cardId=` 拼在路径里**）与 `AccountClient.updateHceData`（`:165`）都是硬编码字符串常量。
  本类**刻意不加类级 `@RequestMapping`**……加任何前缀都会让 ticket-server / fep-dev / face-pay / collect-pay 四个模块同时 404，
  而且**编译与单测都发现不了**」；「**NEVER 把 `queryUserInfo` 挪进来**」——它的调用方同时含内部（ticket-server、
  gate-txn-pay-server、pay-sign-server）与外部（fep-app-server 的 `IndustryDataServiceImpl`），是真·双来源（ADR-D35）。
- **对内端点的两条 URL 形态不一致是历史现状** —— `PayChannelInternalController` 类注释
  （`<J>/controller/internal/PayChannelInternalController.java:14-29`）：「唯一调用方是 pay-sign-server（ADR-D34）」，
  切出的收益是「**鉴权有了单一落点**：上线前补入向校验只需拦本类，不必在 APP 端点上开例外」；
  「`AccountClient.queryPayChannelByContractNo`（`rpc/.../AccountClient.java:130`）与 `AccountClient.syncPayAccountId`
  （同文件 `:156`）是硬编码路径，改这里等于让支付域两条链路同时 404，而 **404 会被上游 catch 成『远端不可用』、
  不会有编译期或单测报错**。因此 `/queryPayChannelByContractNo` 保留在根路径下、**NEVER 为了整齐挪进 `/internal/` 前缀**；
  两个路径的不一致是历史现状，要统一 MUST 与 rpc 模块同批改」；「**本类只做路由与入参日志，NEVER 写业务逻辑**」。
- **`syncPayAccountId` 是状态变更型端点、当前无鉴权且是有意降级** —— 同文件 `:54-65`：「调用方是 pay-sign-server 的
  `SignResultCommittedListener`，走 `AccountClient.syncPayAccountId`。补 ADR-D30 的覆盖率缺口：该列原先只有 IF8A-77 会写」；
  「**⚠️ 本端点是状态变更型接口但当前无鉴权**，与 AGENTS.md §5.2 冲突，属**有意为之的临时降级**（对齐 recon 的
  `X-Recon-Token` 已删除现状）。风险面比 recon 那批小：它只能按签约流水号改一列展示值、改不了任何业务状态。
  **上线前 MUST 补齐**，补时与 `/queryPayChannelByContractNo` 一并处理」。
- **只读端点也要进验签范围** —— 同文件 `:41-46`：「只读、不改状态，因此不落在 AGENTS.md §5.2『状态变更型接口必须鉴权』范围内；
  **但它会返回 cardId，补入向验签时 MUST 一并覆盖本端点**。」

- **内部路径与对外规范名不一致（IF8A-42）** —— `RequestApplicationController.userCancel`
  （`<J>/controller/ci/app/RequestApplicationController.java:121-127`）：「内部路径保持 `/userCancel`
  （`AccountClient.userCancel` 按此调用），对外规范名 `/app/cancelAccount` **只在 fep-app-server 落地**，
  属实现细节差异，与 if8a_76 的 `newMsisdn` 同一处理方式。」查「某接口叫什么」MUST 同时看两侧，
  **NEVER 因为账户域没有 `/app/cancelAccount` 就判定接口未实现**。
- **钱包解绑复用本地删通道事务** —— 同文件 `:112-114`：「钱包协议约定的解绑入口。**钱包没有支付平台签约协议**，
  复用本地支付通道删除事务。」（对应 `PayChannelService:64` 的「钱包 `requestAgreeRelease` 兼容入口」。）
- **补偿端点的三条设计约束** —— `TaskController.phoneSignSyncCompensate`
  （`<J>/controller/task/TaskController.java:73-87`）：「**不收任何入参**：触发的是本服务内部的扫表动作，
  扫描范围由 account-server 自己决定。**这是本端点可以不鉴权的前提**（`docs/architecture/web-server.md` §7.3）——
  **NEVER 给它加『按流水号 / 按用户重推』这类外部可控参数**，那会变成裸暴露的单笔数据操作接口，必须先有鉴权」；
  「**立即返回『已受理』，批处理交后台单线程执行**……上一批未跑完时直接返回『进行中』，**NEVER 改成同步等待**」；
  「幂等：重复触发最多多打几次 RPC，状态回写由 CAS 兜住」；返回码口径「已有批次在跑时返回提示**但仍是成功码**，
  **避免 Quartz 记失败**」。重入判据是进程内 `AtomicBoolean signSyncRunning`（`:47`），
  **无分布式锁 ⇒ 多副本下每个副本各跑一批**；执行器在 `@PreDestroy` 关闭（`:124`「避免容器停止时线程泄漏」）。
- **Quartz 联调端点是空壳** —— `TaskController.quartzDemo`（`<J>/controller/task/TaskController.java:59-63`）：
  「web-server Quartz RPC 联调接口，**只记录日志，不处理账户业务数据**」，恒返成功。排查「Quartz 任务有没有打到账户域」
  可以用它，**NEVER 把它当业务健康探针**。
- **运营列表的条数收敛在 service、不在 controller** —— `AccountExceptionTicketPageController.list`
  （`<J>/controller/page/AccountExceptionTicketPageController.java:46-51`）：「三个筛选项都可为空；不传 `ticketStatus`
  时返回全部状态，运营日常关注的是 `OPEN`」「`limit` 条数上限，缺省 50、上限 200，**收敛规则在 service 层**」，
  实现侧常量在 `AccountExceptionTicketServiceImpl:27`（缺省）与 `:30`（「单次查询条数上限。**这张表只增不删、
  没有归档任务，NEVER 放开成全量查询**」）。
- **注册量统计的日期窗是闭区间** —— `ItpUserPageController.countByCardType`
  （`<J>/controller/page/ItpUserPageController.java:72`）：「注册量统计：按票种分组计数，可选注册日期窗
  （`yyyy-MM-dd`，**闭区间**）。」与 `<X>/UserItpRegInfoMapper.xml` 里「结束边界用 `TO_DATE(end) + 1` 的开区间」
  是同一件事的两侧表述，改一侧 MUST 看齐另一侧。
- **员工码 controller 的五个入口都是「内部业务入口」** —— `EmployeeCardController`
  （`<J>/controller/ci/employee/EmployeeCardController.java:15-52`）：类注释「员工码 account-server **内部**业务入口」，
  其中 ACC 状态通知与 ACC 员工资料变更两个入口的注释都写着「**经前置服务剥离公共报文后**的业务入口」——
  即账户域这两个端点**不解析 APP / ACC 报文骨架**，剥头动作在 `fep-acc-server`（见本文件 fep-acc 阶段一 §一第 2 条）。
  **NEVER 在这两个端点上补 `bizData` 解析或验签**，落点应在前置。
- **构造器注入是全模块统一形态（ADR-D37）** —— 同一句「构造器注入（ADR-D37）。依赖全部 final，**漏注入在编译期即报错**」
  在 7 个类上逐字重复：`FepAlipayTripRequestApplicationController:36`、`CardDataInternalController:52`、
  `PayChannelInternalController:36`、`AccountArchiveServiceImpl:44`、`AccountCancelServiceImpl:66`、
  `AccountProfileServiceImpl:34`、`CardPoolAllocationServiceImpl:52`、`PayChannelServiceImpl:76`、
  `PayChannelInternalServiceImpl:37`、`PhoneChangeServiceImpl:91`、`RegistrationCommitServiceImpl:46`、
  `AccountRequestVerifier:42`。**新增 Bean MUST 照此形态，NEVER 回退成 `@Autowired` 字段注入**。

### 二、application service 接口层（六轮拆分留下的归属判据）

- **六个接口互为「NEVER 加进来」的对偶清单** —— 拆分后每个接口头都写了自己**不收**什么，四条合起来才是完整判据：
  `AccountProfileService`（`<J>/service/AccountProfileService.java:8-16`）「原 `AccountApplicationService` 是**杂物间**，
  已整体删除。三个入口的共同点是**只读写 `USER_ITP_REG_INFO` 自身的资料字段、不碰任何状态机**」「**NEVER 往本接口加销户、
  开户或支付通道方法**」；`AccountCancelService`（`:6-14`）「原 `AccountApplicationService` 是『销户 + 查询 + HCE +
  两个转发』的杂物间，**六个方法讲四件不相干的事**，已整体删除并按概念拆开。本接口只管销户」；
  `AccountRegistrationService`（`:6-19`）「**IF8A-01 APP 渠道开户，只有这一个入口**……唯一调用方是
  `RequestApplicationController`」「**NEVER 把支付宝入口挪回本接口**」；`PayChannelService`（`:12-26`）
  「支付通道的 **APP 契约面**（`APP_USER_PAY_CHANNEL` 的增删改 + 开户记录上的默认通道字段）」。
  这组注释是**判断「新方法该放哪个接口」的唯一成文判据**，改动前 MUST 通读四份。
- **删通道与归档的同事务约束写在接口上** —— `PayChannelService.requestRemovePayChannel`
  （`<J>/service/PayChannelService.java:53-61`）：「删掉最后一个渠道时会**同事务**调
  `AccountArchiveService.archiveIfLastChannelRemoved`，归档失败即整单回滚返错误码让上游重试，
  **NEVER 留『通道已删、归档未做』的半成品**。」（实现侧同一条在阶段一已记，本处是**接口契约面**的那一份。）
- **运营只读路径 2.0.63 起零跨域 RPC，且 NEVER 加回** —— `ItpUserQueryService`
  （`<J>/service/ItpUserQueryService.java:10-23`）：「原先那个 controller 不只是透传：它自己按查询类型分派、
  按渠道逐条调支付域 RPC、并组装脱敏视图与 `terminationReady`，属于 AGENTS.md §3.3 禁止的『controller 写业务逻辑』。
  **NEVER 把 Mapper 或 `PaySignClient` 注回 controller**」；「**2.0.63 起本接口的实现内没有任何跨域 RPC**（ADR-D30）：
  `payChannels` 的『支付账号』原先按渠道**逐条**调 `paySignClient.querySignInfoBySeq`（N+1），现改读
  `APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID` 本地列……**NEVER 为了『查得更全』把 RPC 加回来** —— 运营列表页每行一次跨域 HTTP
  只换一个展示字段，代价与收益不成比例；**覆盖率问题的正解是补齐回写点，不是在读路径上兜底**。」
- **批量导入查询的三条口径** —— `ItpUserQueryService.searchByCardIds`（`<J>/service/ItpUserQueryService.java:57-65`）：
  「按逻辑卡号列表批量检索开户记录，返回**脱敏后**的视图（口径与 `search` 的 CARD_ID 分支一致，**含有效与已注销**）」；
  「列表长度上限由 controller 强制（**500**），本方法**假定入参已合规**；内部只做 trim、去空白、去重。
  **空列表直接返回空结果，不打数据库**」；「永不返回 `null`」。注册量统计同段（`:47-54`）：「**只读聚合，不含个人信息**。
  日期为闭区间，`null`/空表示不限」「各票种注册量列表（**按数量降序**），永不返回 `null`」。
- **`SignSyncCompensateResult` 挪位置等于改对外契约** —— `PhoneChangeService`（`<J>/service/PhoneChangeService.java:3-13`、
  `:33-42`）：「第六轮起本接口即对外契约本身（原先的转发层 `AccountApplicationService` 已删除），**NEVER 让 Controller
  直接依赖它**……换成两套入口就会出现『同一能力两个调用面』」；「返回类型 `SignSyncCompensateResult` 已随第六轮搬入本接口：
  它是 `TaskController` 已经在用的类型，**挪位置等于改对外契约**」；record 三个分量语义「`scanned` 本批扫出的待重推条数 /
  `success` 重推成功并已置 SUCCESS 的条数 / `failed` 仍失败、已累加重试次数的条数」，且「**仅用于日志与调用方回执，不落库**」。
  **注意这里有一条与代码矛盾的 Javadoc，见 §矛盾与待裁决第 2 条。**
- **开户收口接口的收敛判据被改过一次（ADR-D33 修订）** —— `RegistrationCommitService` 类注释
  （`<J>/service/RegistrationCommitService.java:14-31`）：「原判据写的是『入参只有 `UserItpRegInfo` 的方法才属于这里』，
  但它**被本接口自己违反了 3/5** —— **判据一旦不自洽就等于没有判据**。现行口径下：只放『开户提交动作本身』以及它自己需要的归一化」；
  「**已按此判据移出的两个方法，NEVER 加回来**：①`isDayPassCard` —— 只是
  `CardTypeCodeEnum.isDailyTicket(CardTypeMapping.toIssueCardType(..))` 的一行转发，已内联回两个渠道 service；
  ②`attachEmployeeCardsQuietly` —— 依赖 `UserAccEmployeeCardMapper`、与开户落库无关，已迁到
  `EmployeeCardPersistenceService`」；并重申「**NEVER 因为『两个渠道都要用』就把渠道 if-else 挪进来** —— 那会让本类退化成
  杂物间（ADR-D25 删掉的那个类就是这么来的）」。

### 三、domain 层（取值语义的唯一定义点）

- **`ArchiveDecision.Outcome` 四个结论的语义** —— `<J>/domain/ArchiveDecision.java:25-35`：
  「判定结论。**除 `ARCHIVE` 外一律不归档**」；`ARCHIVE` = 全部开户记录已注销、且已无支付通道；
  `NO_REG_INFO` = 该用户已无开户记录（前一次归档已完成，或从未开户）⇒ **幂等跳过**；
  `CHANNEL_REMAINING` = 仍有支付通道未解绑 ⇒ 不归档，等最后一个通道解绑时再来；
  `NOT_ALL_CANCELED` = 存在未注销的开户记录（未走 IF8A-42，**或注销后又重新开户**）⇒ **NEVER 归档**。
  `Result` 的 `ghostIds`（`:37-41`）「`DEL_YN` 既非有效也非已注销的行主键；**仅在 `NOT_ALL_CANCELED` 时可能非空**，
  其余情形恒为空集」，`hasGhostRows()`（`:48`）「命中幽灵态：该用户的归档将**永久无法完成，且没有自愈路径**（ADR-D41）」。
  **排查「某用户归档没做」MUST 先看落的是哪个 Outcome**，四者的处置完全不同。
- **`EmployeeCardStatus` 是 `CARD_STATUS` 语义的唯一定义点** —— `<J>/domain/EmployeeCardStatus.java:3-16`：
  「为什么要有这个枚举：这四个数字此前以裸字面量散在 **3 个文件 6 处**（`EmployeeCardServiceImpl` 的激活 / 禁用白名单与
  合法性校验、`EmployeeCardPersistenceServiceImpl` 的两个私有常量、`UserAccEmployeeCardMapper.xml` 的 `CARD_STATUS = 1`），
  改动取值只能靠全局 grep。**ACC 是这列的权威来源**，一旦甲方调整编码，漏改任何一处都表现为『状态判断静默走错分支』，
  编译与单测都发现不了」；「**数字取值由 ACC 定义，NEVER 自行调整**；也 **NEVER 依赖 `ordinal()`**（声明顺序与编码无关），
  一律用 `code()`」；「**SQL 侧的字面量无法由本枚举收口**（mapper XML 里的 `CARD_STATUS = 1` 仍是硬编码），
  改动取值时 MUST 连 `UserAccEmployeeCardMapper.xml` 一起改」。取值语义（`:20-26`）：`1 NORMAL` 正常（已激活可用），
  且是「`selectActiveByPhone` / `updateThirdUserId` 的**活跃口径**」；`2 DISABLED` 禁用（ACC 侧停用，**可再激活**）；
  `3 NOT_ENABLED` 未激活（**发卡后的起始态**）；`4 CANCELED` 注销（**终态**）。
  `is(Integer)`（`:40-44`）「`null` 恒为 `false`（IF3A 查询链路的 ACC 报文**可能不带 `cardStatus`**）」。
- **`EmployeeCardEvent` 枚举名与落库字符串逐字绑定** —— `<J>/domain/EmployeeCardEvent.java:3-14`：
  「这四个字符串此前以裸字面量散在 **2 个类 6 处**……且 `recordEvent` 的形参是 `String` —— **写错一个字母不报错、
  只是日志表里多出一个没人查得到的事件类型**。换成枚举后**拼错即编译失败**」；「枚举名与落库字符串**刻意逐字一致**，
  落库取 `name()`。**NEVER 改名**：这列已有历史数据，改名等于把历史行与新行割成两类」。四取值（`:18-24`）：
  `OPEN` 本地首次为该员工码建行 / `STATUS` 激活、禁用**以及 ACC 下发的非注销状态通知** / `CHANGE` 换手机号等
  非状态字段变化 / `CANCEL` `CARD_STATUS` 落到终态 4。
- **两个 domain 规则类为什么抽出来（可断言性，不是去重）** —— `ChannelBindingRule`
  （`<J>/domain/ChannelBindingRule.java:5-24`）：「**抽出的动机不是去重**（ADR-D36 已经把三份重复收口过一次），
  而是让这组不变量能**脱离 Spring 上下文与 mapper 被直接断言**：原先要验证『钱包渠道必须带 thirdPayId』就得**端到端打一次 IF8A-23**」；
  `PhoneChangeRule`（`<J>/domain/PhoneChangeRule.java:6-13`）：「抽出的动机是这组前置条件原先夹在『查库 → 判断 → 改三张表 →
  落日志表』的中间，**要断言它就得连 mapper 一起 mock**」，且「本类**只回答『该不该改』，绝不回答『怎么改』**：真正的写入
  （`USER_ITP_REG_INFO.MSISDN`、`USER_ACC_EMPLOYEE_CARD.PHONE`、`USER_PHONE_CHANGE_LOG` **三张表同事务**）仍在服务层」。
- **`isWallet` 内部 trim 是行为等价改写** —— `ChannelBindingRule.isWallet`（`<J>/domain/ChannelBindingRule.java:44-49`）：
  「**入参可为 null**（返回 false），内部自行 trim。原调用点有两种写法：已 trim 过的变量直接 `equals`，未 trim 的先 `.trim()`。
  这里统一成『内部 trim』，对已 trim 的串是空操作，因此**行为完全一致**。」空报文文案是四个入口共用的单一常量（`:38`）。
- **`PhoneChangeRule` 的入口校验刻意与 `decide` 分开** —— `<J>/domain/PhoneChangeRule.java:46-51`：
  「入口级必填校验：`thirdUserId` 与新号都 MUST 非空白。这一步**刻意与 `decide` 分开**：它在**事务之外、查库之前**就要拦掉，
  合并进 `decide` 会让调用方**为了校验两个字符串先去查一次库**。」三个 `Precondition` 取值（`:33-39`）：
  `NO_ACTIVE_USER` 没有有效开户记录、换号失败 / `UNCHANGED` 新旧号相同，不改库也不投递、**对 APP 仍返成功** /
  `PROCEED` 可以改库并**在提交后**向支付域投递。

### 四、开户 / registration 与卡池协作者（阶段一未落笔的行段）

- **员工码挂接 MUST 在 confirmReservation 之后** —— `AccountRegistrationServiceImpl` 字段注释
  （`<J>/service/impl/AccountRegistrationServiceImpl.java:77-79`）与支付宝渠道同款
  （`<J>/service/impl/AlipayTripRegistrationServiceImpl.java:51-54`）：「开户成功后按手机号挂接员工码
  （ADR-D33 从 `RegistrationCommitService` 迁来）。**MUST 在 confirmReservation 之后调用**，
  它自己吞异常、**NEVER 影响开户结果**。」
- **两个渠道类的字段职责划分** —— `AccountRegistrationServiceImpl:68-74`：`cardPoolAllocationService` 是
  「开户发号（卡池预占 / 确认 / 释放、HCE 取卡）的**出网协作者**」；`userItpRegInfoMapper` **只用于开户前的重复开户查重**，
  「两行落库在 `RegistrationCommitService` 里」；`registrationCommitService` 是「**渠道无关**的开户收口：注册乘车状态、
  两行落库、字段归一」。**排查「开户往哪张表写了什么」MUST 按这三条分工找，NEVER 在渠道类里找 INSERT。**
- **支付宝出行开卡的五步编排顺序（注释里的步骤号）** —— `AlipayTripRegistrationServiceImpl:80/92/106/116`：
  ①参数校验 → ②检查用户是否已在支付宝渠道开户 → ③「从卡池预占卡号（**事务外 RPC**）。
  **票种口径与 `buildRegInfo` 写入的 `CARD_TYPE` 保持一致**」→ ④「构建注册信息并**先注册乘车状态（远端），再落本地**」。
  接口侧同一顺序在 `AlipayTripRegistrationService:22-30`：「编排顺序与 IF8A-01 一致……实现**不带事务**（体内全是 RPC）」。
- **开卡时不写签约相关字段** —— `AlipayTripRegistrationServiceImpl:257`：「开户阶段未提供，留空，**后续签约时更新**。」
  这与 `UserPayChannel.payAccountId`「当前只有 IF8A-77 会回写」是同一条链路上的两处空缺，**NEVER 在开卡分支硬填**。
- **卡池预占的四个入参语义** —— `CardPoolAllocationService.reserveFromPool`（`<J>/service/CardPoolAllocationService.java:15-22`）：
  `cardType` 票种码（**044X**）、`businessType` 与 `LOGIC_CARD_POOL_CARD.BUSINESS_TYPE` 对应、
  **`businessId` 决定幂等边界**、`ownerId` 卡号归属方取 `thirdUserId`；「预占成功返回卡号与预占标识；否则返回 `null`」。
  `isHceCardType`（`:63-68`）「`true` 表示 **HCE卡（03）或新版HCE卡（04）**」；`CardAllocation`（`:80-82`）
  「开户时确定的逻辑卡号及 HCE 卡数据。**非 HCE 卡没有 HCE 卡数据**」。
- **`0443` 与新版 HCE 票种的对应关系** —— `CardPoolAllocationServiceImpl:45`：「APP 上送的新版 HCE 票种码（新 NFC 卡），
  **发行侧对应 `0443`**。」（阶段一记了 `03` 同值异义与「NEVER 与支付渠道码合并」，本条补的是 **`04` → `0443` 这个映射事实**。）
- **`iptUserId` 的编码构成** —— `CardPoolAllocationServiceImpl.requestHceCardData`（`:160-166`）：
  「`ticketCard` 取自开户请求；`iptUserId` 由 **`000000` 和 `thirdUserId` 的四字节十六进制编码**组成。
  安全服务成功响应中的 **`logicNum` 即开户使用的 `cardId`**。」
- **release 失败只记日志的理由（与 ADR-D52 不冲突）** —— `CardPoolAllocationServiceImpl.releaseReservation`（`:127-129`）：
  「释放预占。**释放失败只记日志，不向上抛**：卡池的预占超时回收会兜底把卡号收回。」
  注意这条约束的是**释放动作自身失败怎么办**，而「什么时候都不该调 release」是 ADR-D52 那条，两者 NEVER 混谈。
- **confirm 失败的 ERROR 日志字段清单** —— `CardPoolAllocationServiceImpl:121-124` 的日志原文
  「`{}卡池确认失败，开户已落库但卡号仍处预占态，MUST 人工核对, reservationId=…, businessId=…, cardNo=…, outcome=…, msg=…`」——
  排查 ADR-D52 那类不一致时，**这条日志的五个字段就是全部线索**（`scene` 区分 APP / 支付宝渠道）。
- **开户流水行两条链路共用** —— `RegistrationCommitServiceImpl:90`：「开户流水行。**两条开户链路共用**，`OPER_TYPE=0` 表示开户。」
  连同阶段一记过的 1（解约）/ 2（销户）/ 3（归档），**`USER_ITP_REG_LOG.OPER_TYPE` 的四个取值至此齐全**。
- **`TransactionTemplate` 字段本身带 NEVER 告示** —— `RegistrationCommitServiceImpl:40-43`：「`persistRegistration` 的两行写用它
  开短事务。**NEVER** 改成给方法加 `@Transactional` —— 那会把调用方的 RPC 一起圈进事务」，类注释（`:23-29`）同款
  「**NEVER 给本类或本类方法加 `@Transactional`**」（AGENTS.md §5.2，2026-08-26 生产事故）。

### 五、电子员工卡（落库服务契约面与返回码）

- **落库接口的事务纪律与两个例外** —— `EmployeeCardPersistenceService` 类注释
  （`<J>/service/EmployeeCardPersistenceService.java:7-17`）：「本接口**多数**实现方法带 `@Transactional`，用途是把
  『短事务的本地写』从『调 ACC / APP 的 HTTP 请求』里分离出来：调用方 MUST 在事务外完成远端调用，远端成功后再调本接口提交本地写」；
  「**两个例外方法各自在 Javadoc 里写明了『为什么不带事务』，NEVER 顺手给它们补上**：`recordEvent`（单条 INSERT，
  **要按调用方语义决定跟不跟着回滚**）与 `attachEmployeeCardsQuietly`（逐卡独立 CAS + 吞异常，**包事务会让一张卡失败拖垮整批**）。」
- **两类「影响 0 行」的处置完全相反，NEVER 统一** —— `saveFromStatusNotify`（`:19-27`）：「**UPDATE 影响 0 行时抛
  `IllegalStateException`**：本方法先按 cardNo 查到了行，0 行只可能是并发删除。调用方
  `EmployeeCardServiceImpl.processAppBatch` 会捕获、把该卡放进 failList，**ACC 收到 PARTIAL_SUCCESS 后重推即自愈**。
  **NEVER 改成只记日志**——那会连带写出一条『处理成功』的事件日志」；`refreshProfileFromAcc`（`:41-59`）：
  「**UPDATE 影响 0 行只记 WARN、NEVER 抛**：本方法挂在**只读的员工码查询链路**上，抛出会让查询退化成全局异常处理器的
  UUID retCode；资料回填是**尽力而为的旁路**，本次响应用的是内存里已更新的 `target`，下次查询还会再试。
  这与 `saveFromStatusNotify`『0 行即抛』是**两类语义，NEVER 统一**」，且该方法「**不写事件日志**（只是补全资料、
  不是状态变更）」「`source.getCardStatus()` 仍按 ACC 返回值覆盖，这也是搬迁前的行为」。
- **`applyActivationResult` 返回 false 即不一致** —— `:31-39`：「`markOpenTms` 为 true 且 `OPEN_TMS` 为空时回填开通时间」；
  「返回 `false` 表示按卡号未命中任何行（并发注销等），**调用方 MUST 视为不一致**」。落到实现侧
  （`<J>/service/impl/EmployeeCardPersistenceServiceImpl.java:131-134`）：「与上面 `selectByCardNo == null` 是**同一种结局**
  （本地没写成），因此复用 false 这条出口：调用方 `EmployeeCardServiceImpl.activateEmployeeCard` 收到 false 会抛
  `IllegalStateException`、开异常工单并返 **9998『ACC 已受理但本地回写失败』**。**NEVER 改成只记日志后 return true** ——
  ACC 侧状态已变更，本地静默停在旧值就再也没人发现。」
- **`recordEvent` 两种事务语义都是刻意的** —— `:63-84`：「本方法**故意不带 `@Transactional`**：它只有一条 INSERT，
  调用方在事务内调（如 `updateEmployeeInfo`）会按 REQUIRED **加入调用方事务、随其一起回滚**；调用方不在事务内调
  （如 **APP 注册失败留痕**）则自动提交、**NEVER 因为上层业务失败而丢掉这条痕迹**。两种语义都与搬迁前一致。」
- **挂接只能在开户时做的原因与幂等口径** —— `attachEmployeeCardsQuietly`（`:85-108`）：
  「`USER_ACC_EMPLOYEE_CARD` 的行是 **ACC 通知先建的**，那一刻 ITP 侧还不知道这张卡属于哪个 APP 用户，
  所以 `THIRD_USER_ID` 只能在『ITP 用户出现』的时刻反向补，而开户正是这个时刻。**手机号是员工码与 ITP 用户两边唯一的共有键**」；
  「**MUST 在开户事务提交之后调用，本方法 NEVER 抛异常、NEVER 带 `@Transactional`**……把它做成能拖垮开户的强依赖，
  等于**用一个可用性故障换一个数据问题**」；「幂等：`updateThirdUserId` 的 WHERE 含『`THIRD_USER_ID` 为空或已等于目标值』，
  因此重复开户重放**不会把别人的卡抢过来**；影响 0 行 = 该卡已被**别的**用户占用或不在正常态，只记 WARN，
  **NEVER 改成强行覆盖**」。
- **激活 / 禁用的白名单与返回码全集（接口侧那一份）** —— `EmployeeCardService.activateEmployeeCard`
  （`<J>/service/EmployeeCardService.java:19-28`）：「**先调 ACC，ACC 成功后再回写本地状态与事件日志**」；
  「前置状态是**白名单**：`actionFlag=1`（激活）只接受 `CARD_STATUS=3` 未激活，`actionFlag=0`（禁用）只接受
  `CARD_STATUS=1` 正常，其余一律拒绝」；返回码 `0000` 成功 / `8001` 参数非法 / `8004` 员工码不存在 /
  `2002` 当前状态不允许 / **`9998` ACC 已受理但本地回写失败（已开异常工单，需人工介入）** / `9999` 调 ACC 失败。
- **`9998` 与 `FAIL` 的语义差别写在枚举上** —— `AccountErrorCodeEnum`（`<J>/constant/AccountErrorCodeEnum.java:16-20`）：
  「ACC 已受理、但本地状态回写失败。**与 `FAIL` 语义不同、NEVER 合并**：它意味着**远端已生效而本地落后**，
  **调用方不该重试**（重试会再打 ACC 一次），处置方式是**等异常工单人工收口**」；`PARTIAL_SUCCESS`（`:13`）
  「批量处理里『部分成功、部分失败』。**当前只有员工码批量状态通知用**」；`CARD_STATUS_NOT_ALLOWED`（`:43`）
  「员工码前置状态不满足……**取值 2002 由 ACC 侧规格给定**」——**这三个码不是本项目自定的，改值即改对外契约**。
- **出网实现类不许有事务，调用方也不许把它包进事务** —— `EmployeeCardOutboundServiceImpl` 类注释（`:32-43`）：
  「**NEVER 在本类里加事务** —— 这里只有 HTTP，没有一条 SQL；反过来，**调用方也 NEVER 把本类的方法放进 `@Transactional` 里**
  （AGENTS.md §5.2：事务包住 RPC 已在 2026-08-26 造成生产事故）。」搬迁批次是 2.0.58，搬来的是 `postFormData` /
  `registerEmployeeCardsToApp` / `parseAppFailList` / `queryEmployeeCardFromAcc` 与它们独占的配置项 + `RestTemplate`。

### 六、mapper 与 DDL / 迁移脚本（本轮最大的一块空白）

- **`UK_UIRI_ACTIVE_USER_CARDTYPE` 迁移脚本里的四条硬知识** —— `<S>/account-server-active-user-cardtype-index-migration.sql:1-26`
  （整文件 26 行注释、阶段一一条未收）：①它是「**ADR-D49 ① 的开户防重唯一索引**。此前只写在 `account-server-schema.sql` 里、
  没有独立迁移脚本，而 `*-schema.sql` 只服务『新建库』，**对已存在的库等于没写**（AGENTS.md §8『一天撞三次』的第 ③ 条）。
  没有这个索引时……两条并发 IF8A-01 或 APP 超时重推会双双通过查重、双双落库，**用户拿到两张有效卡**，而
  `handleDuplicateRegistration` 那段 `DuplicateKeyException` 兜底**永远走不到** —— 编译、单测、`xmllint` 全都发现不了。
  **2026-09-14 已在 AFCITPDB 执行并回查（UNIQUE / FUNCTION-BASED NORMAL / VALID / PARTITIONED=NO）**」；
  ②「**NEVER 简化成朴素的 `UNIQUE (THIRD_USER_ID, CARD_TYPE)`**。两个 `CASE` 刻意把 `COMPANION_FLAG` 为 Y（同行票）/
  C（第三方代开）的行排除在唯一性之外，那类票按业务定义『每次都给新卡』；改朴素两列会让这些合法请求的第二张卡**直接 INSERT 失败**。
  查重侧 `UserItpRegInfoMapper.xml` 的 `selectActiveByThirdUserIdAndCardType` **MUST 与本谓词逐字对齐**」；
  ③「**NEVER 顺手加 `LOCAL`**。`USER_ITP_REG_INFO` 按 `THIRD_USER_ID` 派生值 **LIST 分区**，而键是两个 `CASE` 表达式、
  不是分区键，**Oracle 只允许 `GLOBAL`，加 `LOCAL` 报 `ORA-14039`**」；④「在新库上执行前 MUST 先按索引的确切谓词做
  **`ORA-01452` 前置统计**（脚本里给了现成 SQL：`COUNT(*)` vs `COUNT(DISTINCT THIRD_USER_ID || '#' || CARD_TYPE)`，
  谓词 `DEL_YN = 1 AND NVL(COMPANION_FLAG,'N') NOT IN ('Y','C')`），有重复只能与业务定归属、**NEVER 删行**（卡号可能已发给用户）」；
  另附「经 `mcp_database_qd` 执行时 **MUST 包一层 PL/SQL** —— 它的 SQL 解析器**拒绝带 `CASE` 的 `CREATE INDEX`**
  （`McpSqlValidationException`），**内层单引号写两个**」，脚本里有完整可复制的一行 `BEGIN EXECUTE IMMEDIATE '...'; END;`。
- **`PAY_ACCOUNT_ID` 迁移脚本记的是「为什么会漏」与列宽判据** —— `<S>/account-server-pay-account-id-migration.sql:1-5`：
  「ADR-D30 的支付账号标识列。此前**只改了 `account-server-schema.sql`、没有单独的迁移脚本**，于是在 AFCITPDB 上一直没执行，
  而 `UserPayChannelMapper.xml` 的 `selectByThirdUserIdAndCardTypeAndCardId` 与 `updatePayAccountIdByReqContractNo`
  **已经在用它**，运营页『支付账号』列与 IF8A-77 回写**一跑就 `ORA-00904`**。2026-09-14 已在 AFCITPDB 执行并回查」；
  「宽度取 **64 CHAR** 与来源列 `APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID` 一致，**NEVER 按 `DATA_LENGTH` 的 128 写**」
  （对应 `docs/domain/decisions.md` 撤回记录里「用 `DATA_LENGTH` 判列长」那条）。
- **`SIGN_SYNC_*` 迁移脚本记的是改造前的违规形态** —— `<S>/account-server-phone-sync-migration.sql:1-6`：
  「背景：`AccountApplicationServiceImpl.updatePhone` **目前在 `@Transactional` 内调 `paySignClient.updatePaySignDisplayAccount`
  （事务内出网，违反 AGENTS.md 5.2）**。改造后该 RPC 移到事务外，成败落到本组列，由 `@Scheduled` 扫表补偿重推，
  达重试上限转异常工单。**列名与 `APP_TERMINATION_REQUEST` 的 `NOTIFY_*` 对称**。详见 `docs/domain/decisions.md` ADR-D8。」
  **注意脚本里「由 `@Scheduled` 扫表」已过期**（现由 web-admin `sys_job` 290 触发 `/phoneSignSyncCompensate`；2026-09-21 由 108 改号），见 §矛盾第 4 条。
- **另两个迁移脚本各只有一行、但都是执行前提** —— `<S>/account-server-hce-data-migration.sql:1`：
  「HCE 卡数据存储。**已执行 `account-server-card-type-migration.sql` 的环境也必须执行本脚本**」；
  `<S>/account-server-companion-flag-migration.sql:1`：「为既有 `USER_ITP_REG_INFO` 表增加同行票/第三方票标识。」
- **忽略 `DEL_YN` 的两条查询各自写明了理由** —— `<X>/UserItpRegInfoMapper.xml:107-110`：「IF8A-42 销户后 IF8A-75 仍要逐渠道解绑，
  因此需要一条**不过滤 `DEL_YN`** 的查询。**常规链路 MUST 用上面的 `selectActiveByThirdUserIdAndCardIdAndCardType`**」；
  `:193-195`：「注销未归档期间**同一 `CARD_ID` 可能与重新开户的有效记录并存（卡号回收）**，故返回列表。」
  后者是「为什么这条查询返回 List 而不是单条」的唯一成文出处，**NEVER 因为『一个卡号只该有一行』把它改回单条**。
- **归档删除语句的完整告示（含快照来源）** —— `<X>/UserItpRegInfoMapper.xml:340-347`：「销户归档：解绑最后一个签约渠道时，
  把已注销的开户记录**从原表物理删除**（**快照已由 `requestRemovePayChannel` 写入 `USER_ITP_REG_LOG`，`OPER_TYPE=3`**）。
  WHERE 固定带 `Del_Yn_Canceled_Filter`（0=已注销），**并发期间新开户产生的有效记录不会被误删**。」
  阶段一记了这段的三条 NEVER，本条补的是**「删掉的行去哪了」这个事实**：唯一留痕在 `USER_ITP_REG_LOG` 的 `OPER_TYPE=3` 行。

- **`ORA-17004` 才是「占位符不带 jdbcType」的真实报错，且它只在 `OLD_MSISDN` 为 NULL 时暴露** ——
  `<X>/UserPhoneChangeLogMapper.xml:14-29`（阶段一只记到「MUST 带 jdbcType」为止，本条补后半段）：
  「MyBatis 默认取 `OTHER`，**Oracle 侧对 `setNull(OTHER)` 直接抛 `ORA-17004` 无效的列类型**。
  **`OLD_MSISDN` 是真实可空路径**：`PhoneChangeRule` 明确要求『库里 `MSISDN` 为 NULL 的历史行走 `PROCEED` 把号补上』，
  此时 `oldMsisdn` 就是 null。**缺 `jdbcType` 时的表现极难定位**：`USER_ITP_REG_INFO` 与 `USER_ACC_EMPLOYEE_CARD`
  两条 UPDATE **已成功**，本 INSERT 抛错导致 `transactionTemplate` 整体回滚，`PhoneChangeServiceImpl` 只 catch 后返 false，
  **APP 只看到普通失败**。」另记主键用途（`:5-7`）：「调用方 insert 后可**直接用 `getId()` 定位本行去落 `SIGN_SYNC_*` 投递状态**。」
- **补偿扫表白名单里「NULL 历史行不捞」是有意的** —— `<X>/UserPhoneChangeLogMapper.xml:118-127`：
  「account-server 侧 **NEVER 加 `@Scheduled`** —— 调度统一由 web-admin 前台可配的 `sys_job` 承担（用户 2026-09-11 要求）」；
  「白名单是 `SIGN_SYNC_STATUS IN ('PENDING','FAILED')`：**NULL 的历史行（改造前的记录）不会被捞出，这是有意的** ——
  那些行没有待投递的事实，**回填它们等于对早于改造的记录发起重推**；**`SUCCESS` 是终态，NEVER 重推**」；
  「`RETRY_COUNT` 达上限的行留在 `FAILED` 不再重推……这类行需人工介入（后续接异常工单表）」。
- **工单表两条语句的索引与幂等出处** —— `<X>/AccountExceptionTicketMapper.xml:5-10`：「开单。幂等由
  `UK_ACCT_EXC_TICKET_TYPE_KEY (TICKET_TYPE, BIZ_KEY)` 保证，**重复开单抛 `DuplicateKeyException`，由 service 捕获后当
  『已有工单』继续**」；`:51-57`：「运营查询。三个筛选项都可为空，**只传 `TICKET_STATUS` 时命中
  `IDX_ACCT_EXC_TICKET_STATUS (TICKET_STATUS, CREATE_TMS)`**」——**这是本表两个索引名的唯一成文出处**。
- **员工码查询刻意分两套列清单（CLOB）** —— `<X>/UserAccEmployeeCardMapper.xml:24`：「明确字段清单，避免全字段查询；
  **只有员工码详情查询需要加载 `PHOTO_URL`**。」（阶段一记了「NEVER 在不需要照片的查询里用 `BaseColumnList`」这条 NEVER，
  本条补的是**两套清单存在的正向理由**。）
- **`PAY_ACCOUNT_ID` 回写按签约流水号定位的理由** —— `<X>/UserPayChannelMapper.xml:75-78`：
  「按 `REQ_CONTRACT_NO` 定位：该值就是签约流水号，**与 `PAY_ACCOUNT_ID` 一一对应，比按
  `(THIRD_USER_ID, CARD_TYPE, CHANNEL)` 定位更贴近这个值的来源**。影响 0 行是正常情形……调用方只记日志、不失败」；
  另两条同文件（`:39-40`、`:49-50`）分别是「`APP_PAY_SIGN_INFO` 的 `CARD_ID`/`CARD_TYPE` 全库为 NULL，**真实来源是本表**」
  与「判断『最后一个签约渠道是否已解绑』：**`thirdUserId` 全量口径，不带 `cardType` / `cardId`**，
  由 `requestRemovePayChannel` **在 delete 之后**调用」。
- **五个文件一条注释都没有（现状，不是遗漏）** —— `AccountServer.java`、`UserItpRegLogMapper.java`、
  `UserAccEmployeeCardLogMapper.java`、`<X>/UserItpRegLogMapper.xml`、`<X>/UserAccEmployeeCardLogMapper.xml`，
  外加 **`<S>/account-server-schema.sql`（8 张表 + 2 个序列的 DDL 全文零注释）** 与
  `<S>/account-server-card-type-migration.sql`。**列语义只能去 `entity/*.java` 的字段 Javadoc 里读**（见下条），
  **NEVER 期待在 schema 文件里找到列注释**。
- **实体字段 Javadoc 是若干列语义的唯一出处** —— 多数是「主键 / 手机号」这类样板（本轮按规则跳过、只计行数），
  但其中三条带实义：`UserItpRegInfo.hceData`（`<J>/entity/UserItpRegInfo.java:105-107`）
  「HCE 卡数据。**开户时由安全服务生成，闸机交易后由 IF1A-01 `reserve1` 更新**」；
  `UserPhoneChangeLog.userType`（`<J>/entity/UserPhoneChangeLog.java:19-21`）
  「用户类型：**`ITP`-地铁APP用户，`ALIPAY`-支付宝用户**」——**这是该列取值集合的唯一出处**（两域共用一张表的证据）；
  `UserPhoneChangeLog.signSyncRetryCount`（`:66-68`）「投递重试次数，**达配置上限后不再扫描、转人工**」。
  `UserItpRegLog.operType`（`<J>/entity/UserItpRegLog.java:39-41`）只写「操作类型」、**没有取值表**，
  取值仍以阶段一登记的 0/1/2/3 四处常量为准。

### 七、config / properties（两个 `@ConfigurationProperties` 与配置文件注释）

- **「地址为空即视为未配置」是三个员工码出网口的统一约定** —— `EmployeeCardOutboundProperties`
  （`<J>/model/EmployeeCardOutboundProperties.java:24-30`）：`app-register-url`「**为空即视为『未配置』，
  出网方法会直接返回失败而不发请求**」；`acc-query-url`「为空即视为『未配置』」；
  `acc-activate-url`「为空即视为『未配置』，**激活入口会返 9999**」。
  **实测现状：`account-server/src/main/resources/application.properties:55` 的 `employee-card.acc-query-url=` 就是空值**
  ⇒ 员工码 ACC 资料查询链路当前处于「未配置」降级态，**排查『查员工码查不到姓名』MUST 先看这个键**（线上真实值仍 MUST 查 Deployment env）。
  其余出网报文字段的默认值语义也在同一文件：`provider-id` / `charset` / `format` / `device-id` /
  「`sign-type`；**`00` 表示免签**」/ 连接与读超时毫秒数（`:33-51`）。
- **`itp.*` 四个键与「两处真值只改一处等于没改」** —— `ItpSignProperties`（`<J>/model/ItpSignProperties.java:6-25`）：
  「ADR-D37 由 4 个散落在 `AccountRequestVerifier` 上的 `@Value` 收拢成一个对象。**配置键与默认值一个字都没改**
  （`itp.providerId` / `itp.charset` / `itp.format` / `itp.signKey`），因此 K8s Deployment 的 env 与 `application.properties`
  都不用动」；「**⚠️ `signKey` 仍带明文默认值，这是本轮刻意保留的既有缺陷**（用户 2026-09-11 裁定『先收成对象、真值暂不动』），
  违反 AGENTS.md §5.2。**上线前 MUST 改成 `${ITP_SIGN_KEY:}` 并轮换该密钥**；改的时候连带把
  `application.properties` 里的同名真值一起清掉——**两处都写了真值，只改一处等于没改**」；
  「**NEVER 把这个前缀下的键搬到别的类里用 `@Value` 再读一遍**：那会让『同一个 signKey 有两个读取点』，轮换密钥时必漏一处」。
  两处真值的位置是 `<J>/model/ItpSignProperties.java:44`（字段默认值）与 `application.properties:53` 附近的 `itp.signKey=`，
  **本文件按 AGENTS.md §5.2 只记键名与位置、不回显值**。字段语义：`providerId`「入向报文的 `providerId` **必须与之相等**」、
  `charset`「**比较时忽略大小写**」、`format`「同上」、`signKey`「摘要式验签的**盐值，拼在签名源串末尾的 `&key=` 之后**」。
- **服务间地址的两条 NEVER 写在 properties 注释里** —— `application.properties:23-24`：
  「服务间调用地址：默认值一律用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11）。
  **NEVER 写 127.0.0.1**（在 K8s 里等于打到自己），也 **NEVER 删键**（键缺失时 rpc 退化成默认服务名 `*-service`、DNS 解析不到）。」
  这与 AGENTS.md §8「`service.*.url` 三类问题」逐条对应，是本模块**唯一**成文的配置纪律。
- **员工码地址那段注释与实际值不一致，且 `testngbackV2` 残留在这一行** —— `application.properties:53`
  的注释是「员工码外部接口。**确认对端地址后填写 URL**」，而紧随的 `:54 employee-card.app-register-url` 里已经填着
  一个 `dtcustomer.bestonepay.com/testngbackV2/...` 测试地址（AGENTS.md §8 的 7 处残留之一，且**是裸硬编码、无 `${ENV:}` 包装**）；
  `:56 employee-card.acc-activate-url` 则带 `${EMPLOYEE_CARD_ACC_ACTIVATE_URL:...}` 包装、默认值指向 `172.20.211.11:32605`。
  **同一段配置里三种形态并存（裸硬编码 / 带 env 包装 / 空值），改任何一个前 MUST 先看它属于哪种。**
- **分片推送的批量上限** —— `application.properties:62`：「**单次推送 APP 的最大卡片数，超出后分片调用**」
  （`employee-card.app-batch-size=200`）。
- **`:4-6` 三行是乱码注释，且第 5 行藏着目标库地址** —— `application.properties:4-6` 实际内容是
  `#??????  ???IP` / `#other.sql.host=172.20.222.3:1521` / `#??????  ???IP`：
  两行中文注释已损坏成问号（**该文件历史上被非 UTF-8 编码保存过**，`grep` 中文关键字搜不到它们），
  中间那行是**被注释掉的 `other.sql.host` 真值，正是 AFCITPDB 的地址**（现行配置是 `${DB_HOST:}`）。
  这既是「注释即失效代码」的样例，也是**仓库内少数直接写出目标库地址的位置**；`docs/ops/生产环境清单.md` 已记该地址，
  本条只记它在这里出现过一次，**NEVER 把这三行当成有效配置说明，也 NEVER 直接取消注释**。

### 八、入向验签（`AccountRequestVerifier`）

- **本类零调用点、24 个端点全裸露** —— `<J>/service/AccountRequestVerifier.java:17-22`：
  「**⚠️ 本类当前没有任何调用点**（ADR-D35 / ADR-D37 复核：全模块 grep 只命中 `RequestApplicationController` 的构造器参数），
  因此**账户域 24 个端点全部裸暴露**。补验签见 ADR-D35，**NEVER 因为『没人用』就删掉本类**。」
- **它用的是 fastjson 1，且刻意没换** —— 同文件 `:24-28`：「**本类用的是 fastjson 1**（`com.alibaba.fastjson`），
  而 AGENTS.md §5.1 要求统一 Fastjson2。ADR-D37 **刻意没有替换**：`buildSignSource` 用 `SerializerFeature.MapSortField`
  决定 `bizData` 的序列化字节，**换库会改变签名源串 ⇒ 已发出的 sign 全部失配**。这属 §5.2『安全红线』，
  **要换 MUST 与上游同批改并端到端比对签名**。」（配合 AGENTS.md §5.1 那条「公共报文骨架只承载报文、不承载签名语义、
  NEVER 因共用一个类就统一签名逻辑」看：本类是账户域这条链路**唯一**的验签实现，`ItpCommonRequest` 只提供字段。）
- **摘要式验签的三个配置约束** —— 见 §七 `ItpSignProperties` 那条：`providerId` 必须**相等**、`charset` 与 `format`
  **忽略大小写**、`signKey` 拼在源串末尾 `&key=` 之后；时间戳格式由 `TIMESTAMP_PATTERN = \d{14}` 钉住（`:33`，代码非注释）。
  **`signType=00` 免签这条在本类注释里没有出处**（AGENTS.md §2.2.1 的说法来自实现代码），本轮不代笔补写。

### 九、注释里写死的版本号与镜像相关事实

注释里出现的版本号**只标记「哪一版引入了这个形状」，NEVER 用来推断线上跑的是哪版**（AGENTS.md §7：判线上 MUST 查 Deployment 的
image tag；2026-09-16 就出过「Deployment 跑 2.0.69、仓库 pom 已 2.0.70，`ItpUserPageController` 少两个端点、404 伪装成
UUID retCode」那一例，ADR-D93 / D97 续）。本轮抽取时刻仓库 `account-server/pom.xml` 的 `<version>` 是 **2.0.73**。

| 版本 | 注释出处 | 该版引入的事实 |
|---|---|---|
| 2.0.47 | `<X>/AccountExceptionTicketMapper.xml:17` | 该 mapper 的 XML 注释里写了行注释符号 ⇒ MyBatis 解析失败 ⇒ **首次部署即启动失败**（Pod `2/2 Running` 但端口不监听）。阶段一已记该陷阱，本条只补「版本号出现在注释里」这一事实 |
| 2.0.58 | `EmployeeCardOutboundServiceImpl:35`、`EmployeeCardServiceImpl:41` | 员工码**出网四方法 + `RestTemplate` + 独占配置**从 `EmployeeCardServiceImpl` 搬到 `EmployeeCardOutboundService` |
| 2.0.59 | `AccountArchiveServiceImpl:23` | 销户归档两入口从 `AccountApplicationServiceImpl` **逐字搬迁**（含 `OPER_TYPE=3` 常量与三个只被归档用到的 mapper 方法） |
| 2.0.63 | `UserPayChannel.payAccountId:17`、`UserPayChannelMapper.java:41`、`ItpUserQueryService:17`、`ItpUserQueryServiceImpl:29`、`PayChannelServiceImpl:307`、`EmployeeCardServiceImpl:45` | **`PAY_ACCOUNT_ID` 列新增**（IF8A-77 是唯一写入点）、运营只读路径**去掉 N+1 跨域 RPC**、员工码事件日志落库收口到 `recordEvent` |
| —— | `<X>/UserPayChannelMapper.xml:18` | 「`PAY_ACCOUNT_ID` 是 ADR-D30 新增列，其 **DDL 与 account-server 镜像的上线顺序无法保证**」⇒ 既有语句刻意不查该列。**这是「代码先上、DDL 后上」这类事故的唯一成文防线**（阶段一已记该条，本表只把它与版本序列放在一起） |

### 十、本轮要求覆盖的重点条目 —— 落点索引（避免重复抄录）

| 重点 | 已落在哪 |
|---|---|
| ADR-D52 卡池预占共享、失败分支 NEVER release、confirm 失败 MUST 开 `CARD_POOL_CONFIRM_REJECTED` 工单 | 阶段一 §一「决策理由」四条 + §四；本轮补 §四的 release 自身失败处置、confirm 失败 ERROR 日志五字段、支付宝渠道工单同口径 |
| ADR-D13 `syncDisplayAccountToPayDomain` 丢 boolean ⇒ `SIGN_SYNC_STATUS='SUCCESS'` 造假 ⇒ 现改 `RpcOutcome` 穷尽 switch | 阶段一 §二「决策理由」的「RPC 三分支处置刻意不同（ADR-D45）」条 |
| Druid WallFilter 第二条：`where 1 = 1` + 全可选 `<if>` 被判恒真条件（`countGroupByCardType`，2.0.73 修） | 阶段一 §一「陷阱」两条（`ORA-01843` + 恒真条件）；本轮在 §九记 2.0.73 是当前 pom 号 |
| mapper XML 注释含连续减号 ⇒ 启动即挂（`AccountExceptionTicketMapper.xml`，2026-09-11 / 2.0.47） | 阶段一 §一「陷阱」；本轮 §九补版本序列、§六补该文件另两段注释（幂等索引名与 `ROWNUM` 子查询） |
| 迁移脚本类缺陷（`PAY_ACCOUNT_ID` / `UK_UIRI_ACTIVE_USER_CARDTYPE` 只写进 `*-schema.sql`；函数索引带 `CASE`、承载多卡语义、NEVER 改朴素两列） | **本轮 §六前两条**（阶段一只在服务层注释里提过索引，脚本内 31 行注释一条未收） |
| 入向验签 `AccountRequestVerifier`（摘要式、零调用点、fastjson 1）与「NEVER 因共用报文骨架就统一签名逻辑」 | 阶段一「墓碑」42/43/53 条；**本轮 §八**（类注释两段 + `ItpSignProperties` 键语义）。`signType=00` 免签在本模块注释里**无出处** |
| IF8A-23 / IF8A-77 回写 `PAY_ACCOUNT_ID`（ADR-D30 / D32 / D55）、员工卡链路、`USER_ITP_REG_INFO` 与 `APP_USER_PAY_CHANNEL` 列语义与状态取值 | 阶段一 §二 + §三；本轮补 §一（`syncPayAccountId` 端点鉴权缺口）、§五（落库契约与 9998）、§六（回写按签约流水号定位的理由）、§六末（实体字段三条实义 Javadoc） |
| 版本号 / 镜像相关注释里的事实 | **本轮 §九** |

### 矛盾与待裁决

1. **`RegistrationCommitService.java:36-37` 与 ADR-D52 直接冲突（阶段一提出，本轮复核确认，且需要裁决）。**
   接口 Javadoc 原文：「按 AGENTS.md §5.2『先调远端、后改本地』，调用方 MUST 在本方法成功后才落库；**失败时 MUST 释放卡池预占**。」
   代码证据（本轮逐处核对，三方一致地反对这句话）：
   ①`AccountRegistrationServiceImpl.requestApplication:158-160` 与 `:208-210` 的注释是
   「**NEVER 在这里 `releaseReservation`**：预占按 `businessId` 幂等、是并发请求共享的……滞留的预占交给 `sys_job` 240『卡池数据导入』的超时回收」；
   ②同类 `:270-274`「**本方法 NEVER `releaseReservation`**（2026-09-14 / ADR-D52 起，此前会释放）」；
   ③支付宝渠道 `AlipayTripRegistrationServiceImpl:120-122`、`:159` 同款；
   ④`CardPoolAllocationService.releaseReservation:45-56` 的墓碑注释（阶段一墓碑第 34 条）明确「**在失败分支调 `releaseReservation`** 是被禁止的，
   但**因链路不再调用就删掉该方法也是被禁止的**」。
   **结论：`registerRideStatus` 的 Javadoc 是 ADR-D52 之前的旧措辞，属注释与实现不一致，且危险方向是「照 Javadoc 写新代码会重现 ADR-D52 的缺陷」。**
   本轮**按约束只记录、未改代码**。**建议裁决**：把该句改成「失败时**不要**释放预占，滞留预占由 `sys_job` 240 超时回收（ADR-D52）」，
   并同批检查是否还有别处沿用旧措辞。**在裁决落地前，读到这句话 MUST 以 ADR-D52 为准。**
2. **`PhoneChangeService.java:18` / `:27` 的 `{@link AccountApplicationService#updatePhone}` 指向已删除的类。**
   本轮全模块 grep 实测：`AccountApplicationService` / `AccountApplicationServiceImpl` **只剩注释里的字样，源文件已不存在**
   （同一文件 `:6` 自己就写着「该类已于第六轮整体删除」）。两个 `@link` 是断链，**编译不报错、`javadoc` 才会警告**，
   于是「语义见另一个类」实际等于**语义无处可查**。同型断链还有 `AccountRegistrationService:18`
   （「它们分别属 `AccountApplicationService`、`PhoneChangeService`、`PayChannelService`」——三者之一已不存在）。
   **待裁决**：把这两处 `@link` 改成正文描述或指向现行实现类。
3. **`AccountArchiveServiceImpl.java:29-30` 声称「上游注入的是 `AccountApplicationService`」，与两处现行注释冲突。**
   `TaskController:29`「第六轮拆分后**直接注入实现方**，不再经 `AccountApplicationService` 转发」与
   `RequestApplicationController:37`「第六轮拆分后直连实现方，**原 `AccountApplicationService` 已删除**」是现行口径。
   归档那条注释想表达的约束（**NEVER 让 Controller 直接依赖归档实现类**）仍成立，但它给出的理由已失效。
   **待裁决**：改成「上游注入的是 `PayChannelService` / `AccountCancelService`，归档只是它们的收尾步骤」。
4. **`<S>/account-server-phone-sync-migration.sql:4` 写「由 `@Scheduled` 扫表补偿重推」，与「account-server 全模块无 `@Scheduled`」冲突。**
   现行事实：调度在 web-admin 的 `sys_job`（290），入口是 `TaskController.phoneSignSyncCompensate`，
   `<X>/UserPhoneChangeLogMapper.xml:118-120` 写的正是「**account-server 侧 NEVER 加 `@Scheduled`**」。
   脚本注释是改造当时的措辞，**已过期**；**NEVER 据它在本模块里加 `@Scheduled`**。
5. **`AccountRequestVerifier` 的存在与 §5.2「新增状态变更型接口 MUST 有鉴权」处于长期冲突态**：类注释自己承认 24 个端点全裸露，
   而 `PayChannelInternalController:60-65` 又把「无鉴权」标注为**有意的临时降级**。两处都写了「上线前 MUST 补齐」，
   但**没有任何注释记录补齐的责任人或触发条件**。本轮不代笔，列为待裁决。

### 墓碑清单（阶段二新增，阶段一 69 条不重复）

编号续阶段一（70 起）。判据同阶段一：注释的唯一作用是**禁止回退到某个已被推翻的做法或已搬走的位置**。

| # | 位置 | 它想禁止的事 | 可否写成断言测试 |
|---|---|---|---|
| 70 | `<J>/controller/internal/CardDataInternalController.java:33-40` | 给本类加类级 `@RequestMapping` 或任何路径前缀（会让 ticket-server / fep-dev / face-pay / collect-pay 同时 404） | 可：反射断言本类无类级 `@RequestMapping` + 两个方法路径字面量 |
| 71 | `<J>/controller/internal/PayChannelInternalController.java:21-27` | 为了整齐把 `/queryPayChannelByContractNo` 挪进 `/internal/` 前缀 | 可：反射断言两个端点路径字面量 |
| 72 | `<J>/controller/internal/PayChannelInternalController.java:30` | 在对内 controller 里写业务逻辑 | 部分：可结构断言字段只有 service |
| 73 | `<J>/controller/task/TaskController.java:76-79`（阶段一 61 已记入参，本条记「不鉴权的前提」） | 在保留「无鉴权」的同时给端点加任何外部可控参数 | 可：反射断言端点零入参 |
| 74 | `<J>/service/AccountProfileService.java:14-15`、`AccountCancelService.java:12-13`、`AccountRegistrationService.java:17-18`（阶段一 45 已含同批三接口，本条补三份「NEVER 加进来」的对偶关系） | 把销户 / 开户 / 查询 / 支付通道方法互相搬进对方接口 | 可：结构断言四接口方法名集合互斥 |
| 75 | `<J>/service/ItpUserQueryService.java:17-23` | 为「查得更全」把 `paySignClient.querySignInfoBySeq` 加回运营只读路径（ADR-D30 消掉的 N+1） | 可：结构断言实现类字段不含 `PaySignClient`（与阶段一 37 同源，本条是接口侧那份） |
| 76 | `<J>/service/PhoneChangeService.java:11-13` | 把 `SignSyncCompensateResult` 挪出本接口（等于改对外契约；与阶段一 27 同源，本条补「TaskController 已在用」这个理由） | 可：结构断言类型所在位置 |
| 77 | `<J>/service/RegistrationCommitService.java:20-31` | 把 `isDayPassCard` / `attachEmployeeCardsQuietly` 加回本接口；把渠道 if-else 挪进来 | 可：结构断言接口方法名集合 + 不依赖渠道枚举 |
| 78 | `<J>/service/EmployeeCardPersistenceService.java:13-16` | 给 `recordEvent` / `attachEmployeeCardsQuietly` 补 `@Transactional`（与阶段一 66/67 同源，本条补「两种事务语义都是刻意的」这个理由） | 可：反射断言 |
| 79 | `<J>/service/EmployeeCardPersistenceService.java:24-26` | 把 `saveFromStatusNotify` 的「0 行即抛」改成只记日志 | 可：单测断言 0 行时抛 `IllegalStateException` |
| 80 | `<J>/service/EmployeeCardPersistenceService.java:55-59` | 把 `refreshProfileFromAcc` 的「0 行只 WARN」与上一条统一 | 可：单测断言 0 行时不抛 |
| 81 | `<J>/service/EmployeeCardPersistenceService.java:104-107` | 把 `attachEmployeeCardsQuietly` 的 0 行分支改成强行覆盖 `THIRD_USER_ID` | 可：SQL 文本断言 WHERE 含「为空或等于目标值」 |
| 82 | `<J>/service/impl/EmployeeCardPersistenceServiceImpl.java:134` | 把 `applyActivationResult` 的 false 出口改成「只记日志后 return true」 | 可：单测断言返回 false ⇒ 上层返 9998 |
| 83 | `<J>/service/impl/EmployeeCardOutboundServiceImpl.java:40-42` | 给出网实现类加事务；或把它的方法放进调用方 `@Transactional` | 可：反射断言本类无 `@Transactional` + 调用点结构断言 |
| 84 | `<J>/domain/EmployeeCardStatus.java:12-16` | 自行调整 ACC 定义的数字取值、依赖 `ordinal()`、只改枚举不改 `UserAccEmployeeCardMapper.xml` 的 `CARD_STATUS = 1`（阶段一 32 只覆盖前两项） | 可：断言 `code()` 映射表 + SQL 文本断言 |
| 85 | `<J>/domain/ChannelBindingRule.java:44-48` | 把 `isWallet` 改回「要求调用方先 trim」 | 可：单测断言 null 与带空白入参 |
| 86 | `<J>/domain/PhoneChangeRule.java:49-50` | 把入口必填校验合并进 `decide`（会导致「为了校验两个字符串先查一次库」） | 可：单测断言 `validateInput` 不需要 `UserItpRegInfo` |
| 87 | `<J>/model/ItpSignProperties.java:18-25`（阶段一 52 只记「NEVER 搬走键」） | 只清 Java 侧或只清 properties 侧的 `signKey` 明文真值（两处都写了，改一处等于没改） | 部分：可写「两处都不含明文」的配置扫描断言 |
| 88 | `<J>/service/AccountRequestVerifier.java:19-22` | 因「零调用点」删掉本类（与阶段一 43 同源，本条补「24 个端点全裸露」这个事实） | 可：断言类存在 |
| 89 | `<J>/service/impl/RegistrationCommitServiceImpl.java:41-43` | 把 `TransactionTemplate` 换成方法上的 `@Transactional`（会把调用方 RPC 圈进事务） | 可：反射断言本类无 `@Transactional` + 字段存在 |
| 90 | `<S>/account-server-active-user-cardtype-index-migration.sql:9-14` | 把唯一索引简化成朴素两列；或给它加 `LOCAL`（`ORA-14039`） | 可：SQL 文本断言索引定义含两个 `CASE` 且无 `LOCAL` |
| 91 | `<S>/account-server-pay-account-id-migration.sql:5` | 按 `DATA_LENGTH` 的 128 写 `PAY_ACCOUNT_ID` 列宽（应为 64 CHAR，与来源列一致） | 部分：可写库回查断言，非纯单测 |
| 92 | `<X>/UserItpRegInfoMapper.xml:108-110` | 用不过滤 `DEL_YN` 的查询走常规链路 | 部分：可结构断言调用点集合 |
| 93 | `<X>/UserItpRegInfoMapper.xml:193-195` | 把「注销未归档期间同一 CARD_ID 并存」那条查询改回返回单条 | 可：断言返回类型是 List |
| 94 | `application.properties:24` | 把任一 `service.*.url` 写成 `127.0.0.1`，或删掉键（退化成 `*-service` 默认名） | 部分：可写配置扫描断言，**无法覆盖集群 env 覆盖值** |

### 本轮覆盖率自评

**计数口径先说明**（否则数字不可比）：本轮用脚本逐文件扫注释行，**块注释的中间行也计一行**（`/** … */` 内的每行、
`<!-- … -->` 内的每行都算），`.properties` 计 `#` 开头行，`.sql` 计 `--` 开头行。

| 项 | 数 | 说明 |
|---|---|---|
| `account-server/src/main` 文件数 | **86** | 其中 `.java` 79、`.xml` 8（mapper）、`.properties` 1、`.sql` 6（本轮口径含 `sql/` 目录） |
| Java 注释行总数 | **2697** | 与用户实测一致 |
| XML 注释行 | **212** | 按上述口径（含块注释中间行）。用户给的 96 是「XML/properties」另一种口径（估计只计 `<!--` 起始行或非空文字行），**两者不冲突、但 NEVER 混用** |
| properties 注释行 | **7** | 其中 3 行是乱码（见 §七末条） |
| SQL 注释行 | **39** | 5 个 `*-migration.sql` 共 39 行；`account-server-schema.sql` 与 `account-server-card-type-migration.sql` **0 行** |
| 阶段一定位串命中的行区间（差集基准） | 覆盖后剩 **777 行** | 差集法：把阶段一所有 `文件:行号` / `文件:起-止` / `` `:起-止` `` 定位串展开成行集合（每条前后各放宽 3 行），再从全量注释行里减掉 |
| 本轮抽取条数 | **95 条** | §一~§八 正文 **65 条** + §九 版本表 **5 条** + 墓碑清单 **25 条**（70~94）。另有 §十 的 7 行是**落点索引、不计条数** |
| 矛盾与待裁决 | **5 条** | 含用户点名复核的 `RegistrationCommitService.java:36-37`（结论：注释是 ADR-D52 之前的旧措辞，MUST 以 ADR-D52 为准） |
| 样板跳过行数（估算） | 约 **269 行** | 其中 **169 行**是 `entity/*`（6 个实体）+ `page/*`（4 个视图 / 查询对象）+ `model/application/RequestApplicationRespDTO` 的纯字段 Javadoc；余约 100 行是 `@param` / `@return` / 复述方法名的接口 Javadoc，以及**逐字重复 12 次**的「构造器注入（ADR-D37）」（该句已在 §一末条按 1 条计入） |
| 有知识量的差集行（估算） | 约 **508 行** | = 777 − 269，已全部落进本轮 95 条 |

**零知识注释文件清单**（本轮实测，注释行为 0 或全部为纯样板）：

- **注释行 = 0（7 个）**：`<J>/../AccountServer.java`、`<J>/mapper/UserItpRegLogMapper.java`、
  `<J>/mapper/UserAccEmployeeCardLogMapper.java`、`<X>/UserItpRegLogMapper.xml`、`<X>/UserAccEmployeeCardLogMapper.xml`、
  `<S>/account-server-schema.sql`（**8 张表 + 2 个序列的 DDL 全文零注释**）、`<S>/account-server-card-type-migration.sql`。
- **有注释但全是纯样板（本轮判为零知识，只计行数）**：`<J>/entity/UserAccEmployeeCard.java`、
  `<J>/entity/UserAccEmployeeCardLog.java`、`<J>/entity/UserItpRegLog.java`、`<J>/page/ItpUserSearchQuery.java`、
  `<J>/model/application/RequestApplicationRespDTO.java`（21 行全是「返回码 / 返回消息 / 签名类型」这类字段 Javadoc）。
  另有三个实体只含**个别**实义字段（`UserItpRegInfo.hceData`、`UserPhoneChangeLog.userType` / `signSyncRetryCount`），
  已在 §六末条单列，其余字段 Javadoc 计入样板。

**仍未覆盖 / 本轮刻意不做的部分**（下一轮的入口）：

1. **`src/test` 全部未读**（本轮范围限定 `src/main`）。已知测试类名在阶段一被引用过（`UserItpRegInfoMapperSqlTest`、
   `PayChannelInternalContractTest`、`delYnLiteralsStayInsideTheTwoFragments`），**测试里的注释可能记着更细的口径**，未抽。
2. **`pom.xml` / jkube 配置的注释未抽**（本模块 `build-image-remote` 绑 `package`、2026-09-08 已核实，事实在 AGENTS.md §7，
   但 pom 内是否有说明性注释本轮没看）。
3. **`log4j2-*.xml` 等非 mapper 资源未纳入**（本轮 xml 只扫 `resources/mapper/`）。
4. **代码里有行为、注释里没记的事项**一律未代笔补写。本轮遇到 2 处并记在此：
   ①`AccountRequestVerifier` 的 `signType=00` 免签逻辑（AGENTS.md §2.2.1 有此说法，**本类注释无出处**）；
   ②`maskPayId` 的脱敏规则虽有一行注释（`PayChannelServiceImpl:686`「保留前 4 后 4，中间固定 4 个星号；长度不足 8 位时整串打星」），
   但**「为什么选这个规则」无出处**。
5. **被注释掉的失效代码不按四类归档**（沿用阶段一口径）：本轮新遇到 `application.properties:5` 的
   `#other.sql.host=…`（已在 §七末条按「陷阱 + 事实」记一次）。
6. **阶段一「归类存疑」5 条中的 3 条本轮已裁决**（`RegistrationCommitService:36-37` → 矛盾第 1 条；
   `PhoneChangeService:17-27` → 矛盾第 2 条；`EmployeeCardServiceImpl:111-113` 那条**唯一的英文注释**仍未裁决，
   `PayChannelServiceImpl:132-139` 的整段失效代码仍按「不归档」处理）。

## 附：account-server DDL 与 fep-acc-server 补漏（2026-09-16，阶段三）

> **有并发写入者，引用行号前 MUST 先 grep 现查。** 本节行号是 2026-09-16 抽取时刻的快照。
>
> 本轮定位：阶段一 / 阶段二覆盖的是 `account-server/src/main` 的 **Java 与 mapper XML 注释**，
> 以及 5 个 `*-migration.sql` 的 `--` 行。**本轮补的是那两轮口径外的三块**：
> ①7 个 SQL 文件里的 **107 条 `COMMENT ON`**（`COMMENT ON` 是 DDL 语句、不是注释语法，
> 因此阶段二按「注释行」口径把 `account-server-schema.sql` 判为「零注释」是**口径自洽的**，
> 但那 98 条列注释里承载着**列取值域的唯一权威定义**，属真实空白 —— 本轮补齐，见矛盾第 1 条）；
> ②`fep-acc-server` 全模块 **30 行注释**（仅 2 个接口、无 DB、无 mapper）；
> ③`account-server/src/main/resources/application.properties:4~6` 的**乱码注释**（阶段二只提到 `:5`）。
> **本轮 NEVER 与阶段一 / 阶段二重复**：凡阶段二已落笔的 `--` 行（如 phone-sync 那 6 行的 `@Scheduled` 过期措辞），
> 本轮只交叉引用、不重抄。
>
> 路径前缀沿用前两轮：`<S>` = `account-server/src/main/resources/sql/`，
> `<P>` = `account-server/src/main/resources/application.properties`，
> 另加 `<F>` = `fep-acc-server/src/main/`。

### 一、`USER_ITP_REG_INFO`（表 + 20 列，`<S>/account-server-schema.sql:184-204`）

grep 短语：`COMMENT ON COLUMN USER_ITP_REG_INFO`。这张表是账户域的根实体，**列取值域的唯一权威定义就在这 21 条里**。

- **`DEL_YN` 的取值方向与直觉相反** —— `:193`「删除标识，**1-有效，0-已注销**」。
  **MUST 先读这条再写任何 `WHERE DEL_YN`**：把它当成「1 = 已删」会把有效卡全过滤掉、把注销卡全捞出来。
  实体侧 `isActive()` 与 `<X>/UserItpRegInfoMapper.xml` 的两个 `DEL_YN` 片段是配套的（阶段一已记）。
- **分区键是虚拟列，不是 `THIRD_USER_ID` 本身** —— `:190`「第三方用户标识后两位，**分区字段**」，
  DDL 侧 `:7-8` 是 `THIRD_USER_ID_SUFFIX VARCHAR2(2 CHAR) GENERATED ALWAYS AS (SUBSTR(THIRD_USER_ID, -2)) VIRTUAL`，
  `:25-127` 按它 LIST 分区共 **101 个分区**（`P00`~`P99` + `PDF` DEFAULT）。
  连带两条硬约束：①三个业务索引 `IDX_UIRI_THIRD_USER_ACTIVE` / `IDX_UIRI_CARD_ID` / `IDX_UIRI_THIRD_PAY_ID`（`:129-139`）
  都带 `LOCAL`；②唯一索引 `UK_UIRI_ACTIVE_USER_CARDTYPE`（`:141-145`）**不带 `LOCAL`**，原因见 §二①。
- **`CARD_TYPE` 与 `ITP_CARD_TYPE` 是两个不同语义的列，NEVER 混用** —— `:187`「**转换后**的卡类型」、
  `:188`「**APP 入参**卡类型」。映射关系写在 `<S>/account-server-card-type-migration.sql:4-14`：
  `0441→02`、`0442→03`、`0443→04`、`0444→11`、`0445→12`、`0446→13`、`0447→14`、`0448→15`，
  其余原样保留（`ELSE CARD_TYPE`）。该脚本还把 `ITP_CARD_TYPE` 回填后再 `MODIFY (... NOT NULL)`（`:16`），
  **顺序不能颠倒**：先加非空约束会因存量行为空而失败。
- **`CARD_ISSUE_CODE` 参与码体拼装，`ISSUE_ORG_CODE` 不参与** —— 这是两条最容易互换的列：
  - `:198`「发行渠道编码，**仅 `0001` 正常渠道 / `0007` 支付宝出行**，**参与码体拼装，取右 2 位落码体渠道位**」；
  - `:199`「发卡机构编码，APP 开户上送原值，`0004` 海上巴士 / `0007` 支付宝出行 / `0008` 成都地铁 /
    `0020` 青岛地铁早期 / `5412` 青岛地铁 / `5413` 畅行 U 惠小程序，**仅留痕不参与码体拼装**」。
  注意 `0007` 在两列里都出现、含义不同；`ISSUE_ORG_CODE` 是 6 个已知取值的**开放**集合
  （未知值只 ERROR 不拒绝开户，见阶段二 `normalizeIssueOrgCode`），而 `CARD_ISSUE_CODE` 是**封闭**的两值。
- **`COMPANION_FLAG` 只有两个有值取值** —— `:203`「同行票或第三方票标识，**Y-同行票，C-第三方票**」，
  空即普通票。这两个字母是 `UK_UIRI_ACTIVE_USER_CARDTYPE` 两个 `CASE` 里 `NOT IN ('Y','C')` 的来源，
  **改动取值 MUST 同批改索引谓词与 `selectActiveByThirdUserIdAndCardType`**（三处逐字对齐）。
- **`HCE_DATA` 有两个写入方** —— `:204`「HCE 卡数据，**开户时生成并由闸机交易 `reserve1` 更新**」，
  即 account-server 写第一次、随后由 `updateHceData`（对内契约面，调用方 ticket-server / face-pay / collect-pay）覆盖。
  列宽 512 CHAR（`:21`）。
- 其余 14 条是字段名直译（`ID` 主键、`CARD_ID` 逻辑卡号、`MSISDN` 手机号、`REG_TMS` / `UN_REG_TMS`、
  `DEL_THIRD_USER_ID`「注销操作对应的第三方用户标识」、`USER_NAME` / `USER_ID`「证件号」、
  `THIRD_PAY_ID`「第三方支付用户标识」、`CHANNEL`、`REQ_CONTRACT_NO`「签约请求号」），**零决策信息**。

### 二、`account-server-active-user-cardtype-index-migration.sql` 那 26 行里的四条硬知识

grep 短语：`UK_UIRI_ACTIVE_USER_CARDTYPE`、`ORA-14039`、`ORA-01452`、`EXECUTE IMMEDIATE`。
这是 7 个 SQL 文件里**知识密度最高**的一个（31 行里 26 行是注释、5 行是 DDL），阶段二只把它记成墓碑第 90 条
（「NEVER 朴素两列 / NEVER 加 LOCAL」），四条硬知识里的另外两条本轮首次落笔。

1. **ADR-D49 ① 的并发窗口后果（`:1-6`）** —— 没有这个唯一索引时，
   `AccountRegistrationServiceImpl` 的「前置查重」与「INSERT」之间有窗口：
   **两条并发 IF8A-01 或 APP 超时重推会双双通过查重、双双落库，用户拿到两张有效卡**，
   而 `handleDuplicateRegistration` 那段 `DuplicateKeyException` 兜底**永远走不到**。
   `:5` 明写「**编译、单测、`xmllint` 全都发现不了**」—— 这条是「索引缺失 = 幂等保护完全失效」的活样例，
   与 AGENTS.md §8「一天撞三次」的第 ③ 条同源（该索引此前只写在 `*-schema.sql` 里、没有独立迁移脚本）。
   `:6` 记了回查证据：**2026-09-14 已在 `AFCITPDB` 执行并回查（`UNIQUE` / `FUNCTION-BASED NORMAL` / `VALID` / `PARTITIONED=NO`）**。
2. **NEVER 简化成朴素两列（`:9-12`）** —— 两个 `CASE` 是刻意把 `COMPANION_FLAG` 为 `Y`（同行票）/ `C`（第三方代开）
   的行**排除在唯一性之外**，那类票按业务定义「**每次都给新卡**」。
   改成朴素 `UNIQUE (THIRD_USER_ID, CARD_TYPE)` 的后果不是「不够精确」，而是
   **这些合法请求的第二张卡直接 INSERT 失败**（同行票开户整条打挂）。
   同一行还带一条对齐要求：查重侧 `<X>/UserItpRegInfoMapper.xml` 的
   `selectActiveByThirdUserIdAndCardType` **MUST 与本谓词逐字对齐** —— 两边不一致时，
   「查重放过 + 索引拒绝」会把并发异常变成对 APP 的硬失败，反之则回到 1 的双卡缺陷。
3. **NEVER 顺手加 `LOCAL`（`:13-14`）** —— `USER_ITP_REG_INFO` 按 `THIRD_USER_ID` 的**派生值**做 LIST 分区
   （见 §一），而索引键是两个 `CASE` 表达式、**不是分区键**，Oracle 只允许 `GLOBAL`，
   加 `LOCAL` 直接报 **`ORA-14039`**。这条是「同一张表上三个 `LOCAL` 索引 + 一个非 `LOCAL` 索引」并存的原因，
   **NEVER 为了「风格统一」给它补 `LOCAL`**。
4. **`ORA-01452` 前置统计 + mcp 执行时的 PL/SQL 包裹（`:16-26`）** —— 两条操作性知识：
   - **在新库上执行前 MUST 先按索引的确切谓词统计**（`:18-22`，原文可直接复制）：
     `SELECT COUNT(*) AS TOTAL, COUNT(DISTINCT THIRD_USER_ID || '#' || CARD_TYPE) AS DISTINCT_KEY
      FROM USER_ITP_REG_INFO WHERE DEL_YN = 1 AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C');`
     两值不等即建索引必报 `ORA-01452`；**有重复只能与业务定归属、NEVER 删行**（`:17`「卡号可能已发给用户」）。
     注意统计谓词**必须与索引 `CASE` 里的谓词同源**，用宽一点的条件统计会漏判。
   - **经 `mcp_database_qd` 执行时 MUST 包一层 PL/SQL**（`:24-26`）：它的 SQL 解析器拒绝带 `CASE` 的
     `CREATE INDEX`（`McpSqlValidationException`），绕法是
     `BEGIN EXECUTE IMMEDIATE '<原 DDL>'; END;`，**内层单引号写两个**；
     脚本 `:26` 存了一份可直接粘贴的完整单行版本。
     **NEVER 因为 mcp 验证不过就去改索引形状** —— 那正是 2 要禁止的事。

### 三、其余四个 `*-migration.sql` 的 `--` 行与 9 条 `COMMENT ON`

- **`account-server-companion-flag-migration.sql`（4 行 / 1 `--` / 1 `COMMENT ON`）**：`:1`「为**既有**
  `USER_ITP_REG_INFO` 表增加同行票/第三方票标识」。零决策信息，但它是「每个新增列都要有独立迁移脚本」这条规则的**正面样板**。
- **`account-server-hce-data-migration.sql`（4 行 / 1 `--` / 1 `COMMENT ON`）**：`:1`「**已执行
  `account-server-card-type-migration.sql` 的环境也必须执行本脚本**」—— 唯一记录了**两脚本之间无依赖、需各自单独执行**这一事实，
  防的是「以为卡类型那次已经把列都加齐了」。
- **`account-server-pay-account-id-migration.sql`（8 行 / 5 `--` / 1 `COMMENT ON`）**：`:1-4` 是 ADR-D30 的
  事故复盘（此前只改 schema、脚本缺失，而 `UserPayChannelMapper.xml` 的
  `selectByThirdUserIdAndCardTypeAndCardId` 与 `updatePayAccountIdByReqContractNo` **已经在用它**，
  「运营页『支付账号』列与 IF8A-77 回写一跑就 `ORA-00904`」，2026-09-14 已在 `AFCITPDB` 执行并回查）；
  `:5`「**宽度取 64 CHAR 与来源列 `APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID` 一致，NEVER 按 `DATA_LENGTH` 的 128 写**」
  —— 这条对应 `docs/domain/decisions.md` 撤回记录里的「用 `DATA_LENGTH` 判列长」那条（阶段二墓碑第 91 条）。
- **`account-server-phone-sync-migration.sql`（20 行 / 6 `--` / 4 `COMMENT ON` / 1 索引）**：
  `--` 那 6 行阶段二已记（矛盾第 4 条：`:4` 的「由 `@Scheduled` 扫表补偿重推」**已过期**，
  现行调度在 web-admin `sys_job` 290，**NEVER 据它在本模块加 `@Scheduled`**），本轮只补 4 条 `COMMENT ON`
  与一处**与 schema 不一致**的事实：
  - `:14` 的 `SIGN_SYNC_STATUS` 注释是「**PENDING-待投递，SUCCESS-已送达，FAILED-投递失败待重试；
    `NULL` 表示本行早于改造，NEVER 被补偿扫描捞取**」，而 `<S>/account-server-schema.sql:369` 同一列的注释是
    「PENDING-待同步、SUCCESS-已同步、FAILED-同步失败；`NULL` 表示该行早于本功能上线」。
    **取值集合一致、措辞不同，且只有迁移脚本那份写了「`NULL` NEVER 被扫描捞取」这条行为约束** ——
    读取值域看 schema、读扫描行为看迁移脚本。
  - `:15` 重试次数「达配置上限后**不再扫描、转异常工单**人工处理」；schema `:370` 补出工单表名
    （`ACCOUNT_EXCEPTION_TICKET`）。两份合起来才是完整链路。
  - `:16`「配合 `staleMinutes` 判定 `PENDING` 滞留」—— **`staleMinutes` 这个参数名只在这一行出现过**，
    是把「`SIGN_SYNC_TIME` 有什么用」讲清楚的唯一出处。
  - `:17`「最近一次投递的返回码与消息，**超长由调用方截断**」（列宽 1024 CHAR）—— 截断责任在**调用方**，
    NEVER 指望数据库侧兜。
  - 列宽差异：迁移脚本 `:9` 是 `SIGN_SYNC_RETRY_COUNT NUMBER(22) DEFAULT 0`，schema `:341` 是 `NUMBER DEFAULT 0`
    —— 语义等价、写法不一致，**回查列定义时不要据此判定「库与脚本不符」**。
- **`account-server-card-type-migration.sql`（19 行 / **0** `--` / 2 `COMMENT ON`）**：见 §一「`CARD_TYPE` 与
  `ITP_CARD_TYPE`」条。它是**唯一一个「零 `--` 注释但有 `COMMENT ON`」的迁移脚本**，
  阶段二据 `--` 口径把它列进「零知识文件清单」，本轮据 `COMMENT ON` 口径把它移出（见矛盾第 1 条）。

### 四、`APP_USER_PAY_CHANNEL`（表 + 12 列，`<S>/account-server-schema.sql:237-248`）

- **主键是三列复合 `(THIRD_USER_ID, CARD_TYPE, CHANNEL)`**（`:234`），`CARD_ID` **不在主键里**
  —— 这解释了为什么 `selectByThirdUserIdAndCardTypeAndChannel` 是查重口径、而带 `CARD_ID` 的那条是反查口径。
- **`PAY_ACCOUNT_ID` 那条是本表唯一的长注释（`:240`），四层含义都要**：
  ①来源「支付域 `APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID`（原值即支付中心签约回调的 `payUserId`）」；
  ②「**与 `THIRD_PAY_ID` 不同源，NEVER 混用**」——同一张表里两个都像「支付方标识」的列，这是唯一的区分出处；
  ③「**当前仅 IF8A-77 回写，未走过该接口的行为空**」——运营页看到空值是**预期**，不是数据丢失；
  ④「加此列是为让运营页面本地读、**去掉逐渠道跨域 RPC**，见 ADR-D30」（即阶段一 / 阶段二那条 N+1 消除）。
- **`:248` 给 `THIRD_USER_ID_SUFFIX` 写了 `COMMENT ON`，但 `CREATE TABLE`（`:223-235`）里没有这一列，
  本表也没有分区子句** —— 在空库上顺序执行 `account-server-schema.sql` 时，这一句必报 `ORA-00904`。
  见矛盾第 2 条。**NEVER 据这条注释推断 `APP_USER_PAY_CHANNEL` 是分区表**（只有 `USER_ITP_REG_INFO` 是）。
- 其余 9 条是直译（`CARD_ID`「地铁会员卡号」、`STATUS`「通道状态」等）。
  **注意 `STATUS` 这条只写「通道状态」、没给取值域** —— 取值只能去 `PayChannelService` / `ChannelBindingRule` 看（阶段二 §三）。

### 五、员工码两张表（`<S>/account-server-schema.sql:287-303`、`:321-327`）

- **`USER_ACC_EMPLOYEE_CARD.CARD_STATUS` 的四个数字取值（`:299`）：1-启用，2-禁用，3-未启用，4-注销。**
  DDL 侧有 `CONSTRAINT CK_UAEC_CARD_STATUS CHECK (CARD_STATUS IN (1,2,3,4))`（`:269`）兜底 ——
  **数据库层就会拒非法值**，因此 `EmployeeCardStatus` 枚举（阶段二墓碑 84）与这个 CHECK 是**两道并行防线**，
  改枚举取值 MUST 同批改 CHECK，否则新增取值会在落库时被数据库拒掉、且报的是约束名而非业务错。
- **两个「不可变」约束只写在注释里，没有数据库约束** —— `:289`「员工号，对应员工码接口 `cardNo` 字段，
  **唯一且不可变**」（唯一性有 `UK_UAEC_CARD_NO`，**不可变性没有**）、`:291`「绑定手机号，**开通后不可变更**」
  （**完全没有约束**）。**这两条只能靠代码保证**，改员工码相关 UPDATE 前 MUST 先读这两行。
- **`PHOTO_URL` 是 CLOB，且有业务上限** —— `:298`「员工照片 Base64 内容，**原始照片限制 250KB**」。
  连带 AGENTS.md §8 那条 MCP 坑：**数 CLOB 非空行 MUST 写 `COUNT(CASE WHEN col IS NOT NULL THEN 1 END)`**，
  `COUNT(PHOTO_URL)` 在 Oracle 非法、且被 MCP 报成 cast 错误。
- **`USER_ACC_EMPLOYEE_CARD_LOG.EVENT_TYPE` 四个取值（`:324`）：`OPEN`-开通，`CHANGE`-信息变更，
  `STATUS`-状态变更，`CANCEL`-注销**，同样有 CHECK 约束（`:314`）配套；
  `CARD_STATUS` 在日志表里是「**本次操作后**的状态」（`:325`）且**允许为空**（CHECK 写的是 `IS NULL OR IN (1,2,3,4)`，`:315`），
  即「信息变更类事件不带状态」是合法形态。`REMARK`「操作说明**或失败原因**」（`:326`）—— 同一列承载成功说明与失败原因两种语义。

### 六、`USER_PHONE_CHANGE_LOG` 与 `ACCOUNT_EXCEPTION_TICKET` 的表级归属声明

- **`USER_TYPE` 只有两个取值（`:361`）：`ITP`-地铁 APP 用户，`ALIPAY`-支付宝用户**；
  **`OPER_TYPE` 目前只有一个取值（`:364`）：`CHANGE_PHONE`-更换手机号** —— 这张表被设计成可容纳更多操作类型，
  但现状只有换号一种，**NEVER 因为列名叫 `OPER_TYPE` 就假定已有别的取值在跑**。
  主键靠序列 `SEQ_USER_PHONE_CHANGE_LOG`（`:347`，`START WITH 1 INCREMENT BY 1`），
  **不是 IDENTITY 列** —— 与本模块另外四张表（`USER_ITP_REG_INFO` / `USER_ITP_REG_LOG` / `USER_ACC_TICKETNO` /
  两张员工码表用的是 `GENERATED BY DEFAULT AS IDENTITY`）**取号方式不同**，写 INSERT 前 MUST 分清。
- **`ACCOUNT_EXCEPTION_TICKET` 的表注释（`:393`）是一条领域边界声明**：
  「补偿重试达上限等无法自愈的情况在此留一条待人工处理的记录。**本表由 account-server 独占，
  其它域 NEVER 写入，各域应建自己的同类表**」—— 这与 `docs/domain/outbox.md` 那条「**NEVER 抽一张公共 outbox 表**」
  （ADR-D46）是同一判据在异常工单上的落点。**跨域复用这张表就是越界**。
  - `TICKET_TYPE` 在 `:395` 只列了两个取值（`SIGN_SYNC_RETRY_EXHAUSTED`、`EMPLOYEE_CARD_STATUS_UNSYNCED`），
    **而代码里还有第三个 `CARD_POOL_CONFIRM_REJECTED`**（`AccountExceptionTicket.TYPE_CARD_POOL_CONFIRM_REJECTED`，
    ADR-D52 引入）。**注释已过期**，见矛盾第 3 条。
  - `BIZ_KEY` 的构造规则只在 `:396`：「与 `TICKET_TYPE` 组成唯一索引；`SIGN_SYNC_RETRY_EXHAUSTED` 用
    `USER_PHONE_CHANGE_LOG.ID`，`EMPLOYEE_CARD_STATUS_UNSYNCED` 用 `卡号:目标状态`」，
    唯一约束是 `UK_ACCT_EXC_TICKET_TYPE_KEY UNIQUE (TICKET_TYPE, BIZ_KEY)`（`:386`）——
    **开单幂等完全依赖这个组合键，新增工单类型 MUST 同批定义它的 `BIZ_KEY` 构造规则**，
    否则同一件事会开出无数条工单。
  - `DETAIL`「开单原因与现场信息快照，**NEVER 写入密钥等敏感信息**」（`:400`，列宽 2000 CHAR）
    —— 与 AGENTS.md §5.2「敏感配置」同源，是工单表侧的落点。
  - `TICKET_STATUS` 两值：`OPEN`-待处理、`CLOSED`-已处理（`:398`）；`RETRY_COUNT` 是「**开单时**已重试次数」（`:399`），
    即快照值、开单后不再增长。

### 七、`fep-acc-server` 全模块 30 行注释（仅 2 个接口、无 DB）

模块形态先说清：`<F>` 下**只有 3 个 `.java` + 1 个 `application.properties`**，
另有两个空的 `service/.gitkeep`、`service/impl/.gitkeep` —— **service 层是空目录**，
这是「纯转发前置」这一事实最硬的结构证据（**NEVER 在这里加业务逻辑，否则 service 目录一有内容就说明边界破了**）。

- **`<F>/java/.../FepAccServer.java:8-10`（3 行）**：「ACC 前置服务启动类」。
  零知识，但类上三个注解是事实：`@SpringBootApplication` + `@ConfigurationPropertiesScan` + **`@EnableRpcAccount`**
  —— **本模块只装配 account 一个 RPC 客户端**，没有第二个下游。
- **`<F>/java/.../controller/BaseAccController.java:6-8` + `:11-17`（10 行）**：
  类注释「ACC FormData 接口的**公共处理基类**」；方法 Javadoc「将 FormData 中的业务 JSON 转换为目标 DTO」+
  `@param request` / `@param targetType` / `@return`。
  实现侧两条**没写进注释但属契约**的事实（本轮不代笔补写，只在此记录）：
  ①入参已收口到 `model` 的 `ItpCommonFormRequest`（AGENTS.md §5.1 那次删 7 份副本，`fep-acc-server` 的
  `CommonRequest` + `CommonFormRequest` 就在被删名单里）；
  ②`bizData` 为 `null` / 空白时**兜成 `"{}"`** 而不是返回 `null`（`:20`）——
  即**下游永远收到非空 DTO**，「字段全空」与「没送 bizData」在这里被抹平。
- **`<F>/java/.../controller/EmployeeCardController.java:16-18`、`:28-36`、`:44-46`（15 行）**：
  - 类注释「员工码相关接口入口」；类上**只有 `@RestController`、没有类级 `@RequestMapping`**
    —— 两条 URL 是**绝对路径字面量**（`/employee_card/notify`、`/employee_card/update_notify`），
    与阶段二 §一那条「对内契约面 NEVER 加类级前缀」是同型约束，**加前缀 = ACC 侧全部 404**。
  - `:28-36` 是本模块**唯一带样例报文的注释**：「请求以 `multipart/form-data` 提交 **APP 同款公共字段**，
    业务参数放在 `bizData` 中，例如
    `{"cardList":[{"phone":"13800138000","cardNo":"QD20240001","cardStatus":1}]}`」。
    三条可用信息：①ACC 侧走的是 `multipart/form-data`（两个端点都显式写了
    `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`），**不是 `x-www-form-urlencoded`** ——
    与 AGENTS.md §4 那条「请求格式 `application/x-www-form-urlencoded`」**不是同一条链路**，
    照 §4 造 ACC 请求会 415；②`bizData` 里是 `cardList` **数组**，即开卡通知天生是批量语义
    （对应 account-server 侧的分片与单卡失败隔离）；③`cardNo` 形如 `QD20240001`，是**员工号**、不是逻辑卡号。
  - `:44-46`「接收员工信息变更通知并**剥离 ACC 公共消息头**」—— 这半句是「前置层到底做了什么」的唯一出处：
    它做的就是**剥壳 + 转发**（`parseBizData` 后直接 `accountClient.updateEmployeeInfo(bizData)`，无任何加工）。
  - 两个端点都 `log.info("...：{}", request)` 打整个 `request` —— 按 AGENTS.md §5.1 那条，
    `ItpCommonFormRequest.toString` 对 `sign` 恒定脱敏、但 **`bizData` 会完整进日志**，
    因此**员工姓名 / 手机号 / 身份证号会原样落日志**。这属现状事实，本轮只记录、未改代码。
- **`<F>/resources/application.properties:9-10`（2 行）**：
  「account-server RPC 地址：默认值用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11）。
  **NEVER 写 `127.0.0.1`——在 K8s 里等于打到自己**。」
  键是 `service.account.url=${SERVICE_ACCOUNT_URL:http://account-n4ba6-svc.itp.svc:9098}`（`:11`）——
  **本模块唯一的下游地址，且是带 `${ENV:}` 包装的正确形态**（对比 AGENTS.md §8 那份 `testngbackV2` 裸硬编码清单，
  本模块**不在其中**）。`server.port=9110`（`:1`）、`spring.application.name=fep-acc-server`（`:2`）。

### 八、`<P>:4~6` 的乱码注释（grep 中文搜不到的那三行）

**位置与现状**（2026-09-16 逐字节实测）：

```
<P>:4   #?????? ???IP
<P>:5   #other.sql.host=<被注释掉的真值，本文档不回显>
<P>:6   #?????? ???IP
```

- **`:4` 与 `:6` 的问号是真实的 `0x3F` 字节，不是显示问题。** `hexdump -C` 实测这两行是
  `23 3f 3f 3f 3f 3f 3f 20 3f 3f 3f 49 50`（`#` + 6 个 `?` + 空格 + 3 个 `?` + `IP`）。
  也就是说**原文的中文已经不可逆地丢了** —— 文件某次被以非 UTF-8（GBK 系）编码的编辑器打开并另存时，
  无法映射的字符被替换成了字面 `?`，**不是 mojibake（可用 `iconv` 还原），而是已经发生的信息损毁**。
  本轮已试过 `iconv -f GB18030`，输出与原文逐字节相同 —— **NEVER 再尝试用 `iconv` 还原这三行**，
  想知道原意只能问写它的人或看 SVN 历史（`svn cat -r <n>`）。
- **「为什么 grep 中文搜不到」**：全文用 `LC_ALL=C grep -n $'[\x80-\xff]'` 实测，
  **整个文件只有 4 行含非 ASCII 字节**（`:23`、`:24`、`:53`、`:62`），`:4` / `:6` **不在其中**。
  因此任何形如 `grep '数据库'` / `grep '生产'` / `grep 'IP'`（前两个）的检索都**必然 0 命中**，
  而 `file` 命令又会报 `Unicode text, UTF-8 text, with CRLF line terminators`（因为那 4 行确实是合法 UTF-8）——
  **「`file` 说是 UTF-8」不等于「文件里没有编码事故」**，它只看整体能否解码。
- **判据（本轮确立）**：排查「注释明明存在但 grep 不到」时 **MUST 按这个顺序**：
  1. `file <path>` 看整体编码与行尾（本例 CRLF，说明确实被 Windows 侧工具编辑过）；
  2. `LC_ALL=C grep -n $'[\x80-\xff]' <path>` 列出**真正含非 ASCII 的行号**，
     与「肉眼看到有中文的行号」对比 —— 差集就是被损毁的行；
  3. 只有当第 2 步能列出该行时，才有必要 `iconv -f GB18030 -t UTF-8` 试还原；
     该行不在第 2 步结果里时，**字符已经没了，iconv 无用**。
  **NEVER 只凭 `file` 的输出判断「编码没问题」，也 NEVER 直接 `iconv` 整个文件**
  —— 本例整文件 `iconv -f GB18030` 会把 `:23/:24/:53/:62` 那 4 行真正的 UTF-8 中文**打成乱码**，
  等于用一次损毁去换另一次损毁。
- **`:5` 是一行被注释掉的 `other.sql.host` 真值**（生效值在 `:7` 是 `${DB_HOST:}`）。
  按 AGENTS.md §5.2「敏感配置」，**本文档只记键名与位置、NEVER 回显该值**；
  要看真值请直接读 `<P>:5`，要看线上实际值 **MUST 查 Deployment 的 `other.sql.host` env**。
  阶段二已按「陷阱 + 事实」在 §七末条记过一次这一行，**本轮不重复**，只补它被两行乱码夹在中间这个位置事实
  —— 这也解释了那两行乱码原本大概是在说明这个地址的用途（写它的人显然是想给这行真值配一段中文说明）。
- **本轮不改这三行**（属改配置文件正文，不在「删注释」范围内）。**建议裁决**：
  ①`:4`/`:6` 两行已无信息、可直接删或改写成 ASCII 说明；
  ②`:5` 的真值应移出仓库、只留 `${DB_HOST:}`。两项都需人工确认后再动。

### 矛盾与待裁决

1. **`COMMENT ON` 算不算「注释」：阶段二判 `account-server-schema.sql`「零注释」，本轮判它有 98 条知识载体。**
   两者**不是对错关系，是口径差**：阶段二的计数脚本按 `--` 开头行统计（该文件确实 0 行），
   而 `COMMENT ON` 是**会写进 `USER_TAB_COMMENTS` / `USER_COL_COMMENTS` 的 DDL 语句**，
   语法上不是注释、语义上却是列取值域的唯一权威定义。
   **结论：两个口径都保留，但 NEVER 混用做覆盖率对比。** 本轮起明确分三个口径记：
   「`--` 行数」（阶段二用，本模块 39）、「`COMMENT ON` 条数」（本轮用，本模块 107）、
   「两者之和」（146，**仅用于说明工作量，NEVER 用于跨模块比较**）。
   连带修正一条：阶段二「零知识注释文件清单」里把 `<S>/account-server-schema.sql` 与
   `<S>/account-server-card-type-migration.sql` 列为零知识，**按 `COMMENT ON` 口径这两个文件都不是零知识**
   （98 条 / 2 条），**NEVER 再据那份清单认为「schema 文件不用读」**。
2. **`<S>/account-server-schema.sql:248` 给一个不存在的列写了 `COMMENT ON`。**
   该句是 `COMMENT ON COLUMN APP_USER_PAY_CHANNEL.THIRD_USER_ID_SUFFIX IS '第三方用户ID后两位，分区字段'`，
   而同文件 `:223-235` 的 `CREATE TABLE APP_USER_PAY_CHANNEL` **没有这一列、也没有 `PARTITION BY` 子句**。
   **后果**：在空库上顺序执行本脚本时这一句必报 `ORA-00904 invalid identifier`；
   若执行方式是「整脚本一把过、遇错即停」，**它后面的 `USER_ACC_EMPLOYEE_CARD` 等三张表全部不会建**。
   （现有 `AFCITPDB` 是既有库、不走这条路径，因此**至今没暴露**——这正是 AGENTS.md §8
   「`*-schema.sql` 只服务新建库」那条的另一面：**schema 文件的错误只在建新库那一刻才发现**。）
   **待裁决**：①删掉 `:248`（若 `APP_USER_PAY_CHANNEL` 本就不该分区），或
   ②给 `CREATE TABLE` 补上虚拟列 + LIST 分区（若原设计是要按 `THIRD_USER_ID` 分区，与 `USER_ITP_REG_INFO` 看齐）。
   **在裁决前 NEVER 拿这条注释当「本表已分区」的证据。**
3. **`<S>/account-server-schema.sql:395` 的 `TICKET_TYPE` 取值域漏了 `CARD_POOL_CONFIRM_REJECTED`。**
   注释只列 `SIGN_SYNC_RETRY_EXHAUSTED` 与 `EMPLOYEE_CARD_STATUS_UNSYNCED` 两个，
   而代码里第三个类型 `AccountExceptionTicket.TYPE_CARD_POOL_CONFIRM_REJECTED` 已在两条开户链路上真实开单
   （`AccountRegistrationServiceImpl` 与 `AlipayTripRegistrationServiceImpl` 各一处，ADR-D52）。
   `:396` 的 `BIZ_KEY` 构造规则同样没写这个类型用什么键。
   **危险方向**：按注释去做「工单类型白名单校验」或运营页面下拉框，会把卡池工单**判成非法类型**。
   **待裁决**：补齐第三个取值与它的 `BIZ_KEY` 规则（代码实测取值请现场 grep `TYPE_CARD_POOL_CONFIRM_REJECTED` 的赋值点）。
4. **同一张表被 `COMMENT ON TABLE` 写了两次，后一句静默覆盖前一句。**
   `USER_ACC_TICKETNO` 在 `:175` 有一条**墓碑式长注释**（「【已废弃，保留不删】账户侧自建卡号池。
   发号已整体迁至 card-pool-server 的 `LOGIC_CARD_POOL_CARD`（开户走 reserve/confirm/release 三段式）。
   本表在 account-server 内**无任何 mapper、entity 与读写代码**（2026-09-11 全仓 grep 核实），
   因此建表语句保留仅为与既有库结构对齐，**NEVER 再新增针对本表的代码**」），
   而 `:215` 又写了一条 `COMMENT ON TABLE USER_ACC_TICKETNO IS '账户卡号池表'`。
   **Oracle 的表注释是覆盖语义**，顺序执行后**库里只剩「账户卡号池表」这五个字，那段墓碑在数据库里完全看不到**
   —— 也就是说「本表已废弃」这个最关键的信息**只存在于仓库文件里**，
   任何从库端（`USER_TAB_COMMENTS`）反查表用途的人都会得到「这是个在用的卡号池表」的错误结论。
   **待裁决**：删掉 `:215` 那句，或把墓碑内容并入它。**在裁决前，判断本表是否在用 MUST 看 `:175`，NEVER 看库里的表注释。**
5. **遗留矛盾归类结论（阶段一提出、阶段二未裁决的两条，本轮按要求给结论）**：
   - **`EmployeeCardServiceImpl.java:111-113` 那条唯一的英文注释 → 归类「事实型 Javadoc」，保留其信息、
     翻译回中文。** 原文：`ACC cardNo is the employee number, not a logical card number. APP performs the later silent account opening.`
     裁决理由：它陈述的是**两条真实业务事实**（① `cardNo` 是员工号、不是逻辑卡号 —— 与
     `<S>/account-server-schema.sql:289` 的 `COMMENT ON` 互相印证；②后续静默开户由 APP 侧完成、**不在本模块**），
     属「不看这句就会把 `cardNo` 当卡号去查 `USER_ITP_REG_INFO`」的必要说明，**不是叙述体、不是事故史、不是墓碑**，
     因此**不属本轮删除范围**。它唯一的问题是**全模块唯一一处英文**，属风格漂移而非知识缺陷。
     **本轮处置：作为方法 Javadoc 保留，措辞译为中文并压成一行。**
   - **`PayChannelServiceImpl.java:132-139` 的整段失效代码 → 归类「失效代码（dead code），删除；
     但它遮住的业务缺口 MUST 留在文档里」。** 那 8 行是被 `//` 注释掉的 IF8A-23 校验：
     `if (!issueCardType.equals(regInfo.getCardType()) || !request.getCardId().trim().equals(regInfo.getCardId())) { 返 INVALID_PARAM「cardId或cardType与开户信息不匹配」 }`。
     裁决理由：注释掉的代码**不是注释**，它既不解释什么、也不禁止什么，留着只会让下一个人以为「这个校验还在」
     （版本历史归 SVN，不归代码正文）。**因此本轮删除这 8 行。**
     **但同时确认一个仍然生效的事实并记在此**：**IF8A-23 当前不校验 `cardId` / `cardType` 与开户信息是否匹配** ——
     只校验「有无有效账户」（`:123-128`）与「该 (thirdUserId, cardType, channel) 通道是否已存在」（`:141-150`），
     `:130-131` 还留着一行 `log.info` 打印比对结果、**但比对结果不影响流程**。
     **待裁决**：这是有意放宽（多卡 / 亲情卡场景下 APP 上送的 `cardId` 可能不是主卡）还是漏删，
     需业务确认；**NEVER 因为「代码里曾经有过」就直接把校验恢复** —— 恢复它会让当前能通的请求开始返 `INVALID_PARAM`。

### 墓碑清单（阶段三新增，编号续阶段二的 94，从 95 起；前两轮 94 条不重复）

判据同前两轮：注释的唯一作用是**禁止回退到某个已被推翻的做法或已搬走的位置**。

| # | 位置 | 它想禁止的事 | 可否写成断言测试 |
|---|---|---|---|
| 95 | `<S>/account-server-schema.sql:175` | 认为 `USER_ACC_TICKETNO` 还在用、给它新增 mapper / entity / 读写代码（发号已整体迁至 card-pool-server 的三段式） | 可：结构断言全模块无该表名的 mapper 语句与实体 |
| 96 | `<S>/account-server-schema.sql:193` | 把 `DEL_YN` 当成「1 = 已删」写 WHERE（方向与直觉相反） | 可：SQL 文本断言两个 `DEL_YN` 片段的字面量 + 单测断言 `isActive()` |
| 97 | `<S>/account-server-schema.sql:198-199` | 把 `CARD_ISSUE_CODE` 与 `ISSUE_ORG_CODE` 互换用；或让 `ISSUE_ORG_CODE` 参与码体拼装 | 部分：可单测断言码体拼装只读 `CARD_ISSUE_CODE` 右 2 位 |
| 98 | `<S>/account-server-schema.sql:240` | 把 `PAY_ACCOUNT_ID` 与 `THIRD_PAY_ID` 混用；或把「该列为空」当成数据丢失去补查跨域 RPC（会退回 ADR-D30 消掉的 N+1） | 可：结构断言运营只读路径不依赖 `PaySignClient`（与阶段二 75 同源，本条是 DDL 侧那份） |
| 99 | `<S>/account-server-schema.sql:289 / :291` | 允许 `CARD_NO` 或已开通员工码的 `PHONE` 被 UPDATE（两条「不可变」**没有数据库约束**，只能靠代码） | 可：结构断言 `UserAccEmployeeCardMapper.xml` 的 UPDATE 语句 SET 列表不含这两列 |
| 100 | `<S>/account-server-schema.sql:299` + `:269` CHECK | 只改 `EmployeeCardStatus` 枚举取值、不改 `CK_UAEC_CARD_STATUS`（新增取值会被数据库拒、报的是约束名而非业务错） | 可：断言枚举 `code()` 集合与 CHECK 里的字面量集合相等 |
| 101 | `<S>/account-server-schema.sql:393` | 让其它域往 `ACCOUNT_EXCEPTION_TICKET` 写入 / 把它当公共工单表（同 ADR-D46「NEVER 抽公共 outbox 表」） | 部分：可全仓 grep 断言只有 account-server 引用该表名 |
| 102 | `<S>/account-server-schema.sql:396` + `:386` UK | 新增工单类型时不定义它的 `BIZ_KEY` 构造规则（开单幂等全靠 `(TICKET_TYPE, BIZ_KEY)` 组合键，缺规则即同一件事开无数条） | 可：单测断言每个 `TYPE_*` 常量都有对应的 key 构造分支 |
| 103 | `<S>/account-server-schema.sql:400` | 往 `DETAIL` 里写密钥等敏感信息 | 部分：可写「开单入参不含配置键值」的扫描断言 |
| 104 | `<S>/account-server-active-user-cardtype-index-migration.sql:16-22` | 在新库上直接建唯一索引、跳过 `ORA-01452` 前置统计；或统计出重复后删行（卡号可能已发用户） | 部分：可写库回查断言（`COUNT(*)` vs `COUNT(DISTINCT ...)`），非纯单测 |
| 105 | `<S>/account-server-active-user-cardtype-index-migration.sql:24-26` | 因 `mcp_database_qd` 验证不过（`McpSqlValidationException`）就去改索引形状，而不是包一层 PL/SQL | 不可（工具链知识，只能靠文档） |
| 106 | `<S>/account-server-hce-data-migration.sql:1` | 认为「执行过 card-type 那个脚本就等于列都加齐了」而跳过本脚本 | 部分：可写库回查断言列存在 |
| 107 | `<F>/java/.../controller/EmployeeCardController.java`（类上无 `@RequestMapping`） | 为「整齐」给 fep-acc 的两个端点加类级路径前缀（ACC 侧立即全部 404） | 可：反射断言本类无类级 `@RequestMapping` + 两条路径字面量 |
| 108 | `<F>/java/.../controller/EmployeeCardController.java:31-33` | 按 AGENTS.md §4 的 `x-www-form-urlencoded` 造 ACC 请求（本链路是 `multipart/form-data`，不匹配即 415） | 可：反射断言两个端点的 `consumes` 值 |
| 109 | `<F>/resources/application.properties:9-10` | 把 `service.account.url` 写成 `127.0.0.1`（K8s 里等于打到自己）或删掉该键 | 部分：可写配置扫描断言，**无法覆盖集群 env 覆盖值** |
| 110 | `<F>/java/.../service/`、`service/impl/` 两个空目录（`.gitkeep`） | 在纯转发前置里写业务逻辑（这两个目录一有内容就说明边界破了） | 可：结构断言 `fep-acc-server` 无 `@Service` Bean |

### 覆盖率自评

**计数口径**（承阶段二，本轮新增第三个口径，三者 NEVER 混用）：
①`--` / `#` 开头行；②`COMMENT ON` 语句条数；③Java / XML 块注释按每行计。

| 项 | 数 | 说明 |
|---|---|---|
| 本轮覆盖文件数 | **11** | `<S>/*.sql` 7 个 + `<P>` 1 个（只 `:4~6`）+ `<F>` 3 个 `.java` 与 1 个 `.properties`（后者计入 `<F>`，故 7+1+3=11） |
| `COMMENT ON` 条数 | **107** | schema 98 + card-type 2 + companion-flag 1 + hce-data 1 + pay-account-id 1 + phone-sync 4 = 107，**逐文件 `grep -c` 实测** |
| SQL `--` 行 | **39** | active-user-cardtype 26 + phone-sync 6 + pay-account-id 5 + companion-flag 1 + hce-data 1 + schema 0 + card-type 0 = 39，与阶段二一致 |
| `fep-acc-server` 注释行 | **30** | 3 个 `.java` 共 28 行（3 + 10 + 15）+ `application.properties` 2 行（`:9-10`）= 30 |
| `<P>` 乱码行 | **3** | `:4`、`:6` 为损毁行；`:5` 是被注释掉的真值行（阶段二已记，本轮只补位置关系） |
| 本轮落笔条目 | **63** | §一 7 条 + §二 4 条（含 4 个子项）+ §三 5 条 + §四 4 条 + §五 4 条 + §六 2 条（含 6 个子项）+ §七 4 条（含 10 个子项）+ §八 5 条 + 矛盾 5 条 + 墓碑 16 条 |
| 判为零决策信息 | **`COMMENT ON` 里 62 条 / 30 行 fep-acc 里 3 行** | 前者是字段名直译（如「主键」「手机号」「创建时间」），后者是 `FepAccServer` 的类注释；**只计数、不逐条抄** |

**覆盖判断**：`<S>` 目录与 `<F>` 模块**本轮已 100% 逐行读过**（7 个 SQL 文件 484 行全文、
`fep-acc-server` 4 个文件全文），`<P>` **只覆盖 `:4~6`**（其余行阶段二已覆盖）。

**仍未覆盖 / 本轮刻意不做**：

1. **`account-server` 与 `fep-acc-server` 都没有 `src/test`**（本轮 `find` 实测：两模块的 `src` 下只有 `main`）。
   阶段二遗留清单第 1 项「`src/test` 全部未读」**因此已闭合，但闭合方式是「目录不存在」，不是「读完了」** ——
   阶段一引用过的 `UserItpRegInfoMapperSqlTest` / `PayChannelInternalContractTest` /
   `delYnLiteralsStayInsideTheTwoFragments` **在仓库里都不存在**，那些名字是**建议写的测试**、不是已有测试。
   **NEVER 再把它们当成现有测试引用。**
2. **两个模块的 `pom.xml` 注释仍未抽**（承阶段二第 2 项）。已知事实在 AGENTS.md §7：
   `account-server` 的 `build-image-remote` 绑 `package`（`account-server/pom.xml:136`）、
   `fep-acc-server` 的 jkube goals 在 `fep-acc-server/pom.xml:78~80` **被整段 XML 注释掉**
   —— 后者本身就是「注释里藏着部署行为」的活样例，值得单独一轮。
3. **`log4j2-*.xml` 等非 mapper 资源仍未纳入**（承阶段二第 3 项）。
4. **`<P>:4`/`:6` 的原意未追** —— 可用 `svn log <P>` + `svn cat -r <n> <P>` 找到损毁前的版本，本轮未做。
5. **`COMMENT ON` 与库内实际注释未做逐条比对** —— 本轮只读仓库文件，**没有查
   `USER_TAB_COMMENTS` / `USER_COL_COMMENTS` 核对 `AFCITPDB` 里实际存的是哪一版**。
   矛盾第 4 条（表注释被覆盖）与第 2 条（列不存在）都**只是按文件顺序推断的后果，未在库上验证**。
   下一轮 MUST 补这次回查，判据：`SELECT * FROM USER_TAB_COMMENTS WHERE TABLE_NAME = 'USER_ACC_TICKETNO'`
   若返回「账户卡号池表」即证实覆盖；`USER_COL_COMMENTS` 里若无 `THIRD_USER_ID_SUFFIX` 行即证实 `:248` 从未生效。


















