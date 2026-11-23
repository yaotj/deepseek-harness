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
- 与业务服务的唯一交集是**共用同一个 Oracle 实例**（`web-admin/src/main/resources/application.properties` 的 `jdbc:oracle:thin:@//...:1521/hditpdb`，注意业务库是 `AFCITPDB`、综管台库是 `hditpdb`，两者不同），但当前只读写 `sys_*`

因此：**要给运营页面加后端接口时，MUST 先确认放在哪一侧。**
现状是业务运营接口放在**各业务模块的 `/page/**` controller** 中，例如：
- 日票退款 → `daily-ticket-server` 的 `/page/daily-ticket/refund`
- 当面付订单 → **`face-pay-server` 的 `/page/face-pay/orders`（2026-09-15 起，ADR-D85 续）**；`collect-pay-server` 的同名端点（**2026-11-20 起测试环境云管台 `Stopped` + K8s 副本 0、Deployment / Service 资源保留未删；红岛从未部署**）当前不可达，**NEVER 再把该页面指回 collect-pay**
- 票卡状态 → `ticket-server` 的 `/page/ride-status`
- 闸机扣费 → `gate-txn-pay-server` 的 `/page/gate-txn-pay`
- 黑名单 → `blacklist-server` 的 `/page/blacklist`
- 参数/风控 → `para-server` 的 `/page/**`
- 用户查询 → `account-server` `/page/user/itp`、`alipay-account-server` `/page/user/alipay`
  - 两个 `/search` 端点的筛选条件自 2026-11-06 起是**并列字段 + AND 组合**（`msisdn` / `cardId` / `thirdUserId` / `reqContractNo`），
    旧的 `queryType` + `keyword` 二选一形态已删除、**NEVER 再按「查询类型」拼参数**。空白字段不下发；**全空一律 `illegalParams`**（防整表扫描）。
  - `reqContractNo` 即「渠道签约流水号」，两表都有该列（`USER_ITP_REG_INFO.REQ_CONTRACT_NO` / `ALIPAY_USER_INFO.REQ_CONTRACT_NO`，均**无索引**，靠「至少一个条件」兜住）。
  - 「已注销用户查询」的数据源 `USER_ITP_REG_LOG` **没有该列**，`/page/user/itp/canceled-log/search` 收到 `reqContractNo` 直接拒绝，前端也不给入口 —— 静默忽略会让运营把「查不了」读成「没有销户记录」。
  - `/page/user/alipay/search` 另有第二个调用方：`rpc` 的 `AlipayAccountClient.searchAlipayUsers` → web-admin `ALIPAY_USER` 异步导出，改该端点参数 **MUST 同步 model 的 `AlipayUserExportDTO` + rpc + web-admin**。
- 扣费明细合并视图 → 主表复用 `gate-txn-pay-server` 的 `/page/gate-txn-pay`，另需两个**按订单号批量回填**端点（2026-11-06 新增）：
  - `GET /pay-sign-server/page/pay-refund/by-orders?orderNos=…`（pay-sign-server 的**首个** `/page` 端点）读 `PAY_REFUND_DETAIL`。
    **NEVER 改用它现成的 `POST /ci/app/queryPayTxnBatch`**：那个读 `PAY_TXN_DETAIL`，而 2026-11-06 实测闸机扣费成功的
    287,906 行里只有 71 行在那张表有记录（它关联的是支付中心侧流水），拿它做「退款状态」列会对运营最关心的单永远显示空；
    `PAY_REFUND_DETAIL.ORDER_NO` 才是与 `GATE_TXN_PAY.ORDER_NO` 同域的键（49 笔退款全挂在扣费成功的单上、挂扣费失败的是 0 笔）。
  - `GET /face-pay-server/page/face-pay/supplement-orders/by-orig?origOrderNos=…` 按 `SUPPLEMENT_ORDER_ITEM.ORIG_ORDER_NO`
    反查补款单 —— 此前该明细表**从未被任何 page 级查询 select 过**，前端拿不到可对账的字段。
  - 两个端点上限都是 200 个订单号（一页 100 行留余量，远低于 Oracle IN 列表 1000 硬顶）。
  - 展开行走 `ticket-server` 现有 `/page/qrcode-txn-detail`（按卡号 + 当日窗取回，前端用
    `CARD_ID + TRX_TYPE + HANDLE_DATE_TIME + TICKET_TRANS_SEQ` 四条收窄，实测 166.97 万扣费行命中 1,669,712）⇒ **ticket-server 零改动**。
  - 旧「交易明细」「扣费信息」两页与入口按裁决**原样保留**，新页从扣费信息页的「合并视图」按钮进入（`dynamicRoutes` 挂权限，不需要菜单 SQL）。

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
