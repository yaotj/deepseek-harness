# IT-033 员工卡 APP 注册地址残留致 ACC 开卡通知全批失败

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-033` 一行。

## 1. 背景 / 现象

- 发现时间：2026-10-30
- 发现渠道：联调测试（员工卡 Test_010 首次真实推送，fep-acc 日志由现场提供）
- 影响范围：员工卡入向链路 Test_010~013 全部阻塞；account-server 处理 ACC 员工码状态通知（`POST /employeeCard/notify`）
- 现象描述：ACC 经 Kong（`172.20.211.200:48000/fep-acc`）推送开卡通知，fep-acc 收报文、转发 account 均正常（HTTP 200），但 account 返回 `{"retCode":"9999","retMsg":"员工码状态通知处理失败","failList":[{"cardNo":"111111111111111","reason":"调用APP注册接口失败"}]}`；主表 `USER_ACC_EMPLOYEE_CARD` 零落库。全链路耗时 4283ms。

## 2. 根因分析

- 排查过程（traceId / 关键日志 / 复现步骤）：
  - fep-acc 侧 traceId `92a8a3852143ae0adafee16efdd5f4c0`（2026-10-30 09:32:32）：`FirstFilter` 收到 `POST /employee_card/notify`（multipart/form-data，x-real-ip=`172.20.211.14`）→ `EmployeeCardController.employeeCardNotify` 打印 bizData → `InternalMicroHttp` 转发 `http://account-n4ba6-svc.itp.svc:9098/employeeCard/notify` → 4.18s 后返回 9999。
  - 代码走查：account `EmployeeCardServiceImpl.processAppBatch` 对每批卡**先调** `EmployeeCardOutboundService.registerToApp`，失败则整批进 failList 且不落库；`registerToApp`（`EmployeeCardOutboundServiceImpl`）catch RuntimeException 后记 `调用APP注册员工码失败` 并给全批标「调用APP注册接口失败」——与现场返回逐字吻合。
  - 配置核对：`employee-card.app-register-url` 在仓库 `account-server/src/main/resources/application.properties` 与测试环境 `deploy/k8s/10-deployments/account.yaml` 的 env 注入**同为** `https://dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/employeeCardSync`——bestonepay（APP 侧）测试环境地址残留（`testngbackV2` 命名与 B20 记录的 `wallet.app-gateway-url` 残留同类）。
- 根因结论：APP 注册地址指向已失效/集群不可达的 bestonepay 测试地址，调用抛异常导致整批失败。耗时 4.28s ≈ `employee-card.connect-timeout-ms=3000` + 开销，倾向**连接超时**（集群出外网受限），但最终判型 MUST 取 account 侧同 traceId 的异常栈（`UnknownHostException` / `ConnectTimeoutException` / SSL 三者修法不同）。本机 2026-10-30 09:3x 执行 `itp-ops.sh log tail account` 时 ssh 跳板（172.20.211.21:22）超时，异常栈待补取。

## 3. 方案对比

| 方案                    | 思路                                                               | 优点                     | 缺点                                                 | 风险              |
| --------------------- | ---------------------------------------------------------------- | ---------------------- | -------------------------------------------------- | --------------- |
| A. 拿到 APP 侧正确地址后改 env | 向 bestonepay 要当前可用 `employeeCardSync` 地址，改 `account.yaml` env 滚更 | 根治，联调即真实链路             | 依赖 APP 侧响应速度                                       | 低；改 env 属常规变更   |
| B. 临时桩（仿 ACC 模拟器）     | 联调期把地址指向一个返回 `0000` 的可用桩                                         | 不等 APP 侧，Test_010 立即可走 | 桩非真实行为，后续仍需真联；需新写/复用桩端点                            | 中；MUST 防桩地址流入生产 |
| C. 代码跳过 APP 注册失败      | 注册失败也落库                                                          | 一劳永逸不阻塞                | **违反既有 fail-fast 设计**（防 ITP 有卡、APP 无卡的裂口），NEVER 采用 | 高，否掉            |

## 4. 决策与结论

- 选定方案：**A/B/C 均无需执行——根因最终定位为测试数据问题，非地址失效**。2026-11-02 探测证实残留地址本身可用（HTTP 200 正常应答），失败是 APP 侧「手机号必须已注册且未绑员工卡」的数据校验。
- 决策理由：用 APP 已注册手机号 `19037529560` 直打 APP 接口返 `0000`；同号经 Kong 全链路推 notify（cardNo `3299990011`）返 `0000`，主表 + 日志表落库成功（证据见 §5 末段）。我方链路零改动。
- 影响范围 / 关联模块：无代码 / 配置变更。
- **遗留（转出，不阻塞本单关闭）**：① `app-register-url` 现值为 bestonepay **测试**环境地址，生产发版前 MUST 核对生产地址（归 `docs/ops/第三方配合配置清单.md` 口径）；② ACC 侧开卡通知推 `cardStatus=1`（应推 `3`）需对方修正；③ `12345678901` 在 APP 侧的状态（疑已绑卡）由 APP 侧自查。

## 5. 验证与回滚

- 验证方式 / 结果：修复后由 ACC 重推同一测试卡，断言：返回 `0000`；`USER_ACC_EMPLOYEE_CARD` 落库 `CARD_STATUS=3`；`USER_ACC_EMPLOYEE_CARD_LOG` 有 OPEN 事件且无失败 remark。
- **2026-11-02 复测（卡点转移，未闭环）**：ACC 重推（cardNo `3234567894`，traceId `d30358d8073f3a6f5af7d719bc49af61`，14:28:12 入 fep-acc）。
  - 实测 Deployment env 未变（`employee-card.app-register-url` 仍为 bestonepay 残留地址），但本次**调用未抛异常**——返回失败原因变为 `数据操作失败`。该文案全仓库零命中，对照 `registerToApp` 代码路径可判定：**它来自 APP 侧应答的 `retMsg`**，即 APP 注册接口已可达、请求已送达，是 APP 侧业务处理失败。
  - account 侧证据：14:28:13.077 收报文 → 14:28:14.369 写 `USER_ACC_EMPLOYEE_CARD_LOG`（`EVENT_TYPE=OPEN`、`REMARK=数据操作失败`）→ 14:28:14.5 返回 `9999` + failList；主表不落库（fail-fast 设计，行为正确）。
  - account 日志不含出向请求/应答报文（`registerToApp` 只在异常时打日志），APP 侧完整应答需 bestonepay 自查其 `employeeCardSync` 日志（按 cardNo + 时间点定位）。
  - 附带发现：① ACC 仍推 `cardStatus=1`（应 `3`）；② bizData 只带 phone/cardNo/cardStatus，`employeeName` 等资料字段全 null——若 APP 侧必填这些字段，很可能就是 `数据操作失败` 的直接原因，待对方确认；③ 同日 fep-acc 日志里有多条来自 `172.20.4.241` 的 `POST /fep-acc/ci/app/requestApplication` 探针，全部 `No static resource`（该路径不在 fep-acc，属 ACC 侧打错网关前缀，返回 UUID retCode）；④ 同日 14:36 ACC 推过一条 `update_notify`（cardNo `1111111111`），返回 `8004 未注册员工信息`，属预期（卡不存在）。
- **2026-11-02 14:38~14:49 本地模拟探测（三发 curl 直打 APP 接口，定位到具体失败语义）**：
  - 14:38 模拟 ACC 全资料推送（cardNo `3299990001`、cardStatus=3、资料字段齐全）走完整链路 → 仍 `9999/数据操作失败`——**排除「资料字段缺失」假设**；
  - 直打 `POST https://dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/employeeCardSync` 拿到原始应答：`retCode=0001` + `failList[{cardNo, phone, reason}]`，HTTP 200、约 0.46s；
  - 三发对照：① phone `12345678901` + cardNo `3299990001` → `数据操作失败`；② **新手机号 `13900001111`** + cardNo `3299990002` → **`手机号未注册`**（证明 APP 侧有「手机号必须已注册 APP」的前置校验）；③ phone `12345678901` + **新 cardNo `3299990003`** → 仍 `数据操作失败`——**失败归属手机号维度**：该号在 APP 侧已注册但数据处理失败（疑似已绑定过员工卡或账号状态异常），与 cardNo 是否重复无关。
  - 结论：联调阻塞转化为一个明确的造数要求——**需要 APP 测试环境「已注册且未绑定员工卡」的测试手机号**；`12345678901` 需 APP 侧查其账号/绑卡状态。
- 回滚预案：env 回退原值即可（原值本就不通，回滚无劣化）。
- **2026-11-02 14:45 闭环验证（通过）**：手机号换成 APP 已注册的 `19037529560` 后——直打 APP 接口返 `{"retCode":"0000","retMsg":"成功"}`；再模拟 ACC 经 Kong 全链路推 notify（cardNo `3299990011`、cardStatus=3、全资料），account 侧 traceId `450bd3c16a54c156de0f0ca9037e547c` 证据链完整：select 主表 0 行 → `insert into USER_ACC_EMPLOYEE_CARD` fetchRowCount=1 → `insert into USER_ACC_EMPLOYEE_CARD_LOG`（`OPEN` / `'3'` / `员工码开通成功`）→ 应答 `{"retCode":"0000","retMsg":"员工码状态通知处理成功","failList":[]}`。**Test_010 前半段（ACC 推送开卡 → ITP 落库）通过**；后半段（APP 激活 → 展码过闸）待续。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-033`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字均标注来源（traceId / 现场日志 / 代码走查 / 部署清单 / 实测 curl 应答）
- 偏差说明：无——标题保留原始现象描述，根因经三轮实测修正为「APP 侧手机号维度数据校验」，地址残留降级为遗留项。
