# IT-048 测试环境 pay-sign 支付网关地址指向未记录域名致扣款 9001

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-048` 一行。

## 1. 背景 / 现象

- 发现时间：2026-11-02 19:50（第二层出站扣款链路测试）
- 发现渠道：联调测试——模拟 APP 出站后 `gate-txn-pay-server` 生成订单并调用 `pay-sign-server`，`pay-sign-server` 调用支付网关返回 9001。
- 影响范围：测试环境 APP 乘车码出站扣款全流程；`PAY_TXN_DETAIL` 订单状态停留在待扣款/失败，乘客侧显示出站失败。
- 现象描述：
  - 模拟出站（fep-dev IF1A-01 `trxType=02`）后，`gate-txn-pay-server` 生成订单并调用 `pay-sign-server` 成功；
  - `pay-sign-server` 日志出现
    ```
    502 网络连接失败：无法解析服务器地址，请检查网络连接是否正常
    （getaddrinfo ENOTFOUND new-api.jointpilot.com） (target: https://new-api.jointpilot.com)
    ```
  - 外部响应包装成本地 9001“系统内部错误”，扣款失败。

## 2. 根因分析

- 排查过程：
  1. 读取 `pay-sign-server` 代码：`PaymentDomainServiceImpl` → `PaymentGatewayPort.requestPay` → `PaySignGateway.request` → `PayGatewayClient.request(url, bizData)`，`url` 取自 `PaySignProperties.requestPayUrl`（配置键 `pay.sign.request-pay-url`）。
  2. 读取 `pay-sign-server/src/main/resources/application.properties`：`pay.sign.request-pay-url=${PAY_SIGN_REQUEST_PAY_URL:http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/payment/requestPay}`，默认指向贝付测试网关 `dtcustomer.bestonepay.com`。
  3. 全仓库 `grep`（含 Git 全部历史 `-S 'jointpilot'/'new-api'`）**零命中**，确认 `new-api.jointpilot.com` 从未出现在本仓库任何代码、配置、文档中。
  4. 读取 `docs/external/支付中心网关接口文档.md`：支付中心基础路径为 `http://dtcustomer.bestonepay.com/ngpayment-gateway`，与仓库一致。
  5. 读取 `deploy/k8s/10-deployments/pay-sign-server.yaml`：未注入 `PAY_SIGN_REQUEST_PAY_URL` 或 `pay.sign.request-pay-url`，因此运行时会用代码默认值；但实际 Pod 报错目标为 `https://new-api.jointpilot.com`，说明运行态被 CloudOS 平台 `advanced-config` 中手工注入的环境变量覆盖。
  6. 当前本机网络无法路由到 `172.20.217.210`（CloudOS 测试平台）和 `172.20.211.23`（k8s-master），暂不能直接读取平台 `metadata.env` 做实锤；但代码、部署模板、接口文档三方交叉确认：**唯一可能来源就是 CloudOS 平台 advanced-config 的手工错误配置**。

- 根因结论：
  - CloudOS 测试环境 `pay-sign-server` 应用的 `advanced-config` 中，`pay.sign.request-pay-url`（或等价下划线键 `PAY_SIGN_REQUEST_PAY_URL`）被配置为 `https://new-api.jointpilot.com`；
  - 该域名不属于本系统任何文档/仓库记录，测试网无法解析，导致 `PayGatewayClient` 抛出网络异常并包装为 9001；
  - 与 IT-045 系列（点号键名不生效、默认值指向生产）不同，本次是**平台注入的值本身错误**。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A. 平台 PATCH 改回仓库默认值 | 通过 `PATCH /apps/{uuid}/advanced-config` 将 `pay.sign.request-pay-url` 改为 `http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/payment/requestPay` | 与仓库、接口文档一致；最小改动 | 需待本机可路由到 CloudOS 后执行 | 低 |
| B. 代码改默认值并重新发版 | 把 `application.properties` 里的默认值写死为正确 URL，忽略平台错误配置 | 不依赖平台立即修复 | 同 tag 覆盖规则下需重新 build-push-deploy，周期长；且未定位平台错误来源 | 中 |
| C. 测试环境启用 mock 网关 | 在测试 ns 起一个简单的 `/api/v1/payment/requestPay` mock，返回成功 | 不依赖外部网络 | 需要额外部署；只能验证链路，不能验证真实签名/扣款语义 | 中 |

## 4. 决策与结论

- 选定方案：**A（为主）+ C（备选）**。
- 决策理由：
  - 首选 A 是因为仓库默认值和接口文档已经指向正确的贝付测试网关，只需把平台错误配置纠正回来；
  - 若 `dtcustomer.bestonepay.com` 在测试网仍不可达（DNS/白名单问题），再启用方案 C 作为扣款链路 mock，保证第二层测试能继续。
- 影响范围 / 关联模块：
  - `pay-sign-server`（请求支付、支付查询、退款等 URL 都可能被同一错误 `gateway-url` 覆盖，需一并检查）；
  - `gate-txn-pay-server`（上游订单生成）、`ticket-server`（出站交易）、`fep-dev-server`（闸机模拟入口）；
  - 乘客 APP 出站体验、订单状态、对账数据。

## 5. 验证与回滚

- 修复方式：
  1. 本机恢复 `172.20.x` 路由后，运行已准备的脚本：
     ```bash
     bash /Users/tuanjie/workspace/company/chinasofti/qd/qditp/.workbuddy/tmp/pay-sign-patch-gateway-url.sh
     ```
     脚本会自动：
     - `GET` 当前 `advanced-config`；
     - 以 Pod 实际 `metadata.env` 为基线，把 `PAY_SIGN_REQUEST_PAY_URL` 改回 `http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/payment/requestPay`；
     - 巡检所有 env 值，若含 `jointpilot` 则一并替换为 `dtcustomer.bestonepay.com/ngpayment-gateway`；
     - `PATCH` 并等待滚更。
  2. 同步修改仓库 `deploy/k8s/10-deployments/pay-sign-server.yaml`，增加 `PAY_SIGN_REQUEST_PAY_URL` 环境变量（已提交），作为基础设施即代码的兜底，避免平台配置被误改或回滚后再次落入错误值。
  3. `PATCH` 后滚更 `pay-sign-server`，待 Pod Ready 后查看日志确认目标 URL 已变。

- 验证方式 / 结果（待执行后回填）：
  1. 重新模拟 APP 出站（fep-dev IF1A-01 `trxType=02`）；
  2. 断言 `pay-sign-server` 日志不再出现 `new-api.jointpilot.com`；
  3. 断言 `pay-sign-server` 向 `dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/payment/requestPay` 发起请求并收到支付中心响应（无论业务成功/失败，不再 9001）；
  4. 断言 `gate-txn-pay-server` 收到 `pay-sign-server` 回调或查询后，订单状态收敛到成功/明确失败（不再重试挂起）。

- 回滚预案：
  - 若 PATCH 后引发其他异常，可将 `pay.sign.request-pay-url` 重新指回 `https://new-api.jointpilot.com`（虽然该地址不可用，但能恢复 PATCH 前状态），或改为 mock 地址临时兜底。

## 6. 流程符合度自评

- [ ] 已在 `问题清单.md` 索引表加行（编号 `IT-048`）
- [x] 已建本处理文档并记录处理过程与决策
- [ ] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 路径 / 域名均标注来源（代码默认值、接口文档、部署模板）
- 偏差说明：无——根因由「代码默认值 + 部署模板未注入 + 接口文档 + 全仓库/全历史无 jointpilot 记录」四方交叉确认；唯一待补的是平台 `metadata.env` 的实锤读取（受网络不可达阻塞）。
