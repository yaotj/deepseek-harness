---
业务域: 账户开户 / 支付通道 / 电子员工卡
模块: account-server, fep-acc-server
---

# 提示词：账户与电子员工卡

## 何时读本文件
开户（IF8A-01）、用户信息查询、支付通道增删改与默认通道设置（IF8A-23/24/25/77）、更换手机号、HCE 数据更新，以及电子员工卡（IF3A 域）相关改动。

## 模块定位
- **account-server**：账户中心，端口 **9098**，`spring.application.name=account`。真正持久化用户注册与通道信息。
  - ⚠️ 端口 9098 与 `collect-ticket-server` 冲突，同机部署 **MUST** 提示用户改端口。
- **fep-acc-server**：ACC 侧**员工卡通知的薄接入层**，端口 **9110**，全模块仅 5 个 java 文件，无 mapper / 无定时任务 / 无 MQ。
  - 认知纠正：本模块**不做** ACC 文件传输、不做对账、不处理 `0xXXXX` 系统级报文。

## 接口清单

**account-server**（**无类级路径前缀**，由 fep-app-server 转发）
`account-server/.../controller/ci/app/RequestApplicationController.java`
> ⚠️ 该类只有 `@RestController`、**无类级 `@RequestMapping`**，真实路径是根路径 `/requestApplication`、`/requestAddPayChannel` …，**没有 `/ci/app` 前缀**。`rpc/.../account/AccountClient.java:60` 起也按无前缀调用。**NEVER** 给这些路径加 `/ci/app`。
- `requestApplication`(IF8A-01)、`requestAddPayChannel`(IF8A-23)、`requestSetDefaultPayChannel`(IF8A-24)、`requestRemovePayChannel`(IF8A-25)、`requestUpdateChannelDefaultContract`(IF8A-77)
- `requestAgreeRelease`（同意解约，`RequestApplicationController.java:66`）
- `queryUserInfo`、`queryCardTypeByCardId`(GET)、`updateHceData`、`updatePhone`(GET)

员工卡 `account-server/.../controller/ci/employee/EmployeeCardController.java`
- `employeeCard/notify`（开卡通知）、`employeeCard/query`、`employeeCard/updateNotify`（信息变更）

渠道侧 `account-server/.../controller/ci/channel/FepAlipayTripRequestApplicationController.java`
- `channel/requestApplication`

运营端 `account-server/.../controller/page/ItpUserPageController.java` — `GET /page/user/itp/search`

**fep-acc-server**（`multipart/form-data`）
`fep-acc-server/.../controller/EmployeeCardController.java`
- `POST /employee_card/notify`、`POST /employee_card/update_notify` → 剥离 ACC 报文头后转发 account-server
- bizData 解析走 `BaseAccController.parseBizData`（Fastjson2）

## 验签
`account-server/.../service/AccountRequestVerifier.java` 是入向校验唯一实现：
校验 `providerId / charset / format / timestamp / signType`，`signType=00` 视为免签，签名走 `itp.signKey` 摘要方式（**不是 RSA**）。
修改此类 **MUST** 提示人工复核安全合规性。

## 数据表
- `USER_ITP_REG_INFO`（用户注册信息，含卡类型）
- `USER_PHONE_CHANGE_LOG`（换号流水）
- 支付通道相关 `APP_USER_PAY_CHANNEL`（建表在 `pay-sign-server/src/main/resources/sql/pay-sign-schema.sql`）
- 员工卡与票号相关表见 `account-server/src/main/resources/mapper/` 下 `UserAccEmployeeCardMapper` / `UserAccEmployeeCardLogMapper` / `UserAccTicketNoMapper` / `UserItpRegLogMapper` 对应的表名
> DDL 位置：本模块主建表脚本是 `account-server/src/main/resources/sql/account-server-schema.sql`；卡类型字段迁移见 `account-server/src/main/resources/sql/account-server-card-type-migration.sql`。
> mapper XML 位于 `account-server/src/main/resources/mapper/`，新增 SQL **MUST** 放这里并走自研 `mybatis-adaptor`。

## 编码约束
- 卡类型判断 **MUST** 复用 `model` 模块的 `CardTypeCodeEnum` / `CardTypeMapping`，**NEVER** 在业务层硬编码卡类型字符串。
- 支付通道的"默认通道"与 pay-sign 的签约状态是两套数据：改动默认通道 **MUST** 同步确认 `APP_PAY_SIGN_INFO` 是否存在有效签约，否则会出现"有默认通道但无签约"的脏状态。
- 员工卡通知是 ACC 单向推送，无回执重试机制；新增字段 **MUST** 保证对旧报文向后兼容（缺字段不报错）。

## 参考原始文档
- `docs/接口规范文档/青岛地铁电子员工卡接口技术规格说明书V2.0_接口清单.md`
- `docs/接口规范文档/青岛地铁电子员工卡接口描述.docx`
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`（IF8A-01/23/24/25/77）
