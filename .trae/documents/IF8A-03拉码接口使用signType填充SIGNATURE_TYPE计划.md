# IF8A-03 拉码接口使用 signType 填充 SIGNATURE_TYPE 计划

## 1. 摘要

将 fep-app-server 的 IF8A-03 请求行业数据接口（`/ci/app/requestIndustryData`）中，当前由配置项 `${industry.signature-type:01}` 硬编码填充的 **SIGNATURE_TYPE** 字段，改为使用请求公共报文中的 `signType` 字段值填充。若请求未提供 `signType`，则回退到配置默认值。

## 2. 当前状态分析

### 2.1 接口入口
- **Controller**: [FepAppController.requestIndustryData](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppController.java#L115-L120)
- **Service**: [IndustryDataServiceImpl.requestIndustryData](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L55-L115)

### 2.2 当前 SIGNATURE_TYPE 填充逻辑
当前 `SIGNATURE_TYPE` 完全由配置项决定：

```java
@Value("${industry.signature-type:01}")
private String signatureType;

private String buildCardData(String unsignedIndustryData, String industryDataSign) {
    return unsignedIndustryData
            + normalizeHex(signatureType, 2, "01")
            + normalizeHex(industryDataSign, 16, "");
}
```

请求中的 `signType`（在 `CommonFormRequest` 中已存在）**未被传递到业务层**，因此无法影响 `SIGNATURE_TYPE`。

### 2.3 请求报文结构
```
POST /ci/app/requestIndustryData
Content-Type: multipart/form-data

providerId=01
charset=UTF-8
format=json
signType=00          <-- 目标值，需要透传到 SIGNATURE_TYPE
timestamp=20260519113029
bizData={"thirdUserId":"00522865","cardId":"0418061109748425","cardType":"02"}
sign=...
```

### 2.4 涉及文件清单

| 序号 | 文件路径 | 当前状态 |
|------|---------|---------|
| 1 | `qditp/model/.../RequestIndustryDataReqDTO.java` | 无 `signType` 字段 |
| 2 | `qditp/fep-app-server/.../FepAppController.java` | 未将 `signType` 传入 DTO |
| 3 | `qditp/fep-app-server/.../IndustryDataServiceImpl.java` | 使用配置值 `signatureType` |

## 3. 拟议变更

### 3.1 修改 RequestIndustryDataReqDTO（model 模块）

**文件**: `qditp/model/src/main/java/com/chinasofti/huateng/model/app/RequestIndustryDataReqDTO.java`

新增 `signType` 字段：

```java
public class RequestIndustryDataReqDTO {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String signType;  // 新增

    // ... 现有 getter/setter ...

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }
}
```

### 3.2 修改 FepAppController（fep-app-server）

**文件**: `qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppController.java`

将 `CommonFormRequest.signType` 设置到 `RequestIndustryDataReqDTO`：

```java
@PostMapping("/requestIndustryData")
public RequestIndustryDataResult requestIndustryData(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-03 请求行业数据, 请求参数: {}", request);
    RequestIndustryDataReqDTO bizData = JSON.parseObject(request.getBizData(), RequestIndustryDataReqDTO.class);
    bizData.setSignType(request.getSignType());  // 新增：透传 signType
    return