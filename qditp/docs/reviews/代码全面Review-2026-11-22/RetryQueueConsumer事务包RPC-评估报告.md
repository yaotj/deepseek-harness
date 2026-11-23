# RetryQueueConsumer 事务包 RPC 评估报告

| 项目 | 内容 |
|---|---|
| 评估日期 | 2026-11-23 |
| 评估对象 | Review 报告第 1/2/9 轮三次命中的 P1「扣款重试队列事务包 RPC，重复扣款风险」 |
| 代码位置 | `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/service/impl/RetryQueueConsumer.java:46,73,87` |
| 评估结论 | **风险属实，但评级由 P1 修正为 P2**；报告假设的「多实例并发双扣」前提不成立，真实残余风险是「长事务 + 回滚吞证据」。**本次不改代码**（用户裁决：先出评估）。 |
| 性质 | 只读评估；未修改任何代码、配置、DDL |

---

## 1. 报告原结论

10 轮 Review 报告在三个维度重复命中同一处：

- 第1轮 P1：「扣款重试队列的领取标记在事务内、外部扣款在提交前，多实例可重复发起扣款」
- 第2轮 P1：「补偿消费在事务内发起远程 RPC，行锁跨越支付链路」
- 第9轮 P1：「用户重试队列消费把跨服务 RPC 包进 `@Transactional`」，引用 `docs/business/gate-txn-pay.md:61`「NEVER 带 `@Transactional`（链路含 RPC）」

建议修法（报告）：去方法级 `@Transactional`，改逐笔短事务 + 领取/投递分离 + CAS 收口。

## 2. 代码核查（属实部分）

`RetryQueueConsumer.consumeBatch()`：

- `@Transactional(rollbackFor = Exception.class)`（`:46`）标注在整批方法上；
- `recoverTimeout`（`:50`）+ `selectPendingForConsume`（`:57`，默认 10 笔）+ **逐笔循环**（`:71`）共用同一个事务；
- 循环内 `markProcessing`（`:73`，`UPDATE ... STATUS='PROCESSING'`，映射 XML `:82-89` 无租约字段）与**外部 RPC** `paySignInitiator.retryAndConverge(order)`（`:87`，内部 `requestPaySign` / `requestAlipayTripPay` 走 HTTP）在同一事务里；
- 逐笔 `catch RuntimeException`（`:102`）只记日志继续，**不回滚、不重抛**。

⇒ 「`@Transactional` 包住 RPC」事实成立，确实违反 `docs/architecture/transaction-and-consistency.md` §6「`@Transactional` 方法内 NEVER 发起任何 RPC / 网络调用」的行锁放大禁令（该文档记载 2026-08-26 生产事故：`PaymentDomainServiceImpl.receivePayResult` 事务内调 `syncDebitStatus`，循环 8 分钟、单请求 287 秒、`enq: TX - row lock contention`、回调日志零落库）。

## 3. 评级修正依据（报告未核实的部分）

### 3.1 多实例前提不成立

| 证据 | 来源 | 结论 |
|---|---|---|
| `sys_job` 400「用户主动重试扣费队列消费」`concurrent='1'`，注释明写「**web-admin MUST 单副本**」 | `web-server/web-quartz/src/main/resources/sql/web-quartz-retry-queue-consume-job-migration.sql:5-6` | 禁止同一 job 并发执行 |
| 触发端是 web-admin Quartz 的 `gateTxnPayQuartzTask.consumeRetryQueue()`，而 web-admin 是单副本 | ADR-D169、`sys_job` 脚本注释 | 单实例调度，无两实例同时消费 |
| gate-txn-pay 部署 `replicas: 1` | `deploy/k8s/10-deployments/gate-txn-pay.yaml:11` | 服务本身也是单副本 |
| cron `0 0/5 * * * ?`（每5分钟）；`concurrent='1'` 保证上一轮没跑完不重叠 | ADR-D169 + sys_job | 无跨轮重叠 |

报告第1轮的核心场景「face-pay 多实例并发捞同一批」在 `RetryQueueConsumer` 上**不成立**——它既不是多副本服务、调度器也不并发。第1轮实际命中的是 face-pay 的 `F2fNotifyDeliverer`（另一处），报告把两个不同模块的问题混在了一起。

### 3.2 三道幂等兜底仍在（即使重复出网也不等于双扣）

| 层级 | 机制 | 证据 |
|---|---|---|
| ① 订单状态 CAS | `updateStatusFromPending` 的 WHERE 带 `DEBIT_STATUS IN ('INIT','RETRY')` 白名单，第二笔更新返 0 行 | `GateTxnPayMapper.xml:159-167`、`GateTxnPayWriter.java:57-65` |
| ② 下游明细唯一索引 | `UK_PAY_TXN_DETAIL_ORDER ON PAY_TXN_DETAIL (ORDER_NO, TXN_DATE) LOCAL`；`ensurePayTxn` 并发插入捕获冲突后继续 | `pay-sign-server/src/main/resources/sql/pay-txn-schema.sql:70-71`、`PaymentDomainServiceImpl.java:576-605` |
| ③ 支付中心幂等 | `PaymentReply.AlreadyPaid` 分支按「订单已支付成功（幂等）」返成功，不重复扣 | `PaymentDomainServiceImpl.java:138-143` |

⇒ 报告所谓「重复扣款」在链路上有三道拦截；其中①②是我方确定的，③依赖支付中心（不在我方控制内，但项目其它链路同样依赖它做幂等，非本处特有缺口）。

### 3.3 ADR-D169 与代码不符（真正该记录的问题）

ADR-D169（`docs/domain/decisions.md:8188` 附近）写：

> 「消费时 `SELECT ... FOR UPDATE SKIP LOCKED` 防多实例重复消费」
> 「`RetryQueueConsumer` 带 `@Transactional`（消费过程原子性）」

但实际 `GateRetryQueueMapper.xml:71-79` 的 `selectPendingForConsume` 是**普通 SELECT，无 `FOR UPDATE SKIP LOCKED`**。ADR 描述的防护未落地。`@Transactional` 部分倒是按 ADR 实现了——所以报告若按字面「去事务」，等于**推翻 ADR-D169 的明确裁决**，属于跨模块契约变更，须重新裁决而非静默修改。

## 4. 残余风险（修正后仍然成立的 P2）

报告虽高估了「双扣」，但指出的事务边界问题本身真实：

1. **长事务持锁**：整批 10 笔共用一个事务，行锁从第一笔 `markProcessing` 持有到整批 commit，时长 = 10 次 HTTP RT 之和。上游对同一行的重推会串行等待，超过 Druid `remove-abandoned-timeout` 后连接被强杀、`commit` 抛 `connection closed`。
2. **回滚吞证据**：若 commit 阶段失败，整批的 `markFailed` / `markDone` 状态更新一起回滚——而远端可能已受理。表现为「钱已发起、本地队列仍 PENDING/PROCESSING」，下轮再捞；`recoverTimeout`（24h）才最终收口。这不是双扣，但是**重复发起 + 超长不一致窗口**。
3. **逐笔 catch 不回滚**：单笔异常后继续下一笔，异常笔的 `PROCESSING` 标记不回滚也不重试，靠 `recoverTimeout` 兜。

评级建议：**P2（架构债）**，不是 P1 资损。理由：无多实例、有三道幂等、触发频率低（每5分钟、单副本、concurrent=1），但违反 §6 的行锁禁令且有可观测的不一致窗口。

## 5. 三个修法方案对比

| 方案 | 做法 | 优点 | 缺点 / 风险 | 与 ADR-D169 关系 |
|---|---|---|---|---|
| **A：仅补 SKIP LOCKED（最小）** | `selectPendingForConsume` 加 `FOR UPDATE SKIP LOCKED`，事务与逐笔逻辑不动 | 零业务语义改动；让实现符合 ADR 描述；防未来多副本 | 不解决长事务持锁与回滚吞证据；SKIP LOCKED 要求 Oracle 11g+（本项目 Oracle，OK） | 补齐 ADR 未落地部分，不推翻裁决 |
| **B：领取/投递分离，RPC 移出事务（推荐）** | `consumeBatch` 去事务；① 每笔 `markProcessing` 独立短事务提交（或改 lease 抢占）；② 提交后再调 RPC；③ 回写用现有 `markDone`/`markFailed` 的 `WHERE STATUS='PROCESSING'` CAS；④ 异常笔显式 `markFailed` 留证据 | 根治行锁放大与回滚吞证据；符合 §6；保留逐笔幂等 | 改动较大；推翻 ADR-D169「消费过程原子性」裁决，须走 ADR 修订；需补并发单测 | **需修订 ADR-D169**：把「消费过程原子性」改为「逐笔领取原子 + RPC 外置 + CAS 收口」 |
| **C：按报告字面去 `@Transactional`** | 直接删注解，其余不动 | 一行改动 | 领取失去原子性；`markProcessing` 与后续 CAS 之间无保护，多副本下确实可能重复领取；且未补 SKIP LOCKED，比 B 更脆弱 | 破坏 ADR 裁决却没换来完整收益 |

**推荐：方案 B + 同步修订 ADR-D169**。若只想做零争议改善，先做方案 A（补 SKIP LOCKED），把 B 作为独立 ADR 变更推进。

## 6. 建议的后续动作（供裁决）

1. **登记**：本评估作为重档跟踪（`IT-099`），索引行标注「评估完成，P1→P2，待裁决修法」。
2. **文档订正**：无论是否改代码，先把 ADR-D169 中「FOR UPDATE SKIP LOCKED」改为「实际未实现，见 IT-099」，消除 ADR 与代码不符——这是**确定性缺陷**，与修法选择无关。
3. **修法裁决**：由用户在 A / B / C 中选；涉及资金链路，改代码前须人工复核安全合规性（AGENTS.md §4.2）。
4. **验证**：若选 B，补并发单测（两实例领取同一行 → 第二笔 `markProcessing`/CAS 返 0 → 不出网），跑 `mvn -o -pl gate-txn-pay-server -am test-compile`（JDK21 绝对路径，见 IT-098 编译坑）。

## 7. 流程符合度自评

- [x] 未改任何代码 / 配置 / DDL（用户裁决：先出评估）
- [x] 所有结论给出文件与行号证据，可逐条复核
- [x] 明确区分「报告属实部分」（§2）与「报告未核实部分」（§3）
- [x] 明确标注与既有 ADR 的冲突，未擅自推翻裁决
- [x] 结论相对报告做了评级修正并给出理由，未照搬 P1
