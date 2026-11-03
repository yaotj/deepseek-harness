# IT-045 员工卡 ACC 资料查询地址未生效（env 点号命名致绑定失败）

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-045` 一行。

## 1. 背景 / 现象

- 发现时间：2026-11-02 16:46
- 发现渠道：联调测试（员工卡资料补全链路自查）
- 影响范围：`POST /employeeCard/query`（APP 查询员工码）的「资料补全」分支；出向 ACC `/api/v1/employee_card/query` 从未真正发出过请求
- 现象描述：account 两个副本均稳定输出
  ```
  WARN EmployeeCardOutboundServiceImpl.queryFromAcc(EmployeeCardOutboundServiceImpl.java:127)
       ACC员工码查询地址未配置, cardNo=13045027231
  WARN EmployeeCardServiceImpl.queryEmployeeCard(EmployeeCardServiceImpl.java:157)
       员工码资料未补全或ACC查询失败, cardNo=13045027231
  ```
  查询接口仍返 `0000`，但 `employeeName`/`company` 等资料字段全为 `null`（ACC 推送时也没带）。

### 1.1 同类第 2 例：`industry-data-server` 安全服务地址（2026-11-02 17:22 发现，已修）

- 发现渠道：排查 IT-046（拉码 8004）时，数据修好后链路推进到生码环节才暴露。
- 现象：`industry-data-server` 生成卡数据时稳定失败，返回 UUID 形态 `retCode`：
  ```
  ERROR webClient request failed ... WebClientRequestException: Failed to resolve 'acc-security-server-d8mf9-svc.itpb.svc'
  ERROR Service method execute error: ...rpc.security.SecurityClient.requestSignInsData
  ```
- 根因（与本单同源，但多一层「默认值指向生产」）：
  - `SecurityClient` 的 `@Value("${service.security.url:security-service}")` —— 读的是**精确键** `service.security.url`；
  - 而 `industry-data-server` 的 `application.properties` 写的是 `${SERVICE_SECURITY_URL:http://acc-security-server-d8mf9-svc.itpb.svc:31921}`，`@Value` 只认 `SERVICE_SECURITY_URL` 这个环境变量名；
  - 平台注入的是点号键 `service.security.url=http://security-server-internal.itp.svc:8080` → 不生效 → 回落到默认值，而该默认值是**红岛生产 `itpb` 命名空间**（复制红岛配置时残留）；
  - 结果是测试环境的 Pod 在跨命名空间解析生产服务名，`Failed to resolve`，签名环节必挂。
- 修复：走平台 `PATCH /apps/{uuid}/advanced-config` 追加 `SERVICE_SECURITY_URL=http://security-server-internal.itp.svc:8080`（`uuid=6b1145a12de24c6ca2eae94e951d47c0`，version 2.0.27→2.0.28，release `fb04085c84634c808d5780b48a81385f`）。
  - ⚠ **平台 `metadata.env` 只有 12 项，而 Pod 实际 20 项**（DB_HOST/DB_NAME/DB_PASSWORD/TZ/VLOGS_URL/NAMESPACE/SERVICE_NAME 等是历史 `kubectl set env` 加的，平台不认）。若照 `metadata.env` 全量回传会把这些整批删掉、服务起不来 —— **MUST 以 Pod 实际 env 为基线**构造 body。
- 验证：新 Pod `industry-data-server-78cbc79b4f-th8zw` 就绪，`SERVICE_SECURITY_URL` 与 `DB_HOST` 均在；`ticket-server` `/internal/ticket/industry/online` 实测返回 `0000` + 码数据。

### 1.2 同类第 3 例：`key-server` 安全服务地址（2026-11-02 17:34 发现并修复）

- 发现渠道：IT-046 数据修好、`industry-data-server` 修好之后，APP 仍然「生码失败」；查 fep-app 日志发现拉码成功后 APP 紧接着连调 3 次 `/ci/app/requestKeyList`（IF8A-02 取密钥），全部 `9999 失败`，`keyList=null`。
- 现象：`key-server` 稳定输出
  ```
  ERROR Service method execute error: ...rpc.security.SecurityClient.requestUserSm2Key
        java.lang.RuntimeException: WebClientRequestException: Failed to resolve 'acc-security-server-d8mf9-svc.itpb.svc'
  ERROR 处理IF8A-02请求同步密钥异常, thirdUserId=00522967, cardId=0426090949000058
  ```
- 根因：与 1.1 完全相同——jar 默认值 `${SERVICE_SECURITY_URL:http://acc-security-server-d8mf9-svc.itpb.svc:31921}` 指向红岛生产，平台只注入点号键 `service.security.url` → 回落生产地址。
- 修复：平台 `PATCH /apps/3fee922c974a4a49a4fb6f5b0411c0dd/advanced-config`，追加 `SERVICE_SECURITY_URL=http://security-server-internal.itp.svc:8080`（env 30 项 = 平台 22 项 ∪ Pod 29 项 ∪ 新键），version 2.0.21.1→2.0.21.2，release `9f50e2bc403a427a9dc909508d1ee1e7`。
- 验证：新 Pod `key-server-bb648dcb6-kv9zm` 就绪；`POST /requestKeyList` 直接实测返回 `{"retCode":"0000","keyList":[{"keyId":"01","keyUserId":"0007FAD7","keyPublic":...,"signData":...}]}`；用户 17:36 重试时 fep-app 侧 `requestKeyList` 亦返回 `0000`。

### 1.3 同类第 4 例：`fep-app` 黑名单地址（端口写错，2026-11-02 17:39 发现并修复）

- 发现渠道：取密钥修好后 APP 仍失败，日志显示 `/app/ticket/queryBlackList` 报 `503 Service Unavailable`，`retCode` 为 UUID 形态（本项目「异常转 UUID」约定）。
- 根因是**两层叠加**，比前 3 例多一层：
  1. **代码默认值端口错**：`service.blacklist.url=${SERVICE_BLACKLIST_URL:http://blacklist-server-wmfzs-svc.itp.svc:8080}`，而 `blacklist-server-wmfzs-svc` 的 Service 端口是 **9102**（`targetPort: 8080`）。请求 `svc:8080` 没有对应端口 → Envoy 直接 503，请求根本没到应用（应用日志 0 条，但直连 Pod IP:8080 是 200，可证）。
  2. **修正值被点号键吃掉**：平台上早已有人注入 `service.blacklist.url=http://blacklist-server-internal.itp.svc:8080`（值是对的），但键名带点号不生效 → 仍回落上面那个错默认值。点号键配了等于没配，这点最容易误判「已经改过了」。
- 影响面：`fep-app` 的 `queryBlackList` 自 152 天前 Service 建为 9102 起就一直失败；同一错默认值在 **5 个模块**里都存在（`fep-app-server` / `fep-alipay-server` / `pay-sign-server` / `alipay-pay-sign-server` / `web-server/web-admin`，最后一个是 `BLACKLIST_SERVICE_URL` 键名）。拉码前置必查黑名单，故这是「生码必失败」的第三个卡点。
- 修复：
  - 运行态：`PATCH /apps/b6368f7131f0453099611f7a8da5cf81/advanced-config` 追加 `SERVICE_BLACKLIST_URL=http://blacklist-server-internal.itp.svc:8080`（env 46 项），version 2.1.5→2.1.6，release `6122645349ad49cba8fc7c2720425cf4`。
  - 代码：上述 5 处默认值统一改为 `http://blacklist-server-internal.itp.svc:8080`（待发版生效；本次运行态已由 env 覆盖）。
- 验证：`/app/ticket/queryBlackList` 由 `1034ms + 503 + UUID retCode` 变为 `81ms + {"retCode":"0000","inBlack":"0"}`。

## 2. 根因分析

- 排查过程：
  1. `kubectl exec <pod> -- printenv | grep employee-card` → Pod 内**确实存在** `employee-card.acc-query-url=http://172.20.211.11:32605/api/v1/employee_card/query`；Deployment 模板里也有同一键，值一致 → 排除「env 没配」。
  2. 读配置类 `EmployeeCardOutboundProperties`（`@ConfigurationProperties(prefix="employee-card")`，字段 `accQueryUrl`，默认 `""`）→ 类本身正确。
  3. 对比同文件其他键：`app-register-url` / `acc-activate-url` 在 `application.properties` 里**有值**（前者明文、后者 `${EMPLOYEE_CARD_ACC_ACTIVATE_URL:默认值}`），而 `acc-query-url=` **为空**——只有这一个键完全依赖 env 注入。
  4. 结合项目既有实证（`AGENTS.md` §5.C「K8s env MUST 下划线命名」；IT-035 记录 face-pay 对点号 `server.port` 不生效、对 `SERVER_PORT` 生效）：**env 名带点号时 Spring 松弛绑定取不到**。
- 根因结论：Deployment 用点号 env 名 `employee-card.acc-query-url` 注入，Spring 期望的规范化形式是 `EMPLOYEE_CARD_ACC_QUERY_URL`，两者不匹配 → `accQueryUrl` 停留默认空串 → `queryFromAcc` 直接 WARN 返回 null。其余 `employee-card.*` 键未暴露问题，只是因为 properties 里有同值兜底。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A. env 改下划线命名 | 把 `employee-card.acc-query-url` 改为 `EMPLOYEE_CARD_ACC_QUERY_URL`（同批可把其余点号键一并规范） | 符合既有约定；零代码改动；松弛绑定天然支持 | 需同步仓库 yaml 与线上 Deployment，两处都要改 | 低 |
| B. properties 给默认值 | 在 `application.properties` 里补测试环境默认地址 | 改动最小 | 生产/测试地址不同，硬编码默认值是隐患；且掩盖命名问题 | 中 |
| C. 代码里加 `@Value("${EMPLOYEE_CARD_ACC_QUERY_URL:}")` 兜底 | 双读 | 兼容两种写法 | 同一配置两个来源，违反「配置项优先 `@ConfigurationProperties`」约定 | 中 |

## 4. 决策与结论

- 选定方案：**A（待执行）**——改 `deploy/k8s/10-deployments/account.yaml` 的键名，并同步测试环境 Deployment（优先走平台 `advanced-config`，保持与 IT-016/IT-020 同一姿势）。
- 决策理由：符合 `AGENTS.md` 既有硬约定，最小改动、无代码风险。
- 影响范围 / 关联模块：account-server（仅配置键名）；`docs/ops/第三方配合配置清单.md` 里该键的记录需同步。
- 附带结论：**该缺陷意味着「ACC 员工码资料查询」这条出向链路至今未被任何测试覆盖过**，修完必须补测（见 §5）。

## 5. 验证与回滚

- 验证方式 / 结果（修完执行）：
  1. 滚更后 `kubectl exec <pod> -- printenv | grep -i employee` 确认新键存在（旧点号键可保留，无害）；
  2. 用一张 `EMPLOYEE_NAME` 为空的卡调 `POST /fep-app/ci/app/employeeCard/query`；
  3. 断言：日志不再出现 `ACC员工码查询地址未配置`；若 ACC 侧有该卡资料，响应里 `employeeName` 等字段被补全（`refreshProfileFromAcc` 会把资料回写主表）；若 ACC 返 `8004`，则应看到 `ACC员工码查询返回失败` 的 WARN（说明请求已发出）。
  4. 直连验证：跳板机 `curl` 打 `http://172.20.211.11:32605/api/v1/employee_card/query`（multipart + `bizData={"cardNo":"..."}`），确认 ACC 侧该接口本身可用。
- **2026-11-02 17:28 修复后实测（通过）**：`13045027231`（本地 `EMPLOYEE_NAME` 为 NULL）调 `/fep-app/ci/app/employeeCard/query` → 0.099s 返回 `employeeName="一三零"`、`idCardNo="110105********3333"`；account 侧 traceId `ce997b20981a78bedf41c18d8cbf1dbc` 出现 `select` + `update USER_ACC_EMPLOYEE_CARD set EMPLOYEE_NAME=?...`（fetchRowCount=1）；库内回查确认姓名/身份证已落库（`UPDATE_TMS=17:28:12`）。日志中 `ACC员工码查询地址未配置` 不再出现。
- **2026-11-02 17:29 强化验证（用 ACC 实有数据）**：先把 `13111111111` 的 8 个资料字段全部置空（姓名/身份证/公司/中心/部门/岗位/照片），再调查询接口 → 0.103s 从 ACC 取回全量资料并回写：`employeeName="一一一"`、`idCardNo="111111********3333"`、company=`公司`、center=`测试`、department=`部门`、position=`岗位`、**photo 88020 字符（Base64 JPEG）**；库内 8 字段实测全部回写。证明补全链路（含 CLOB 照片）端到端可用。
- 回滚预案：env 键名改回点号形式即恢复现状（现状=功能缺失，不会更差）。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-045`）
- [x] 已建本处理文档并记录处理过程与决策
- [ ] 处理后已回填索引「状态 / 下一步 / 更新」（待执行后回填）
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的端口 / 路径 / 类名 / 行号均标注来源（2026-11-02 实测日志、Pod env 实查、配置类走查）
- 偏差说明：无——根因由「Pod env 实查 + 配置类走查 + 项目既有命名实证」三方交叉确认；尚差一步实测（改键名后复验）。
