# 对外入向接口清单（自动化测试 / 压力测试用）

> **生成时间**：2026-09-22
> **代码基线**：`main` 工作副本当前状态（SVN 工作副本根 `/Users/tuanjie/workspace/company/chinasofti/qd/qditp`）
> **网关路由基线**：2026-09-22 现查 `kubectl get vs fep-app-vr -n itp -o yaml`，`generation: 20`

## 0. 这份清单的定位与失效条件

本清单**只描述端点契约**（URL、报文骨架、bizData 字段、响应字段），**不含实现率 / 进度评估**。
`AGENTS.md` §5.3 禁止在仓库内维护「需求接口清单 / 实现进度报告 / 接口提取结果」这类汇总文件，理由是那批文件会给出互相矛盾的实现率、且「实现位置」列大面积误指。本清单是**测试交付物**而非进度报告，但同样会过期，因此：

- **判断「某接口是否存在」仍 MUST 直接看 Controller 的 `@PostMapping`，NEVER 以本清单为准。**
- **判断「对外 URL 前缀落到哪个服务」MUST 现查 `fep-app-vr` VirtualService + `fep-app` Deployment 的 `service.*.url` env**，NEVER 引用本文件 §1 的表格。
- 本清单的用途是「一次性生成测试用例与压测脚本的输入」。用完后若与代码不一致，**改代码那边、废弃本清单，NEVER 反过来改代码去对齐本清单**。

## 1. 入向链路与 8 条网关前缀（2026-09-22 实测）

```
公网 58.56.166.170:48000
  → 节点 172.20.211.200 (k8s02-gateway-866a2，入向网关节点)
    → itp-gateway (ns itp-gateway，istio，svc :50908)
      → ns itp 的 VirtualService fep-app-vr (gateway: accountserver-gw, hosts: *)
        → 各业务 Service
```

| 网关前缀 | rewrite.uri | 目标 Service | 端口 | 归属模块 | 清单文件 |
|---|---|---|---|---|---|
| `/fep-app/` | `/` | `fep-app-hr32k-svc` | 9101 | fep-app-server | [01](01-fep-app-account-ticket.md) / [02](02-fep-app-pay-dailyticket.md) |
| `/fep-dev/` | `/` | `fep-dev-server-748lq-svc` | 30009 | fep-dev-server | [03](03-fep-dev-acc.md) |
| `/fep-acc/` | `/` | `fep-acc-wracu-svc` | 30030 | fep-acc-server | [03](03-fep-dev-acc.md) |
| `/fep-alipay/` | `/` | `fep-alipay-rec8g-svc` | 30020 | fep-alipay-server | [04](04-fep-alipay.md) |
| `/itptvm/` | `/itptvm/`（**保留前缀**） | `face-pay-server-svc` | 30025 | face-pay-server | [05a](05a-face-pay-tvm.md) / [05b](05b-face-pay-bom.md) |
| `/itpbom/` | `/itpbom/`（**保留前缀**） | `face-pay-server-svc` | 30025 | face-pay-server | [05a](05a-face-pay-tvm.md) / [05b](05b-face-pay-bom.md) |
| `/itpagm/` | `/` | `fep-dev-server-748lq-svc` | 30009 | fep-dev-server | [03](03-fep-dev-acc.md) |
| `/para-server/` | `/` | `para-server-pufrl-svc` | 30026 | para-server | [07](07-para-server.md) |

两个 rewrite 陷阱，写压测脚本时 **MUST 注意**：

1. **`/itptvm/` 与 `/itpbom/` 的 rewrite 保留前缀**，其余 6 条 rewrite 成 `/`。因此 TVM/BOM 的容器内路径**本身就带 `/itptvm/` `/itpbom/`**（`@RequestMapping({"/itptvm/ci/tvm", "/itpbom/ci/tvm"})`），公网 URL 与容器内路径**一致**；而 `/fep-app/ci/app/xxx` 到容器里是 `/ci/app/xxx`，**前缀被吃掉**。
2. **`/ci/app/**` 在 `fep-app-vr` 里没有独立 route**。face-pay 的 `/ci/app/**`（`AppOrderController`）**公网不可直达**，只能由 `fep-app` 经 `service.collectPay.url` 转发进来；face-pay 里凡是需要被外部直连的回调，都特意做成 `/itpbom/ci/bom/...` 前缀形态（见 [06](06-face-pay-app.md) 的 `RefundNoticeController`）。

### 压测入口地址选择

- **走网关（含真实链路 + istio 开销）**：`http://58.56.166.170:48000/<前缀>/<path>`
- **直连节点 NodePort（绕过网关，用于定位瓶颈在网关还是应用）**：从 `k8s-master` 打 `http://172.20.211.23:<NodePort>/<容器内路径>`。
  **NEVER 用 `<svc>.itp.svc:<port>`** —— `k8s-master` 是宿主节点、不在 Pod 网络、无 CoreDNS，一律 `curl` 返回 `http=000`（不是服务挂了）。
- NodePort 号现查 `kubectl get svc -n itp`，冒号右边那个。

## 2. 公共报文骨架（form-urlencoded 端点通用）

绝大多数对外端点是 `Content-Type: application/x-www-form-urlencoded`，`POST`，报文骨架只有两个类：

- `com.chinasofti.huateng.model.app.ItpCommonRequest<T>`
- `com.chinasofti.huateng.model.app.ItpCommonFormRequest extends ItpCommonRequest<String>`（form 入向用）

face-pay 的设备域用的是**模块内独立的** `com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest`（字段集不同，多一个 `providerId`，见 [05a](05a-face-pay-tvm.md) §0）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `sign` | String | 签名。**`fep-app-server` 不验签、原样透传**；`toString` 已恒定脱敏 |
| `charset` | String | 字符集，通常 `UTF-8` |
| `format` | String | 报文格式，通常 `JSON` |
| `timestamp` | String | 时间戳 |
| `deviceId` | String | 设备号 / 渠道号 |
| `signType` | String | 签名类型。`00` 在 `AccountRequestVerifier` 链路上等于**免签** |
| `bizData` | String | 业务参数 JSON 字符串（部分链路是 Base64） |

### 验签现状（压测必须知道，否则造不出能过的报文）

**入向验签不统一，NEVER 假定都是 SHA256WithRSA**：

- `fep-app-server` / `fep-dev-server` / `fep-acc-server` / `fep-alipay-server` **四个接入层一律不验签**，`sign` 原样透传。
- 真正校验发生在下游：`AccountRequestVerifier`（摘要式，`signType=00` 免签）、`ItpRequestSignVerifier`（SHA1/MD5）、各渠道 RSA 工具类。
- SHA256WithRSA 只用于**渠道对接出向**方向。

因此压测时对多数接入层端点可以送**任意 `sign`**；只有链路会走到下游验签器的接口才需要真签名。逐接口的验签归属见各文件的「压测备注」列。

### bizData 解析规则（对外契约边界）

所有 `parseBizData(ItpCommonFormRequest, Class<T>)` 能解析的 DTO **就是对外契约，NEVER 加字段**。
Fastjson2 宽松模式会**静默丢弃目标 DTO 里不存在的字段、不报错不告警** —— 压测时送了 DTO 里没有的字段，不会报错，只是被丢掉。
`bizData` 为空或空白时按 `"{}"` 处理（不会 400）。

## 3. 响应骨架与错误码

- 多数端点返回 `CommonResult` 家族：`retCode` + `retMsg` + 业务字段，`retCode=0000` 为成功。
- **HTTP 状态码基本恒为 200，判成功 MUST 看 `retCode`，NEVER 看 HTTP code。**
- **`retCode` 是一个 UUID 时表示踩了全局异常处理器**，至少有 5 种成因：事务内 RPC 超时 / `service.*.url` 指向自身 / 路径无 handler（**404 被伪装成 200**）/ SQL 被 Druid WallFilter 拒 / 入参反序列化失败。压测中出现 UUID `retCode` **MUST 去服务端日志取那行原文**，NEVER 套用历史结论。
- face-pay 设备域有两套错误码族：TVM `2xxx`（`TvmResponses`）与 BOM `8xxx`（`BomResponses`）。**`/itpbom/ci/bom/notiTakeTicketResult` 与 `notiTakeTicketFailResult` 故意返回 TVM 的 `2xxx`**，代码里有明确禁止「修正」的注释，断言 MUST 按现状写。
- 已知残留：设备送小写 `ticketLogicNum` 时 face-pay 返 `8999`（新应用是精确等值比较、旧应用是 `UPPER()`），已按裁决只做数据归一、未改代码。压测造数 **MUST 用大写**。

## 4. 清单文件索引

- [01-fep-app-account-ticket.md](01-fep-app-account-ticket.md) — fep-app-server 27 个端点：账户 / 支付通道 / 员工卡 / 票务交易查询 / 基础参数 / 补款 / 行业数据 / 手机号
- [02-fep-app-pay-dailyticket.md](02-fep-app-pay-dailyticket.md) — fep-app-server 30 个端点：签约支付解约 / 日票计次票旅游票 / TVM-BOM 订单透传
- [03-fep-dev-acc.md](03-fep-dev-acc.md) — fep-dev-server（AGM 闸机前置，4 端点）+ fep-acc-server（ACC 员工卡通知，2 端点）
- [04-fep-alipay.md](04-fep-alipay.md) — fep-alipay-server 13 个端点（支付宝出行 / 碰一下渠道网关）
- [05a-face-pay-tvm.md](05a-face-pay-tvm.md) — face-pay-server TVM 域 15 个端点（`TvmOrderController`，双前缀 `/itptvm/ci/tvm` + `/itpbom/ci/tvm`）。**`BaseDeviceRequest` 公共骨架表在本文件 §0，其余 face-pay 文件都引用它**
- [05b-face-pay-bom.md](05b-face-pay-bom.md) — face-pay-server BOM 域 14 个端点（`BomOrderController`，双前缀 `/itpbom/ci/bom` + `/itptvm/ci/bom`）
- [06-face-pay-app.md](06-face-pay-app.md) — face-pay-server 其余 9 个 Controller / 20 个 handler，按可达性分三块：网关直达 / 经 fep-app 转发 / 内部端点
- [07-para-server.md](07-para-server.md) — para-server 35 个端点（`/para-server/` 前缀，含 `/page/**` 管理后台 22 个）

## 4.1 跨模块注意点（逐代码核实，写用例前先看）

这些是生成清单过程中发现的、**会让测试用例或压测结论直接出错**的事实：

- **Controller 类名 / 配置键不代表真实下游**。已核实三处：`fep-app` 的 IF8A-05 / IF8A-34 / IF8A-41 打的是 **trans-query-server**（不是 ticket-server，而同名 URL 在 ticket-server 侧逐字存在、并存未替换）；IF8A-02 打 **key-server**（不是 account-server）；IF8A-26 打 **facePayClient**（不是 gateTxnPayClient，尽管类名叫 `GateTxnPayController`）；IF8A-03 / IF8D-03 打 **ticket-server**（尽管 `service.industryData.url` 这个键确实配着，但在这条链路上没有读取方）。**看服务端指标判断「压到了谁」MUST 按实测链路，NEVER 按 Controller 类名或配置键存在性推断。**
- **全部对外 DTO 零 Bean Validation**。七份清单逐个 DTO 读过，`@NotNull` / `@NotBlank` / `@Valid` 在对外契约上**一处都没有**。因此**送空值不会返 400**，只会走到业务层或直接 NPE 被全局异常处理器包成 UUID `retCode`。真正的必填来自各 Controller / service 里的显式 `isBlank` 判断，逐接口的判空行号与对应错误码已写在各文件的端点节里。**NEVER 凭字段名或规格文档推断必填性。**
- **响应码族 MUST 按代码里用的 `*Responses` 类判定，NEVER 按 URL 前缀推断**。face-pay 里两个方向的反例都存在：`/itpbom/ci/bom/notiTakeTicketResult` 挂 BOM 前缀却返 TVM `2xxx`，而 `requestGetPayResult` 共用了 TVM 的 URL 名却坚持返 BOM `8xxx`（后者 javadoc 里有明确禁令）。
- **8 个 fep-app 响应 DTO 不继承 `CommonResult`**（`RequestTransListResult`、`RequestTransDetailResult`、`RequestTransStatisticsResult`、`RequestExcessFareResult`、`QueryBlackListResult`、`RequestUserAccInfoResult`、`UserCancelResult`、`SupplementOrderRespDTO`），各自声明同名的 `retCode` / `retMsg`。用统一基类反序列化全部响应的自动化框架会在这 8 个上踩坑。
- **同名字段类型不一致三处、日期格式两套**：`orderExpType`（一处 Integer 一处 String）、分页三字段（请求 Integer / 响应 String）、补款金额（请求 `String price` / 响应 `Long totalAmount`）；IF8A-05 用 `yyyy-MM-dd`、IF8A-41 用 `yyyyMMdd`。
- **`fep-app-server` 缺 `service.alipayAccount.url` 配置键**，而 IF8A-76 换手机号的支付宝分支在用 `AlipayAccountClient`；叠加该链路 `catch(Exception) → return false` + Controller 把 boolean 翻成 `9999`，结果是「支付宝用户改不了手机号」**完全静默**。测这条要盯日志，不能只看 `retCode`。
- **face-pay 的 `BaseDeviceRequest.toString()` 完全不输出 `sign`**（连脱敏占位都没有），与 `ItpCommonFormRequest` 的「恒定脱敏」不是一回事。排查设备域报文时别指望从日志里看到 `sign` 字段的存在与否。
- **face-pay 设备域四个 service 类刻意不带 `@Transactional`**，中途失败不回滚已落的行。压测后清理造数 MUST 按表逐个核对，不能假定失败的请求没留痕。
- **face-pay `@Scheduled` 7 个、分布 6 个类、无分布式锁 ⇒ MUST 单副本**；`recon-server` 与 `web-admin` 同样 MUST 单副本。**压测时为提吞吐而扩副本会引入重复补偿，那是配置约束不是被测缺陷。**
- **`/page/**` 与 `/admin/payment/**` 这类运营侧端点在 para-server 和 fep-alipay 上是公网可达且无鉴权的**，其中含 PUT / POST / DELETE 写操作。安全测试要覆盖，但**写操作类 NEVER 在生产环境压测**。


## 5. 不在本清单内的端点

- **`/internal/**`**：服务间 RPC 与 web-admin Quartz 触发的补偿端点，**不经网关、公网不可达**。这批端点目前**没有鉴权**（`X-Recon-Token` 已于 2026-09-11 按要求整段删除，属有意的临时降级，上线前 MUST 恢复），因此**NEVER 把它们纳入对外压测**。
- **`/page/**`**：运营后台接口，分散在各业务模块。para-server 的那一批因为 `/para-server/` 前缀对外可达，列在 [07](07-para-server.md) 的附录里；其余模块的 `/page/**` 不在本次范围。
- **出向通知（IF8B 族）**：`app.notify.*-url` 那批是我方主动推给 APP 的，方向相反，不是入向端点。压测这条链路需要 APP 侧配合起 mock 服务端。
