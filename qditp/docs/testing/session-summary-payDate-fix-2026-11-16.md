# 会话总结 - payDate 格式修复

**时间**: 2026-11-16
**问题**: 测试环境 `/ci/app/requestPayResult` 接口返回的 `payDate` 格式为 `yyyy-MM-dd HH:mm:ss`，不符合接口文档要求的 `yyyyMMddHHmmss`

---

## 最终修复

### 修改文件
- `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/service/F2fAppOrderService.java`
  - `PAID_TMS_FORMATTER` 格式从 `"yyyy-MM-dd HH:mm:ss"` 改为 `"yyyyMMddHHmmss"`

### 版本变更
- face-pay-server: 1.0.94 → 1.0.96

### 调用链
```
APP → fep-app-server(/ci/app/requestPayResult) → face-pay-server:1.0.96
```

---

## 误判的变更（已撤回）

以下变更与本问题无关，已恢复到修复前状态：

1. **model/DailyTicketPayQueryResult.java**
   - 原改动: `payDate` 类型从 `Date` 改为 `String`
   - 撤回原因: face-pay-server 不使用该类，返回的是 `JSONObject`
   - daily-ticket-server 的日票接口路径是 `/ci/app/dailyTicket/payment/requestPayResult`，不是用户反馈的 `/ci/app/requestPayResult`

2. **daily-ticket-server 版本 bump** (1.0.74→1.0.77)
   - 撤回原因: 与本次问题无关

3. **fep-app-server 版本 bump** (2.1.6→2.1.9)
   - 撤回原因: 网关层无需改动

4. **文档更新**
   - 撤回原因: 基于错误判断的文档修改

---

## 经验教训

1. **接口路径要精确匹配**: 用户反馈的路径是 `/ci/app/requestPayResult`，这是 CollectPayController（转发到 face-pay-server），不是 AppDailyTicketController（转发到 daily-ticket-server）

2. **确认调用链再修改**: 应该先通过 VL 日志确认实际的调用链，而不是假设

3. **最小化变更**: 只修改真正有问题的代码，避免引入不必要的版本 bump
