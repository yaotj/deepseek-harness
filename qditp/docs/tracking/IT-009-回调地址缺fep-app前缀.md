# IT-009 回调地址缺 fep-app 前缀

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-009` 一行。

## 1. 背景 / 现象

- 发现时间：2026-10-15 17:26
- 发现渠道：用户反馈（联调测试）
- 影响范围：daily-ticket-server（旅游票退款）、pay-sign-server（签约/支付回调）
- 现象描述：
  - 旅游票退款请求体中 `notifyUrl` 为 `http://58.56.166.170:48000/app/payment/receiveRefundResult`，支付中心推送后我方响应 404。
  - 签约请求体中 `notifyUrl` 为 `http://58.56.166.170:48000/ci/app/receiveSignResult`，同样缺少对外服务前缀 `fep-app`。
  - 正确地址应为 `http://58.56.166.170:48000/fep-app/...`，经 `fep-app-server` 反向代理到对应后端服务。

## 2. 根因分析

- 排查过程：
  1. 全仓 grep 定位 `notifyUrl` 配置源：`daily-ticket.pay.refund-notify-url` 与 `pay.sign.default-notify-url`。
  2. 读取 `daily-ticket-server/src/main/resources/application.properties:48` 与 `pay-sign-server/src/main/resources/application.properties:81`，发现默认值均缺少 `/fep-app` 前缀。
  3. 核对 K8s Deployment 环境变量：两服务虽在 `deploy/k8s/10-deployments/*.yaml` 中配了正确 URL，但环境变量名使用 `daily-ticket.pay.refund-notify-url`、`pay.sign.default-notify-url` 等 **dot-name** 形式。
  4. 根据 Spring Boot Relaxed Binding 规则，环境变量名必须为大写下划线形式（如 `DAILY_TICKET_PAY_REFUND_NOTIFY_URL`）才能正确映射到带点的配置键；dot-name 不会被识别，导致运行时回退到 `application.properties` 中的错误默认值。
- 根因结论：
  - 直接原因：`application.properties` 默认值缺 `/fep-app` 前缀。
  - 深层原因：K8s Deployment 环境变量名违反 `AGENTS.md`「K8s env var MUST 下划线命名」约束，dot-name 无法被 Spring Boot 识别，导致环境变量覆盖失效。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A | 仅修改 `application.properties` 默认值为带 `fep-app` 前缀 | 改动最小，本地/默认启动即可正确 | K8s 环境变量仍为 dot-name，后续如需覆盖配置仍不生效 | 覆盖能力缺失，隐患仍在 |
| 方案B | 同时修改 `application.properties` 默认值 **并** 将 K8s Deployment 中相关 env 名改为大写下划线 | 默认值正确，且环境变量可正常覆盖 | 改动文件稍多 | 无额外风险，符合项目规范 |

## 4. 决策与结论

- 选定方案：方案B
- 决策理由：
  - 默认值错误必须修复，确保本机、测试环境无需额外配置也能得到正确回调地址。
  - K8s env var 必须下划线命名是项目硬约束（`AGENTS.md`），且 dot-name 导致当前线上配置实际未生效，必须同步修正。
- 影响范围 / 关联模块：
  - `daily-ticket-server/src/main/resources/application.properties`
  - `pay-sign-server/src/main/resources/application.properties`
  - `deploy/k8s/10-deployments/daily-ticket-server.yaml`
  - `deploy/k8s/10-deployments/pay-sign-server.yaml`
  - 同步更新文档：`docs/ops/生产环境清单.md`、`docs/testing/payment/00-链路事实与配置字典.md`、`docs/testing/payment/01-签约与解约.md`、`docs/testing/payment/02-扣费与失败重试.md`

### 具体修改

1. `daily-ticket-server/application.properties`
   - `daily-ticket.pay.notify-url` 默认值：`/app/payment/receivePayResult` → `/fep-app/app/payment/receivePayResult`
   - `daily-ticket.pay.refund-notify-url` 默认值：`/app/payment/receiveRefundResult` → `/fep-app/app/payment/receiveRefundResult`

2. `pay-sign-server/application.properties`
   - `pay.sign.default-notify-url` 默认值：`/ci/app/receiveSignResult` → `/fep-app/ci/app/receiveSignResult`
   - `pay.sign.request-pay-notify-url` 默认值：`/ci/app/receivePayResult` → `/fep-app/ci/app/receivePayResult`

3. `deploy/k8s/10-deployments/daily-ticket-server.yaml`
   - `daily-ticket.pay.notify-url` → `DAILY_TICKET_PAY_NOTIFY_URL`
   - `daily-ticket.pay.refund-notify-url` → `DAILY_TICKET_PAY_REFUND_NOTIFY_URL`

4. `deploy/k8s/10-deployments/pay-sign-server.yaml`
   - `pay.sign.request-pay-notify-url` → `PAY_SIGN_REQUEST_PAY_NOTIFY_URL`
   - `pay.sign.default-notify-url` → `PAY_SIGN_DEFAULT_NOTIFY_URL`
   - `pay.sign.request-refund-notify-url` → `PAY_SIGN_REQUEST_REFUND_NOTIFY_URL`

## 5. 验证与回滚

- 验证方式 / 结果：
  - 本地离线编译：`mise exec -- mvn -o clean test-compile -pl model,rpc,daily-ticket-server,pay-sign-server -am`
  - 结果：**BUILD SUCCESS**（model / rpc / daily-ticket-server / pay-sign-server 均 SUCCESS）
  - 文档 lint：执行 `python3 docs/提示词/lint-prompts.py`，ERROR 为 0。
  - 逻辑验证：默认值已带 `/fep-app` 前缀；K8s env 名已改为大写下划线，可被 Spring Boot 正确识别。
  - 测试环境部署验证（2026-10-15）：
    - `daily-ticket-server` 1.0.68 → **1.0.69**，推送 digest `sha256:a963876a7b624c99f3f30b82c392b86dd1245970838810f6a53467e1d04b0465`
    - `pay-sign-server` 2.1.14 → **2.1.15**，推送 digest `sha256:38324958741346dd65f8f03d5d96205ccb1843775e641ea6ac255010a7a70997`
    - 两服务 Pod `2/2 Running`、重启 0、Pod `imageID` 与推送 digest 一致
    - NodePort 探活：`172.20.211.23:30027/actuator/health` 与 `172.20.211.23:30016/actuator/health` 均为 **http=200 / status=UP / Oracle UP**
- 回滚预案：
  - 代码回滚：恢复上述 4 个文件的修改。
  - 部署回滚：
    - `kubectl -n itp set image deployment/daily-ticket-server daily-ticket-server=os-harbor-svc.default.svc.cloudos:443/itp/daily-ticket-server:1.0.67`
    - `kubectl -n itp set image deployment/pay-sign-server pay-sign-server=os-harbor-svc.default.svc.cloudos:443/itp/pay-sign-server:2.1.13`

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-009`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 处理情况 / 更新」
- 偏差说明（如有）：无
