# 支付宝小程序 取行业数据 `/channel/requestIndustryData` 端到端联调（2026-09-17）

**结论：先开户再取行业数据全链路通过，6 个用例全部符合预期。** 第一步开户后落库即 `CHANNEL='07'` + 卡号，第二步 `cardData` 组装并签名成功。**但联调后复核源码发现两个待裁决问题**：签名段长度两侧契约不一致（`acc-security-server` 给 8 位、`industry-data-server` 按 16 位收并左补零），以及渠道位没有白名单、线上已有非枚举值 `17` 在流通。均见 §六。

## 一、被测环境（现查实测，NEVER 引用本节，MUST 每次重查）

| 项 | 实测值 |
|---|---|
| 支付宝网关 | `fep-alipay`（`itp/fep-alipay:1.0.61`），NodePort **30020** |
| 支付宝账户 | `alipay-account-server`（`itp/alipay-account:1.0.15`），NodePort **30021** |
| 乘车码 | `ticket-server:2.1.86`，NodePort **30014** |
| 行业数据 | `industry-data-server:2.0.17`，NodePort **30018** |
| 安全服务 | `acc-security-server:2.0.14`，NodePort **30012** |
| 探活 | 30020 / 30021 / 30014 / 30018 / 30012 全 `http=200` |
| 目标库 | `172.20.222.3:1521 / AFCITPDB`（`mcp_database_qd`） |

`industry-data-server` Deployment env：`industry.issue-channel-code=01`、`industry.ticket-type=0441`、`service.security.url=http://172.20.211.23:30012`。

## 二、测试账号

| 项 | 值 |
|---|---|
| `THIRD_USER_ID` | `0700009921`（本次新开，16:41:09） |
| `CARD_ID` | `0426090949000205` |
| `MSISDN` | `15064259921` |

## 三、第一步：开户后落库断言（本次测试的重点要求）

`ALIPAY_USER_INFO`（`0700009921`）经 `mcp_database_qd` 只读回查：

- `CHANNEL = '07'` ✅
- `CARD_ID = '0426090949000205'` ✅
- `CARD_TYPE = '02'`、`CARD_ISSUE_CODE = '0007'`、`STATUS = 'ACTIVE'`、`DELETE_FLAG = '0'`

`CHANNEL` 的写入点是 `AlipayAccountServiceImpl.java:340`，取 `IssueChannelCodeEnum.ALIPAY.getCode()`（= `"07"`），读回时（`:169`）不做任何变换。

> **`'07'` 没有白名单校验，两处 `8001` 判断实质是同一道空值检查。** `AlipayApplicationServiceImpl:97` 判 `getChannel() == null`，`:111` 判 `SignChannelUtils.resolve()` 返回空——而 `SignChannelUtils.resolve()`（`model/.../SignChannelUtils.java:14-23`）**只在入参 `null` 或全空白时返 `null`**，`null` 那一支已被 `:97` 拦掉，因此 `:111` 只多拦「纯空白字符串」一种情况。**不是两道校验。**
>
> 唯一的实质防线是 `IndustryCardDataServiceImpl.java:33` 的 `HEX_BODY = [0-9A-F]{64}`：渠道段是非十六进制字符时整个码体校验失败、返 `8001`（`:109-115`）。所以**十六进制值一律放行、非十六进制被拦**，`CHANNEL='88'` / `'AB'` / `'FF'` 都能取到行业数据，只是渠道段跟着变。
>
> **白名单能力存在但刻意不用于准入**：`IssueChannelCodeEnum` 只有 `01 正常渠道` / `07 支付宝` 两个值，`fromCode()` 在 `IndustryCardDataServiceImpl:236` 被调用，**只用于决定超长截断时要不要打 WARN**，不影响返回值。
>
> **线上已有非白名单值在流通（实证）**：2026-09-17 16:48:29 `industry-data-server` 日志里，`cardId=0426090942000006` 那笔的码体 `signChannelCode` 段是 **`17`**，不在枚举内，签发成功。该笔来自 APP 链路（`issueChannelCode=01`）；`17` 的来源未追查。
>
> 要靠 `CHANNEL` 做渠道隔离 **MUST 另加白名单，NEVER 假定现在拦得住**。

## 四、逐跳链路（日志实证，`fep-alipay` + `industry-data-server`）

`POST http://172.20.211.23:30020/channel/requestIndustryData`（form-urlencoded + `bizData`），入口 `FepAlipayTripController.requestIndustryData:104`。

1. `fep-alipay` → `GET http://172.20.211.23:30021/channel/queryUserInfo?thirdUserId=0700009921`
   返回 `{"cardId":"0426090949000205","cardType":"02","channel":"07","phone":"15064259921","thirdPayId":null,"reqContractNo":null}`
2. `fep-alipay` → ticket-server `queryQrCodeStatus`
   返回 `{"retCode":"0000","status":"03","gateInStation":"FFFF","gateInTime":"00000000000000","lastTxnStation":"FFFF","lastTxnTime":"00000000000000","txnSeq":"0"}`（新用户无乘车记录，全是默认值）
3. `fep-alipay` → `POST http://172.20.211.23:30018/ci/industry/buildCardData`
   请求 `IndustryCardDataBuildReqDTO{..., txnSeq='1', issueChannelCode='07', signChannelCode='07'}` —— **`txnSeq` 由 0 变 1 是 `fep-alipay` 自增的**，不是 ticket-server 给的
4. `industry-data-server` 组装 64 位码体：`unsignedIndustryData=29B94DC103FFFF323ED4B6323F0CF60426090949000205044100000001070701`
5. `industry-data-server` → `acc-security-server` HSM 取签名，追加到码体尾部

另有第二个同 URL 入口 `FepAlipayTripMemberContractController:36`（前缀 `/memberContract/channel`），实测与主流程 `cardData` 完全一致。

## 五、用例与实测结果（全部 HTTP 200）

- **A 主流程** → `{"retCode":"0000","retMsg":"成功","cardData":"29B94DC103FFFF323ED4B6323F0CF6042609094900020504410000000107070100000000330D843C","signType":"00","sign":""}` ✅
- **B 未开户用户** → `9999 用户未开户` ✅
- **C 卡号不匹配** → `8002 卡号不匹配` ✅（`equals` 裸比较，**无 trim / 大小写归一**）
- **D 缺 `cardId`** → `1001 cardId不能为空` ✅
- **E `bizData` 整个缺失** → `8001 无效的参数`，`cardData` / `signType` / `sign` 均 `null` ✅
- **F `/memberContract/channel/requestIndustryData`** → 与 A 的 `cardData` 逐字一致 ✅

`signType` 是硬编码 `"00"`、`sign` 恒为空串（响应 DTO 只有 `cardData` / `signType` / `sign` 三个字段），这是现行设计。

## 六、cardData 分段解码与两个待裁决问题

实测 80 位十六进制 = 64 位码体（11 段）+ 16 位签名段，段布局见 `IndustryCardDataServiceImpl.java:47-63`，`[0-9A-F]{64}` 校验在 `:33` / `:109`：

- `29B94DC1` 第三方用户号（`700009921` 的十六进制）
- `03` 票卡状态
- `FFFF` 上次交易车站
- `323ED4B6` 处理日期
- `323F0CF6` 时间戳
- `0426090949000205` 票卡逻辑卡号
- `0441` 票卡类型
- `00000001` 交易序号
- `07` 发行渠道
- `07` 签约渠道
- `01` 卡版本
- `00000000330D843C` 签名段（**前 8 位是补的零，`330D843C` 才是真 TAC，见下面事实 2**）

**事实 1：`issueChannelCode` 段是 `07`，不是 `industry-data-server` env 里的 `01`。** 请求里带的值（硬编码在 `AlipayApplicationServiceImpl:199`）优先，`industry.issue-channel-code=01` 这个配置对本链路**等于死配置**。排查「渠道段为什么不对」MUST 从 `fep-alipay` 的请求参数看，**NEVER 先怀疑 industry-data 的 env**。

**事实 2：`cardData` 实测 80 位，但其中第 65~72 位恒为 `00000000`、是补出来的零 —— 签名真实内容只有 8 位。这是一处待甲方裁决的契约冲突。**

两侧对同一字段的长度契约不一致，一方必错：

- **安全侧给 8 位**：`acc-security-server` 的 `ItpServiceImpl.java:174` 与 `ItpRequestService.java:93` 都是 `result.setIndustryDataSign(tac.substring(0, 8))` —— TAC 截前 8 个十六进制字符 = **4 字节**。
- **组装侧按 16 位收**：`IndustryCardDataServiceImpl:131` 写 `normalizeHex(signResp.getIndustryDataSign(), 16, "")`，而该方法（`:242-254`）不足即**左补零**、超长即取右侧，**两种情况都不报错**。于是 8 位签名被撑成 16 位。

实证（`industry-data-server` 日志，2026-09-17 16:48:29，两笔）：

```
response={"industryDataSign":"F91BFF86","retCode":"200","retMsg":"SUCCESS"}
response={"industryDataSign":"A82599E7","retCode":"200","retMsg":"SUCCESS"}
```

均为 8 位。因此本次主流程 `cardData` 尾部 `00000000330D843C` 中，**`330D843C` 才是真 TAC，前 8 位是 `normalizeHex` 补的零**。

`BODY_LENGTH = 64`（`:36`）本身有 static 块（`:65-70`）在类加载时断言段长之和，码体那 64 位是可靠的；**只有签名段这 16 位是无依据的字面量**。而其余每段都有 `:140-143` 的段长告警，**唯独签名段没有任何长度校验**。

**待办：向甲方 / 闸机侧确认 `cardData` 定长是 72 还是 80。** 若闸机按 72 解析，则现在发出的码全部多 8 位；若按 80 且约定高位补零，则现状正确、只是浪费 4 字节。**属签名链路，MUST 人工复核后再改，NEVER 擅自调整那个 16。**

> 此前本节记的「80 位才对、『8 位签名』里的 8 指字节」**是错的，NEVER 回退** —— 8 就是 8 个十六进制字符，已由上面两条硬证据推翻。

## 七、复核 SQL

```sql
SELECT THIRD_USER_ID, CHANNEL, CARD_ID, CARD_TYPE, CARD_ISSUE_CODE, STATUS, DELETE_FLAG
  FROM ALIPAY_USER_INFO WHERE THIRD_USER_ID = '0700009921';
```

## 八、待闭合与未覆盖

**待闭合（需甲方 / 人工裁决，均不自行改码）：**

- `cardData` 定长是 72 还是 80 —— 签名段长度两侧契约不一致，见 §六事实 2
- 渠道位是否需要白名单 —— 线上已有非枚举值 `17`，见 §三

**未覆盖：**

- `CHANNEL` 非 `'07'`（如 `88`）时取行业数据的实际表现 —— 按代码推断能通，未实测
- `17` 那个渠道值的来源（哪个用户、怎么写进去的）
- 有真实乘车记录（`status` / `gateInStation` / `txnSeq` 非默认值）时的码体
- HSM 不可用时的降级行为
- 并发同一用户取行业数据（`txnSeq` 自增点在 `fep-alipay`，无锁）
