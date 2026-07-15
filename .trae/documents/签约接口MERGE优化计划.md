# 签约接口 MERGE 优化计划

## 摘要

用户提供了签约相关表结构，要求基于表结构对请求签约接口做深度优化。经分析，核心问题在于：
1. **MERGE 匹配条件与唯一约束不一致**：MERGE 按 `(REQUEST_SIGN_SEQ, PAYMENT_VENDOR)` 匹配，但唯一约束是 `(THIRD_USER_ID, PAYMENT_VENDOR)`
2. **normalizeVendor 未做编码映射**：`0802` 等编码未映射为 `03`，导致同一用户同一支付渠道的重复记录
3. **日志表主键设计问题**：`APP_PAY_SIGN_LOG` 以 `REQUEST_SIGN_SEQ` 为主键，同一流水号日志被覆盖

## 当前状态分析

### 表结构关键信息

**APP_PAY_SIGN_INFO（免密签约主表）**
- 主键逻辑：`REQUEST_SIGN_SEQ`（业务主键，非数据库主键）
- 唯一约束：`UK_APP_PAY_SIGN_INFO_USER_VENDOR` (THIRD_USER_ID, PAYMENT_VENDOR)
- 语义：一个用户 + 一个支付渠道 = 一条记录

**APP_PAY_SIGN_LOG（操作日志表）**
- 主键：`PK_APP_PAY_SIGN_LOG` (REQUEST_SIGN_SEQ)
- 问题：同一流水号多次操作，日志会被覆盖

### 现有 MERGE 语句

```sql
MERGE INTO APP_PAY_SIGN_INFO t
USING (
    SELECT #{requestSignSeq} AS REQUEST_SIGN_SEQ, ...
) s
ON (t.REQUEST_SIGN_SEQ = s.REQUEST_SIGN_SEQ AND t.PAYMENT_VENDOR = s.PAYMENT_VENDOR)
WHEN MATCHED THEN UPDATE SET ...
WHEN NOT MATCHED THEN INSERT ...
```

### 冲突场景

| 场景 | 结果 |
|------|------|
| 同一 requestSignSeq + 同一 paymentVendor | MERGE UPDATE，正常 |
| 同一 requestSignSeq + 不同 paymentVendor | MERGE INSERT，正常（但业务上不应发生） |
| 不同 requestSignSeq + 同一 THIRD_USER_ID + 同一 paymentVendor | MERGE INSERT，触发唯一约束冲突 |

## 拟议变更

### 变更 1: 修改 MERGE 匹配条件，与唯一约束对齐

**文件:** [PaySignInfoMapper.xml](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/resources/mapper/PaySignInfoMapper.xml)

**变更内容:**
```xml
<!-- 修改前 -->
ON (t.REQUEST_SIGN_SEQ = s.REQUEST_SIGN_SEQ AND t.PAYMENT_VENDOR = s.PAYMENT_VENDOR)

<!-- 修改后 -->
ON (t.THIRD_USER_ID = s.THIRD_USER_ID AND t.PAYMENT_VENDOR = s.PAYMENT_VENDOR)
```

**原因:** 与唯一约束 `UK_APP_PAY_SIGN_INFO_USER_VENDOR` (THIRD_USER_ID, PAYMENT_VENDOR) 对齐，确保 MERGE 的语义与表设计一致。

**影响:** 
- 同一用户同一支付渠道，无论 requestSignSeq 是否变化，都会走 UPDATE 分支
- 避免不同 requestSignSeq 导致的唯一约束冲突
- `REQUEST_SIGN_SEQ` 字段在 UPDATE 时也会被更新（保持为最新流水号）

### 变更 2: 修复 normalizeVendor 编码映射

**文件:** [PaySignServiceImpl.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java)

**变更内容:**
```java
private String normalizeVendor(String payChannelCode) {
    if (!StringUtils.hasText(payChannelCode)) {
        return null;
    }
    String code = payChannelCode.trim();
    if (code.length() >= 2) {
        String prefix = code.substring(0, 2);
        if ("08".equals(prefix)) {
            String suffix = code.length() >= 3 ? code.substring(2, 3) : "";
            if ("2".equals(suffix)) {
                return "03";
            }
            if ("3".equals(suffix)) {
                return "04";
            }
        }
        if ("03".equals(prefix) || "04".equals(prefix)) {
            return prefix;
        }
    }
    return code;
}
```

**原因:** 确保 `0802` 始终映射为 `03`，避免同一用户因编码不一致产生重复记录。

### 变更 3: 修改日志表主键，支持多笔记录

**文件:** [PaySignLogMapper.xml](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/resources/mapper/PaySignLogMapper.xml)（需确认是否存在）

**方案 A（推荐）：** 将主键改为复合主键 `(REQUEST_SIGN_SEQ, OPERATION_TYPE, CREATE_TMS)`
**方案 B：** 添加自增 ID 列作为主键

**原因:** 当前同一 `REQUEST_SIGN_SEQ` 的多次操作（如多次查询、回调）会覆盖历史日志。

### 变更 4: 升级 pay-sign-server 版本号

**文件:** [pay-sign-server/pom.xml](file:///d:/workspace/zr/qditp/pay-sign-server/pom.xml)

**变更内容:** 版本号升级（当前 `1.4` → `1.5`）。

## 假设与决策

| 决策 | 说明 |
|------|------|
| MERGE 按用户+渠道匹配 | 与表设计的唯一约束对齐，requestSignSeq 作为普通字段更新 |
| 0802 → 03, 0803 → 04 | 与 fep-app-server 编码映射规则保持一致 |
| 日志表改复合主键 | 保留完整操作历史，便于问题排查 |

## 验证步骤

1. 修改后编译 pay-sign-server
2. 第一次调用 requestSignInfo，传入 payChannelCode=0802，验证入库成功
3. 第二次调用 requestSignInfo（相同用户、相同渠道、不同 requestSignSeq），验证 MERGE 走 UPDATE 分支，无冲突
4. 检查数据库中记录的 REQUEST_SIGN_SEQ 为最新值
5. 检查日志表中同一 requestSignSeq 的多笔记录是否都保留

## 影响范围

| 服务 | 影响 | 版本升级 |
|------|------|----------|
| pay-sign-server | PaySignInfoMapper.xml MERGE条件修改；normalizeVendor添加映射；日志表主键修改 | 1.4 → 1.5 |
| 其他服务 | 无影响 | 不升级 |
