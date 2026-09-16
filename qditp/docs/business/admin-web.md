---
业务域: 综合管理后台
模块: web-server, web
---

# 提示词：综合管理后台

## 何时读本文件
管理后台的用户/角色/菜单/字典/定时任务/代码生成、登录鉴权、以及运营查询页面相关改动。

## 模块定位
`web-server` 基于 **RuoYi-Vue3** 改造，前端在 `web/`（Vue 3.5 + Vite 6 + Element Plus + Pinia）。

子模块结构、鉴权链路、数据源装配、前端工程结构见 [`../architecture/web-server.md`](../architecture/web-server.md)（技术视角，勿在本文重复维护）。本文只讲**运营页面接口该放哪里**。

## ⚠️ 关键事实：web-server 目前不调用任何业务服务
- 全部 pom **无 `rpc` / `model` / `micro` 依赖**
- 无 `RestTemplate` / `WebClient` / `@FeignClient` / 任何 `XxxClient` 注入
- `application.properties` 中**没有任何 `service.*.url`**
- 唯一 HTTP 工具 `web-common/.../utils/http/HttpUtils.java` 只被 IP 归属地查询 `AddressUtils` 使用
- 与业务服务的唯一交集是**共用同一个 Oracle 实例**（`web-admin/src/main/resources/application.properties` 的 `jdbc:oracle:thin:@//...:1521/AFCITPDB`），但当前只读写 `sys_*`

因此：**要给运营页面加后端接口时，MUST 先确认放在哪一侧。**
现状是业务运营接口放在**各业务模块的 `/page/**` controller** 中，例如：
- 日票退款 → `daily-ticket-server` 的 `/page/daily-ticket/refund`
- 当面付订单 → **`face-pay-server` 的 `/page/face-pay/orders`（2026-09-15 起，ADR-D85 续）**；`collect-pay-server` 的同名端点仍在但只服务旧单，**NEVER 再把该页面指回 collect-pay**
- 票卡状态 → `ticket-server` 的 `/page/ride-status`
- 闸机扣费 → `gate-txn-pay-server` 的 `/page/gate-txn-pay`
- 黑名单 → `blacklist-server` 的 `/page/blacklist`
- 参数/风控 → `para-server` 的 `/page/**`
- 用户查询 → `account-server` `/page/user/itp`、`alipay-account-server` `/page/user/alipay`

**MUST** 沿用这一约定：新运营接口写进对应业务模块的 `/page/**`，**NEVER** 在 web-server 里新建业务 controller 直连业务表。

菜单种子脚本里出现的业务表名（`web-server/sql/face-pay-order-menu.sql`、`user-query-menu.sql` 中的 `TBL_TVM_ORDER_PAY`、`ALIPAY_USER_INFO`、`USER_ITP_REG_INFO`、`QRCODE_STATUS`）**只是菜单注释，web-server 后端无对应实现**，勿据此推断存在实现。

## 数据表
`sys_*`、`gen_table` / `gen_table_column`、`QRTZ_*`（`web-server/sql/quartz.sql`）

## 前端约束
- **MUST** 使用 Vue 3.5 Composition API（`<script setup>`）+ Element Plus 规范
- API 层放 `web/src/api/**`，与后端 `/page/**` 路径对应；新增页面 **MUST** 同时补菜单 SQL（参考 `web-server/sql/*-menu.sql` 写法）
- 环境配置在 `web/.env.development` / `.env.staging` / `.env.production`

## ⚠️ 安全
`web-admin` 与多个模块的 `application.properties` 明文含数据库与 Druid 口令（既有问题）。
触碰这些文件 **MUST** 提示人工复核，**NEVER** 在对话或日志中回显口令值。

## 参考
- RuoYi-Vue3 原始文档（外部）
- `web-server/sql/` 下菜单与 Quartz 脚本
