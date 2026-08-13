# online-server 微服务文档

> **模块路径**: `web-server/web-admin/`（原 `online-server/`）
> **端口**: 9103（历史）
> **职责**: 在线支付/交易服务，处理地铁 APP 在线支付、交易查询、订单管理等业务
> **源码阅读范围**: 当前项目内未找到独立的 `online-server/` 源码目录；相关在线交易代码可能位于 `web-server/web-admin/` 或历史模块中
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

online-server 历史上承担以下核心职责：

1. **在线支付** - 处理地铁 APP 在线支付请求
2. **交易查询** - 查询交易记录、订单状态
3. **订单管理** - 订单创建、取消、查询
4. **支付回调** - 接收支付平台异步通知

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |

### 1.3 端口配置

```properties
# 历史配置
server.port=9103
```

### 1.4 数据表

| 表名 | 说明 |
|------|------|
| `ONLINE_ORDER` | 在线订单表 |
| `ONLINE_PAY_LOG` | 在线支付日志表 |
| `ONLINE_REFUND_LOG` | 在线退款日志表 |

---

## 二、接口清单

### 2.1 APP 接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF8A-12 | 请求支付 | `/ci/app/requestPay` | POST | |
| IF8A-13 | 查询订单 | `/ci/app/queryOrder` | POST | |
| IF8A-14 | 取消订单 | `/ci/app/cancelOrder` | POST | |
| IF8A-15 | 支付结果通知 | `/ci/app/receivePayResult` | POST | |

---

## 三、与其他服务的交互

### 3.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| online-server | pay-sign-server | 支付时 | 调用签约/支付接口 |
| online-server | account-server | 查询用户时 | 查询用户信息 |
| online-server | para-server | 查询参数时 | 查询票价、线路 |

---

## 四、源码说明

**当前状态**：项目中未找到独立的 `online-server/` 源码目录。根据项目结构，online-server 的相关功能可能已整合到以下模块：
- `pay-sign-server/` - 支付签约服务
- `collect-pay-server/` - 取票支付服务
- `web-server/web-admin/` - 管理后台

**建议**：如需更新本文档，请提供 `online-server/` 模块的实际源码路径，或确认功能归属后合并到对应服务文档。

---

## 五、相关文档

- `docs/02-微服务文档/pay-sign-server.md` - 支付签约服务
- `docs/02-微服务文档/collect-pay-server.md` - 取票支付服务

---

## 六、源码文件索引

### 6.1 注意事项

本文档暂无法基于源码编写，原因：
1. 项目根目录下不存在 `online-server/` 目录
2. `web-server/web-admin/` 为管理后台，不包含 online-server 业务代码
3. 历史 online-server 功能可能已迁移至其他服务

### 6.2 待补充

待确认源码位置后补充：
- 控制器层文件
- 服务层文件
- 实体类文件
- Mapper 文件

---

> **本文档为占位文档**，待确认 online-server 实际源码位置后更新。
