---
业务域: 电子员工卡
模块: account-server, fep-acc-server, fep-app-server
来源: 青岛地铁电子员工卡接口技术规格说明书 V2.0（2026-08-13 编制，V2.1 2026-08-21 修订错误代码与接口名称）
---

# 电子员工卡接口技术规格

> 本文档是 `docs/接口规范文档/青岛地铁电子员工卡接口技术规格说明书*.docx` 的 Markdown 归档，供 Agent 快速检索与代码对照。
> 文中 IP/URL 均为测试环境；生产地址见 `docs/ops/environments/跨环境共性.md`。

## 1. 通用约定

### 1.1 通信方式

- HTTP POST
- 公共参数见 §1.4，业务参数放在 `bizData`（JSON）
- 签名原始串：除 `sign` 外所有非空参数按字段名 ASCII 升序以 `key=value` 拼接，不 URL Encode；空值不参与签名

### 1.2 公共参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `providerId` | string | `01` 青岛地铁 APP / `02` TVM / `03` BOM / `04` AGM / `05` ACC / `06` ITP / `07` STT |
| `charset` | string | `UTF-8` |
| `format` | string | `json` |
| `timestamp` | string | `YYYYMMDDHHMMSS` |
| `deviceId` | string | 设备编码 |
| `signType` | string | `00` 不签名 / `01` sha1withrsa / `02` MD5 |
| `sign` | string | 签名值 |
| `bizData` | json | 业务数据 |

### 1.3 通用错误码

| 码 | 含义 | 出现接口 |
|----|------|----------|
| `0000` | 成功 | 全部 |
| `0001` | 部分成功/部分失败 | 员工码状态通知 |
| `8001` | 请求参数无效 | `cardNo` 为空、`cardList` 为空等 |
| `8004` | 员工码信息不存在/未注册 | 查询、变更时未注册 |
| `9999` | 参数错误/验签错误 | 全部 |
| `7005` | 数据处理中 | 全部 |
| `7004` | 处理过程出现错误 | 全部 |

> 代码实现中另有账户域自定义码（如 `2002` 当前状态不允许激活），见 `AccountErrorCodeEnum.java`。

### 1.4 敏感信息

身份证号、手机号等在内网传输；代码实现中 ITP 对手机号/身份证做脱敏后返回 APP。

## 2. 状态定义

`USER_ACC_EMPLOYEE_CARD.CARD_STATUS`：

| 值 | 名称 | 说明 |
|----|------|------|
| `1` | 启用 | 可正常使用 |
| `2` | 禁用 | 暂时不可用 |
| `3` | 未启用 | 新员工默认状态 |
| `4` | 删除/注销 | 永久不可用 |

> 代码实现中枚举为 `EmployeeCardStatus`：`NORMAL(1)` / `DISABLED(2)` / `NOT_ENABLED(3)` / `CANCELED(4)`。

## 3. 下行通知接口（ACC → ITP → APP）

### 3.1 员工卡开通与使用状态通知

- **接口方向**：ACC → ITP → APP
- **ACC → ITP 地址**：`POST http://172.20.211.200:48000/fep-acc/employee_card/notify`
- **ITP → APP 地址**：`POST https://dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/employeeCardSync`
- **场景**：ACC 工作站新增/调整员工信息后主动通知

#### 请求参数

| 参数名 | 类型 | 必填 | 长度 | 说明 |
|--------|------|------|------|------|
| `cardList` | Array | 是 | — | 员工卡状态变更列表 |
| `phone` | String | 是 | 11 | 员工手机号，APP 用户关联标识 |
| `cardNo` | String | 是 | ≤20 | 实体员工卡卡号 |
| `cardStatus` | Integer | 是 | 1 | `1`-启用 / `2`-禁用 / `3`-未启用 / `4`-删除（注销） |

请求示例：

```json
{
  "cardList": [
    {
      "phone": "13800138000",
      "cardNo": "QD20240001",
      "cardStatus": 1
    }
  ]
}
```

#### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `retCode` | String | `0000` 全部成功 / `0001` 部分成功 |
| `retMsg` | String | 响应描述 |
| `failList` | Array | 失败项列表（部分成功时） |
| `failList[].cardNo` | String | 失败卡号 |
| `failList[].phone` | String | 失败手机号 |
| `failList[].reason` | String | 失败原因 |

成功响应：

```json
{
  "retCode": "0000",
  "retMsg": "批量状态更新通知成功，共处理 3 条"
}
```

失败响应：

```json
{
  "retCode": "0001",
  "retMsg": "部分处理失败",
  "failList": [
    {
      "cardNo": "QD20240002",
      "phone": "13800138000",
      "reason": "员工号不存在或手机号未注册APP"
    }
  ]
}
```

#### 代码对应

- fep-acc-server：`POST /employee_card/notify` → `AccountClient.notifyEmployeeCardStatus` → account-server `POST /employeeCard/notify`
- account-server：`EmployeeCardServiceImpl.notifyEmployeeCardStatus`
- APP 注册：`EmployeeCardOutboundServiceImpl.registerToApp`

### 3.2 员工信息变更通知

- **接口方向**：ACC → ITP
- **地址**：`POST http://172.20.211.200:48000/fep-acc/ci/app/employee_card/update_notify`
- **场景**：ACC 工作站调整员工信息（公司、中心、部门、岗位、实体员工卡号、照片）

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `company` | String | 是 | 所属公司 |
| `center` | String | 是 | 所属中心 |
| `department` | String | 是 | 所属部门 |
| `position` | String | 是 | 岗位 |
| `cardNo` | String | 是 | 实体员工卡号 |
| `photo` | String | 是 | 1 寸证件照 Base64，≤250KB |

> **注意**：规范列出的变更字段仅 `company/center/department/position/photo`，**不含 `employeeName` / `idCardNo`**。

请求示例：

```json
{
  "company": "青岛地铁运营有限公司",
  "center": "运营一中心",
  "department": "客运一部",
  "position": "值班站长",
  "cardNo": "QD20240001",
  "photo": "照片数据（1寸证件照，大小250KB以内）"
}
```

#### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `retCode` | String | `0000` 成功 |
| `retMsg` | String | 响应描述 |

#### 代码对应

- fep-acc-server：`POST /employee_card/update_notify` → `AccountClient.updateEmployeeInfo` → account-server `POST /employeeCard/updateNotify`
- account-server：`EmployeeCardServiceImpl.updateEmployeeInfo`

## 4. 上行请求接口（APP → ITP → ACC）

### 4.1 员工卡信息查询

- **接口方向**：APP → ITP → ACC
- **APP → ITP 地址**：`POST http://172.20.211.200:48000/fep-app/ci/app/employee_card/query`
- **ITP → ACC 地址**：`POST http://172.20.211.11:32605/api/v1/employee_card/query`
- **场景**：APP 查看个人电子员工卡详情
- **规则**：若 ITP 无信息则向 ACC 查询；若 ITP 有信息则直接应答

#### 请求参数

| 参数名 | 类型 | 必填 | 长度 | 说明 |
|--------|------|------|------|------|
| `cardNo` | String | 是 | ≤20 | 实体员工卡卡号 |

#### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `retCode` | String | 响应码 |
| `retMsg` | String | 响应描述 |
| `employeeName` | String | 员工姓名 |
| `employeeNo` | String | 员工编号 |
| `phone` | String | 员工手机号 |
| `idCardNo` | String | 身份证号（脱敏：前 6 后 4） |
| `company` | String | 所属公司 |
| `center` | String | 所属中心 |
| `department` | String | 所属部门 |
| `position` | String | 岗位 |
| `cardNo` | String | 实体员工卡号 |
| `photoUrl` | String | 员工照片 Base64，≤250KB |
| `cardStatus` | Integer | `1`-启用 / `2`-禁用 / `3`-未启用 / `4`-删除/注销 |

#### 代码对应

- fep-app-server：`AppAccountController.queryEmployeeCard` → `AccountClient.queryEmployeeCard` → account-server `POST /employeeCard/query`
- account-server：`EmployeeCardServiceImpl.queryEmployeeCard`
- ACC 回查：`EmployeeCardOutboundServiceImpl.queryFromAcc`
- **已知实现差异**：需求说明书要求「若 ITP 无信息则向 ACC 查询」，代码实际逻辑是「若 `employeeName` 为空且状态非注销，则向 ACC 回查补全」。注销态（`4`）已在 IT-047 修复中排除补全。

### 4.2 请求电子员工卡激活/禁用

- **接口方向**：APP → ITP → ACC
- **地址**：`POST http://172.20.211.11:32605/api/v1/employee_card/activate`
- **场景**：APP 提交激活/禁用申请
- **前置条件**：电子卡须处于「待启用」状态方可激活

#### 请求参数

| 参数名 | 类型 | 必填 | 长度 | 说明 |
|--------|------|------|------|------|
| `cardNo` | String | 是 | ≤20 | 实体员工卡卡号 |
| `actionFlag` | Integer | 是 | 1 | `1`-启用（激活）/ `0`-禁用 |

#### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `retCode` | String | `0000` 成功 |
| `retMsg` | String | 响应描述 |

失败示例：

```json
{
  "retCode": "2002",
  "retMsg": "电子卡当前状态不允许激活操作"
}
```

#### 代码对应

- fep-app-server：`AppAccountController.activateEmployeeCard` → `AccountClient.activateEmployeeCard` → account-server `POST /employeeCard/activate`
- account-server：`EmployeeCardServiceImpl.activateEmployeeCard`
- ACC 出网：`EmployeeCardOutboundServiceImpl.requestActivation`
- **实现差异**：需求说明书前置条件为「待启用（3）方可激活」，代码实现已扩展为「`3` 未启用 或 `2` 禁用 均可激活到 `1`」（IT-044 修复）。

## 5. APP → ITP 直接接口

### 5.1 员工卡乘车记录查询

- **接口方向**：APP → ITP
- **遵循规范**：通用交易记录查询流程 IF8A-05
- **说明**：参数与返回格式与普通乘车码交易查询一致，详见 ITP 通用接口规范文档。

## 6. 非功能需求

### 6.1 性能（参考）

| 指标 | 要求 |
|------|------|
| P95 响应时间 | ≤500ms（不含 ACC 内部处理） |
| P99 响应时间 | ≤2000ms |
| 并发处理能力 | ≥500 TPS（ITP 网关层） |
| 推送到达延迟 P99 | ≤3 秒 |
| 系统可用性 | ≥99.9% |

### 6.2 容错与重试

- 调用失败须指数退避重试（初始 1s，最大 30s，最多 3 次）
- ACC 推送失败写入重试队列定时重推
- ITP 作为中转层须实现熔断：下游 ACC 异常时快速失败

### 6.3 日志与监控

- 全链路 traceId
- 上报调用量、成功率、响应时间分布
- 非 `0000` 错误码触发告警

## 7. 代码实现对照表

| 规格接口 | 规格路径 | 代码 Controller | 代码 Service |
|----------|----------|-----------------|--------------|
| 状态通知 | `/fep-acc/employee_card/notify` | `fep-acc-server/EmployeeCardController` | `EmployeeCardServiceImpl.notifyEmployeeCardStatus` |
| 信息变更 | `/fep-acc/ci/app/employee_card/update_notify` | `fep-acc-server/EmployeeCardController` | `EmployeeCardServiceImpl.updateEmployeeInfo` |
| 信息查询 | `/fep-app/ci/app/employee_card/query` | `fep-app-server/AppAccountController` | `EmployeeCardServiceImpl.queryEmployeeCard` |
| 激活/禁用 | `/api/v1/employee_card/activate`（ACC） | `fep-app-server/AppAccountController` | `EmployeeCardServiceImpl.activateEmployeeCard` |

## 8. 已发现的实现偏差与修复

| 问题 | 现象 | 修复 | 文档 |
|------|------|------|------|
| IT-044 | 停用（`1→2`）后 APP 无法重新启用 | 激活白名单增加 `2→1` | `docs/tracking/IT-044-员工卡停用后APP无法启用状态机不闭环.md` |
| IT-045 | ACC 查询地址因点号 env 名未绑定而失效 | env 改下划线命名 | `docs/tracking/IT-045-员工卡ACC查询地址未生效.md` |
| IT-047 | 注销（`4`）后查询仍回查 ACC 补全资料 | `queryEmployeeCard` 排除 `CANCELED` 状态 | `docs/tracking/IT-047-员工卡注销后查询仍回查ACC补全资料.md` |

## 参考

- `docs/business/account-employee-card.md` — 账户域与员工卡代码实现全貌
- `docs/接口规范文档/青岛地铁电子员工卡接口技术规格说明书22.docx`
- `docs/接口规范文档/青岛地铁电子员工卡接口描述.docx`
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`
