# 敏感配置与鉴权（现状 + 规则）

> **何时读**：改任何 `application.properties` 里的口令 / 私钥 / signKey / 3DES / token.secret，或新增 / 排查鉴权接口时 **MUST 先读**。
> 本文件承载「规则 + 当前存量清单」，**不再固化进 `AGENTS.md`**（2026-10-15 抽离）。
> **清单为 2026-10-15 静态核实结果；MUST 现查复核，尤其是「已清零 / 仍有 N 处」这类完成态断言。**

## 一、敏感配置规则

新增配置项时敏感项（口令 / 私钥 / signKey / 3DES 密钥 / token.secret）**MUST** 写成 `${ENV_VAR:}`（**空默认值**）并由 K8s Secret 注入，**NEVER** 写默认真值；**NEVER** 在对话、日志、提交信息或文档中回显密钥值，引用时只写键名与位置。生产 Oracle 地址、`DB_*` 环境变量与数据源已知坑见 `docs/ops/生产环境清单.md` §二、§三。

## 二、存量明文违规清单（2026-10-15 静态核实，MUST 现查）

> 下面**只列文件 + 键名，不回显任何密钥真值**。按「注入路径是否齐备」分两级，**B 级风险最高**。

### B 级（**2026-10-15 已清零**）：原「写死默认值 / 明文，又没有 K8s 注入路径」——风险最高

原 B 级两项：
- `daily-ticket-server` 的 `daily-ticket.pay.merchant-private-key`（ENV `DAILY_TICKET_PAY_MERCHANT_PRIVATE_KEY`）
- `pay-sign-server` 的 `pay.sign.merchant-private-key`（ENV `PAY_SIGN_MERCHANT_PRIVATE_KEY`）

两者**当前是同一把 PKCS#8 RSA 商户私钥**（1624 字符明文，曾直接入库）。2026-10-15 清零动作：
1. 两处的 properties 默认值已改空：`...merchant-private-key=${ENV}`（仓库不再含私钥明文）。
2. `deploy/k8s/05-secret-template.yaml` 已补 `daily-ticket.pay.merchant-private-key: ''` 与 `pay.sign.merchant-private-key: ''` 两个键。
3. `deploy/k8s/10-deployments/daily-ticket-server.yaml` / `pay-sign-server.yaml` 各加一条 `secretKeyRef: {name: itp-secrets, key: <同名键>}` 注入。

⚠️ **尚未生效的前置条件（MUST 现查）**：`deploy/` 只是清单模板（§附.5.1 ③：集群 env 覆盖 jar 内配置、`deploy/k8s/` 只作模板），**线上真正生效需**（a）在集群 `itp-secrets` 这两个键里填入真实私钥值；（b）`kubectl -n itp set env` 或重新 apply Deployment 使注入生效；（c）确认 `daily-ticket-server` / `pay-sign-server` Pod 能读到该 env。完成前，代码若拿到空值将**缺商户私钥**（支付/对账链路的签名步骤会失败）——**改完 MUST 在测试环境端到端验证一次商户签名/退款**。

### A 级：写死默认值 / 明文，但 K8s 已用 `secretKeyRef` 覆盖同名键 —— 属「双写」而非缺注入，仍是存量债

| 模块 | 键 |
|---|---|
| `alipay-account-server` | `other.sql.password`、`itp.signKey` |
| `acc-security-server` | `security.publicKey`、`security.privateKey`、`itp.signKey` |
| `account-server` | `itp.signKey` |
| `key-server` | `appserver.3des.key`、`acc.3des.key` |
| `acc-es-server` | `ftp.password` |
| `web-server/web-admin` | `token.secret`、`spring.datasource.druid.statViewServlet.login-password`、`spring.datasource.password` |
| 9 个模块（account / ticket / daily-ticket / blacklist / gate-txn-pay / para / employee-card-acc-simulator 等） | `other.sql.password`（ENV `DB_PASSWORD`，默认非空） |

**对照（已合规的空默认值写法）**：`recon-server` / `acc-es-server` / `pay-sign-server` / `key-server` / `card-pool-server` 的 `other.sql.password=${DB_PASSWORD:}`；`alipay-pay-sign-server` 的 `pay.center.merchant-private-key` 与 `pay.center.paycenter-public-key`（空）；`pay-sign-server` 的 `itp.signKey=`（空）。

**清零动作**：B 级先把 `deploy/k8s/05-secret-template.yaml` 补上对应键并确认 Deployment `valueFrom`，再把 properties 默认值改空；A 级逐模块把 properties 里硬编码真值改 `${ENV:}`。每改一处 MUST 跑一次「确认 K8s 已注入」的核对（键名与 secret 同名、`secretKeyRef` 到位）。

## 三、鉴权现状

### 规则

新增状态变更型接口 **MUST** 有鉴权与归属校验。鉴权 **MUST** 对齐现有验签实现（`AccountRequestVerifier` / `ItpRequestSignVerifier`，另有 `AccSignUtils` / `RSASignUtils` 等），**NEVER** 自造签名逻辑。

### 现状（2026-10-15 核实）

- **只有 `web-server`（web-admin）有 spring-security**；其余 18 个业务模块**全部没有**。
- 全局 `MoreInterceptor` 只做耗时统计（`runningCnt.incrementAndGet()` 后 `return true`），**不做鉴权**。
- `FirstFilter.checkToken()` 因 `web.properties` 与 `recon-server` 都设了 `other.web.skipCheckTokenUrls=/**` 而**显式关闭了全局 JWT 校验**（⚠️ 键名与语义相反：值恰为 `/**` 时是「整体跳过 JWT」，不是「只校验 `/**`」）。`recon-server` 的注释写明「对账内部接口当前无鉴权，有意临时降级，上线前 MUST 恢复」。
- ⇒ **业务接口在框架层就是裸暴露的，鉴权必须逐接口自己写。**
- 已知无鉴权的状态变更端点（docs/ops/生产环境清单.md §六「上线前必须处理的问题」已登记）：`collect-pay-server` 的 `/internal/app-order/*` 三个端点、IF8A-26 在线补款下单；`collect-pay-server` / `face-pay-server` 内**零**验签调用（`rg -l "AccountRequestVerifier|ItpRequestSignVerifier|checkSign"` 均 0 命中）。

## 四、可机检候选（待办，未实施）

- 在 `docs/提示词/lint-prompts.py` 增 `scan_sensitive_config_defaults`：扫 `**/src/main/resources/*.properties`，命中 `password|secret|signKey|privateKey|publicKey|3des|\.key|token` 的键，若既非 `${VAR:}`（空默认）则报 **ERROR**（存量入 `lint-baseline.json`）。判据与 §一 完全一致、机械可判。
- 扫 `crypto` / `sign` / `Cipher` / `Signature` 文件 diff 路径 → 命中即 WARN「触及加密/签名，MUST 人工复核」。
