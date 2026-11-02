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
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A. 拿到 APP 侧正确地址后改 env | 向 bestonepay 要当前可用 `employeeCardSync` 地址，改 `account.yaml` env 滚更 | 根治，联调即真实链路 | 依赖 APP 侧响应速度 | 低；改 env 属常规变更 |
| B. 临时桩（仿 ACC 模拟器） | 联调期把地址指向一个返回 `0000` 的可用桩 | 不等 APP 侧，Test_010 立即可走 | 桩非真实行为，后续仍需真联；需新写/复用桩端点 | 中；MUST 防桩地址流入生产 |
| C. 代码跳过 APP 注册失败 | 注册失败也落库 | 一劳永逸不阻塞 | **违反既有 fail-fast 设计**（防 ITP 有卡、APP 无卡的裂口），NEVER 采用 | 高，否掉 |

## 4. 决策与结论
- 选定方案：待定。优先 A；APP 侧本期未就绪时退 B（桩方案需用户裁决）。
- 决策理由：notify 处理顺序「先 APP 注册、后落库」是刻意设计（见 `docs/business/account-employee-card.md` 员工码一节），不改代码。
- 影响范围 / 关联模块：account-server（配置变更）；employee-card-acc-simulator 不涉及。
- 附带发现（同批日志）：ACC 开卡通知上送 `cardStatus=1`（启用），按表注释「1-启用，2-禁用，3-未启用，4-注销」开卡应推 `3`，否则用户无需激活即为启用态、Test_012 激活前置（要求当前=3）走不通；`providerId='05'`（既有约定 06，fep-acc 当前不校验、不阻塞）。两项需与 ACC 侧对齐。

## 5. 验证与回滚
- 验证方式 / 结果：修复后由 ACC 重推同一测试卡，断言：返回 `0000`；`USER_ACC_EMPLOYEE_CARD` 落库 `CARD_STATUS=3`；`USER_ACC_EMPLOYEE_CARD_LOG` 有 OPEN 事件且无失败 remark。异常栈补取命令：`itp-ops.sh log tail account 92a8a3852143ae0adafee16efdd5f4c0 -500`（需可达跳板）。
- 回滚预案：env 回退原值即可（原值本就不通，回滚无劣化）。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-033`）
- [x] 已建本处理文档并记录处理过程与决策
- [ ] 处理后已回填索引「状态 / 下一步 / 更新」（待方案执行后回填）
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字均标注来源（traceId / 现场日志 / 代码走查 / 部署清单）；account 侧异常栈标注「待补取」
- 偏差说明：方案尚未裁决（依赖 APP 侧地址与用户对桩方案的决定），状态先置「待处理」。
