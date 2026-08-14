# 统一使用 DeviceTypeEnum 计划

## 1. 任务目标

将项目中分散的设备类型字符串定义统一收敛到 `DeviceTypeEnum`，消除硬编码，提升可维护性和类型安全性。

## 2. 当前状态分析

### 2.1 已完成的枚举定义

`DeviceTypeEnum` 已创建于：
`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/model/src/main/java/com/chinasofti/huateng/model/enums/DeviceTypeEnum.java`

覆盖 01\~36 设备类型，包含：

* 01\~14：服务器/终端设备（ACC/LCC/SC/AGM/TVM/BOM/PCA/TCM/ES/ITP/STT）

* 15\~31：保留区间

* 32\~35：工作站（ACC/LCC/SC/ITP）

* 36：自助补站手机（0x24）

### 2.2 现状问题

1. **DTO/DO 层**：4 个类使用 `String devType`，无统一枚举约束

   * `StlStationConfigDO`

   * `SecDevInfoDTO`

   * `StlParaSynDTO`

   * `SearchDevByStationCodesAndDevTypesDTO`

2. **业务代码层**：大量硬编码设备类型字符串

   * `TicketRideStatusServiceImpl.java:391` 硬编码 `"36"` 构造设备ID

   * 各模块散落 `"01"~"08"` 等设备类型判断

3. **业务类型混淆**：`BusinessTypeEnum` 定义的是支付业务类型（扫码购票/充值/取票/BOM支付），不是设备类型，两者应保持分离

## 3. 决策总结

| 问题               | 决策                                                          |
| ---------------- | ----------------------------------------------------------- |
| DTO字段类型          | 保持 `String devType` 不变（兼容数据库/外部接口），但业务代码使用 `DeviceTypeEnum` |
| 硬编码替换            | 全部替换为 `DeviceTypeEnum.XXX.getCode()`                        |
| 字符串转枚举           | 统一使用 `DeviceTypeEnum.fromCode()`                            |
| BusinessTypeEnum | 保持独立，不合并到 DeviceTypeEnum                                    |

## 4. 详细修改计划

### 4.1 Model 层 - DTO/DO 增强（可选）

在以下 DTO/DO 中添加 `DeviceTypeEnum` 转换工具方法：

**StlStationConfigDO.java**

```java
public DeviceTypeEnum getDevTypeEnum() {
    return DeviceTypeEnum.fromCode(devType);
}
```

**SecDevInfoDTO.java**

```java
public DeviceTypeEnum getDevTypeEnum() {
    return DeviceTypeEnum.fromCode(devType);
}
```

**StlParaSynDTO.java**

```java
public DeviceTypeEnum getDevTypeEnum() {
    return DeviceTypeEnum.fromCode(devType);
}
```

**SearchDevByStationCodesAndDevTypesDTO.java**

```java
public List<DeviceTypeEnum> getDevTypeEnums() {
    return devTypes.stream()
        .map(DeviceTypeEnum::fromCode)
        .collect(Collectors.toList());
}
```

### 4.2 业务代码 - 硬编码替换

#### ticket-server

**TicketRideStatusServiceImpl.java**

| 行号  | 修改前                                             | 修改后                                                                                   |
| --- | ----------------------------------------------- | ------------------------------------------------------------------------------------- |
| 391 | `request.getUpgradeStationCode() + "36" + "01"` | `request.getUpgradeStationCode() + DeviceTypeEnum.SELF_SERVICE_GATE.getCode() + "01"` |

其他硬编码位置（需逐一审查）：

* 行 233：`"07"` 是签发渠道码，不是设备类型，保持原样

* 行 315/318/327-364：`codeStatus` 是票卡状态，不是设备类型，保持原样

#### collect-pay-server

**BusinessTypeEnum.java** - 保持独立，不修改

**TvmOrderPreServiceImpl.java**

| 行号 | 修改前                                             | 修改后                                                                    |
| -- | ----------------------------------------------- | ---------------------------------------------------------------------- |
| 25 | `private static final String TVM_PG = "01";`    | `private static final String TVM_PG = DeviceTypeEnum.TVM_1.getCode();` |
| 26 | `private static final String TVM_TOPUP = "02";` | 保持 `"02"`（业务类型，非设备类型）                                                  |
| 27 | `private static final String TVM_APP = "03";`   | 保持 `"03"`（业务类型，非设备类型）                                                  |

**BomOrderServiceImpl.java**

| 行号 | 修改前                                            | 修改后                                                                    |
| -- | ---------------------------------------------- | ---------------------------------------------------------------------- |
| 59 | `private final static String BOM_SALE = "01";` | `private final static String BOM_SALE = DeviceTypeEnum.BOM.getCode();` |
| 60 | `private final static String BOM_PAY = "02";`  | 保持 `"02"`（业务类型，非设备类型）                                                  |

### 4.3 工具类增强（可选）

在 `DeviceTypeEnum` 中添加：

```java
/**
 * 判断是否为AGM设备
 */
public boolean isAgm() {
    return this == AGM_IN || this == AGM_OUT || this == AGM_BIDIRECTIONAL;
}

/**
 * 判断是否为TVM设备
 */
public boolean isTvm() {
    return this == TVM_1 || this == TVM_2;
}

/**
 * 判断是否为BOM设备
 */
public boolean isBom() {
    return this == BOM;
}

/**
 * 判断是否为服务器/工作站
 */
public boolean isServerOrWorkstation() {
    return this == ACC_SERVER || this == LCC_SERVER || this == SC_SERVER 
        || this == ITP_SERVER || this == ACC_WORKSTATION 
        || this == LCC_WORKSTATION || this == SC_WORKSTATION 
        || this == ITP_WORKSTATION;
}
```

## 5. 执行顺序

1. **Phase 1**：Model 层 DTO/DO 添加转换方法
2. **Phase 2**：ticket-server 业务代码替换硬编码
3. **Phase 3**：collect-pay-server 业务代码替换硬编码
4. **Phase 4**：其他模块扫描并替换
5. **Phase 5**：编译验证

## 6. 验证步骤

1. 执行 `mvn compile` 确保编译通过
2. 执行 `mvn test` 确保单元测试通过
3. 全局搜索确认无遗漏硬编码：`grep -r '"01"\|"02"\|"03"\|"04"\|"05"\|"06"\|"07"\|"08"' --include="*.java" | grep -v DeviceTypeEnum | grep -v BusinessTypeEnum | grep -v test`

## 7. 风险与注意

1. **不要混淆业务类型和设备类型**

   * `BusinessTypeEnum` 是支付业务类型（扫码购票/充值/取票/BOM支付）

   * `DeviceTypeEnum` 是物理设备类型（TVM/AGM/BOM/服务器等）

   * 两者编码空间独立，不要合并

2. **保持数据库兼容**

   * DTO 字段保持 `String` 类型，不修改数据库表结构

   * 仅在 Java 业务层使用枚举进行类型安全转换

3. **保留区间**

   * 15\~31 (0x0F\~0x1F) 为保留区间，不在枚举中定义

   * 如遇未知编码，`fromCode()` 返回 `null`，调用方需处理空指针

