# IT-089 cumulativeType 累计类型判断逻辑修正

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中新增条目。
> 状态：**已修复并部署测试环境**（ticket-server 2.2.17，2026-11-20）。
> 发现时间：2026-11-20
> 发现渠道：VL 日志排查（免密二维码主码使用钱包方式乘车，`cumulativeType` 错误输出 `"03"`）

## 1. 现象

测试环境 VL 日志显示一笔免密二维码主码交易（`paymentVendor=0B`，支付宝钱包支付）：

```json
{
  "cumulativeType": "03",
  "companionFlag": "N",
  "cardType": "0441"
}
```

`cumulativeType="03"` 表示「不参与累计的票（NFC、同行票等）」，但该交易是普通乘客刷二维码乘车，应为 `"01"`（普通闸机出站）。

## 2. 根因分析

`AlipayIndustryDetailAssembler.resolveCumulativeType()` 原逻辑过于宽泛：

```java
// 旧逻辑：只要 companionFlag 非空就返回 "03"
private String resolveCumulativeType(NotifyVerifyResultReqDTO request) {
    if (StringUtils.hasText(request.getCompanionFlag())) {
        return "03";
    }
    return "03".equals(request.getTrxType()) ? "02" : "01";
}
```

**问题**：
1. `GateCardTypeEnricher` 从 account 侧查询签约信息后，会用 `regInfo.getCompanionFlag()` 覆盖 request 的 `companionFlag`
2. 如果签约信息里该字段存了空字符串以外的值（如历史数据异常、或签约时未正确初始化），就会导致误判
3. 闸机设备传来的 `companionFlag` 通常是 `"N"`（普通票），但签约信息可能覆盖为其他值

## 3. 修复方案

根据 ACLC 二期支付 API 接口文档 V1.2：

| cumulativeType | 含义 |
|----------------|------|
| `01` | 普通闸机出站 |
| `02` | 闸机超时出站 |
| `03` | 其他不参与累计的票（NFC、同行票等） |

**修复后的逻辑**：
```java
private String resolveCumulativeType(NotifyVerifyResultReqDTO request) {
    String flag = request.getCompanionFlag();
    if (flag != null) {
        String trimmed = flag.trim();
        if ("Y".equalsIgnoreCase(trimmed) || "C".equalsIgnoreCase(trimmed)) {
            return "03";
        }
    }
    if (CardTypeCodeEnum.isHceCard(request.getCardType())) {
        return "03";
    }
    return "03".equals(request.getTrxType()) ? "02" : "01";
}
```

**关键变更**：
1. `companionFlag` 只判断 `"Y"`（同行票）和 `"C"`（第三方票），其他值（包括 `"N"` 和异常值）按普通票处理
2. 新增 NFC/HCE 卡判断（`cardType=0442/0443`），这些卡也应返回 `"03"`

## 4. 影响范围

- **文件**：`ticket-server/src/main/java/com/chinasofti/huateng/ticket/gate/AlipayIndustryDetailAssembler.java`
- **测试**：`ticket-server/src/test/java/com/chinasofti/huateng/ticket/gate/AlipayIndustryDetailAssemblerTest.java`
- **版本**：ticket-server 2.2.16 → 2.2.17

## 5. 测试覆盖

新增 4 个测试用例：
- `normalTicketWithNFlag()` - 验证 `companionFlag=N` 时返回 `"01"`
- `abnormalCompanionFlagTreatedAsNormal()` - 验证异常值（如 `"X"`）按普通票处理
- `nfcCardShouldReturnCumulativeType03()` - 验证 HCE 卡（`0442/0443`）返回 `"03"`
- `qrCodeCardShouldReturnCumulativeType01()` - 验证二维码卡（`0441`）返回 `"01"`

所有 14 个测试用例通过。

## 6. 部署记录

- **测试环境**：2026-11-20 14:57 完成部署（ticket-server 2.2.17）
- **SVN**：连接超时，未提交（需手动补提交）
- **Git**：`d34d6e27e4`，已推送到 origin/main

## 7. 回滚方案

如需回滚：
```bash
./itp-ops.sh --env test cloudos deploy ticket-server 2.2.16 --yes
```
