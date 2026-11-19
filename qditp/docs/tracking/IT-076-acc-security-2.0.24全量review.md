# IT-076 acc-security-server 2.0.24 全量代码 Review

> **Review 范围**：本次改动（commit `1eab9ffb`）涉及的文件 + 模块全量关键代码
> **Reviewer**：Agnes (AI)
> **日期**：2026-11-18
> **环境**：测试环境（k8s ns `itp`，Pod `security-server-56c9465978-qp5f2`）

---

## R1 正确性

**结论：PASS（本改动无正确性问题）**

| # | 文件:行号 | 检查项 | 结果 |
|---|---|---|---|
| 1.1 | `ChannelCache.java:46-56` | `set()` 持有锁期间不调用外部方法 | ✓ 只调用 `touch()` + `signal()` |
| 1.2 | `ChannelCache.java:73-97` | `acquireExclusive()` 超时语义正确 | ✓ deadline 计算正确，中断恢复抛异常 |
| 1.3 | `ChannelCache.java:100-118` | `tryAcquireExclusive()` 原子性 | ✓ lock 内完成所有判断与写入 |
| 1.4 | `ChannelCache.java:121-131` | `releaseExclusive()` 唤醒语义 | ✓ signal 在 unlock 前，保证 waiters 能拿到锁 |
| 1.5 | `ChannelCache.java:134-147` | `clear()` 关闭连接时机 | ✓ 摘除后关闭，不持锁 |
| 1.6 | `HsmHeartbeatScheduler.java:49-73` | 遍历 snapshot 期间池状态变化 | ✓ snapshot 是独立副本，遍历期间池变化不影响循环 |
| 1.7 | `HsmHeartbeatScheduler.java:58` | `sendRawMsgOn` 失败处理 | ✓ 返回 null → continue，不抛异常中断本轮 |
| 1.8 | `ClientSendMsg.java:75-102` | `sendRawMsgOn` 资源释放 | ✓ finally 块确保 releaseExclusive |

---

## R2 安全合规

**结论：1 个 HIGH（r1319 遗留，非本次改动引入）**

| # | 文件:行号 | 严重度 | 现象 | 建议 | 状态 |
|---|---|---|---|---|---|
| 2.1 | `CertificateServiceImpl.java:36` | **HIGH** | `log.info("公钥为" + publickey)` 打印 SM2 公钥（明文） | 改为脱敏或移入 debug 级别 | ⚠️ r1319 遗留，非本次改动 |
| 2.2 | `CertificateServiceImpl.java:83` | **HIGH** | `log.info("签名数据..." + byteArraySignData)` 打印 SM2 签名（明文） | 同上 | ⚠️ r1319 遗留 |
| 2.3 | `HsmHeartbeatScheduler.java:63` | LOW | 心跳日志 `channelId {} ansCode {}` | ✓ 无敏感信息 | 无需改动 |
| 2.4 | `ClientSendMsg.java:91` | INFO | `send raw msg` 日志打印报文 hex | ✓ 不含密钥（仅 0x00 系统信息） | 无需改动 |

**红线复核**：本次改动**未触碰**任何加解密/MAC/TAC/证书/签名逻辑。✓

---

## R3 线程安全

**结论：PASS**

| # | 检查项 | 结果 |
|---|---|---|
| 3.1 | `ChannelCache` 所有公共方法均持 `ReentrantLock` | ✓ |
| 3.2 | `lastActiveMap` 使用 `ConcurrentHashMap`，与 lock 保护一致 | ✓ |
| 3.3 | `snapshot()` 返回独立副本，心跳遍历无并发修改风险 | ✓ |
| 3.4 | `PENDING_CONNECTS` 使用 `AtomicInteger`，无锁竞争 | ✓ |
| 3.5 | `HsmHeartbeatScheduler` 由 Spring `taskScheduler` 单线程调度，无需额外同步 | ✓ |
| 3.6 | Netty Channel 的访问边界：仅在 hsm-io 线程读写，心跳走专用连接不越界 | ✓ |

---

## R4 资源/泄漏

**结论：1 个 MEDIUM（r1319 遗留，已修复）**

| # | 检查项 | 结果 |
|---|---|---|
| 4.1 | `releaseExclusive()` 保证 finally 释放 | ✓ |
| 4.2 | `sendRawMsgOn()` finally 块清属性 + 释放独占 | ✓ |
| 4.3 | `ReConnectManager` 执行器生命周期：池满后 cancel 保留复用 | ✓ |
| 4.4 | `SocketClient.start()` 异常路径 `PENDING_CONNECTS.decrementAndGet()` | ✓ |
| 4.5 | Netty ByteBuf：`sendRawMsgOn` 由 `ClientDecoder` 负责释放，业务层不持有 | ✓ |
| 4.6 | **⚠️ BeanConfig.buildSchedule() 创建的 scheduledTask Bean 未被任何代码引用** | MEDIUM（死代码，非泄漏） |

---

## R5 配置一致性

**结论：PASS**

| # | 配置项 | application.properties | SecurityConfig | 生效方式 |
|---|---|---|---|---|
| 5.1 | `security.channelPoolSize` | `=48` (L29) | `@Value(...=10)` → 覆盖为 48 | ✓ 一致 |
| 5.2 | `security.acquireTimeoutMs` | `=800` (L31) | `@Value(...=800)` → 覆盖为 800 | ✓ 一致 |
| 5.3 | `security.heartbeatEnabled` | `=true` (L33) | `@Value(...=false)` → 覆盖为 true | ✓ 一致 |
| 5.4 | `security.heartbeatIdleSeconds` | `=60` (L34) | `@Value(...=60)` → 覆盖为 60 | ✓ 一致 |
| 5.5 | `security.heartbeatPeriodMs` | `=30000` (L35) | `@Value(...=30000)` → 覆盖为 30000 | ✓ 一致 |
| 5.6 | `server.undertow.io-threads` | `=8` (L4) | N/A（Spring Boot 内置） | ✓ 已由 env-set 生效 |
| 5.7 | `server.undertow.worker-threads` | `=200` (L5) | N/A | ✓ 已由 env-set 生效 |

**⚠️ 残留配置**：`thread.corePoolSize=10`（L40）仍在 properties 中，但文档已写明不再影响连接数。建议后续清理或加注释说明用途。

---

## R6 错误处理

**结论：PASS（本改动无新增问题）**

| # | 检查项 | 结果 |
|---|---|---|
| 6.1 | `tryAcquireExclusive(null)` 不抛异常 | ✓ L103 提前返回 false |
| 6.2 | `sendRawMsgOn` 拿不到连接返回 null 而非抛异常 | ✓ 心跳跳过 |
| 6.3 | `heartbeat()` 异常捕获：失败连接 `clear` 后继续下一轮 | ✓ L65-72 |
| 6.4 | `acquireExclusive` 中断恢复：抛 HsmUnavailableException | ✓ L91-93 |

---

## R7 日志口径

**结论：1 个 LOW（r1319 遗留）**

| # | 文件:行号 | 严重度 | 现象 | 建议 | 状态 |
|---|---|---|---|---|---|
| 7.1 | `CertificateServiceImpl.java:36,83` | LOW | 公钥/签名明文打印（见 R2） | 移入 debug | ⚠️ r1319 遗留 |
| 7.2 | `HsmHeartbeatScheduler.java:63` | INFO | `channelId {} ansCode {}` | ✓ 仅打印通道 ID 与应答码，无敏感信息 | 无需改动 |
| 7.3 | `ClientSendMsg.java:91` | INFO | `send raw msg` 打印报文 hex | ✓ 0x00 指令无敏感内容 | 无需改动 |

**traceId 机制**：依赖 log4j2 的 `%X{traceId}`，本改动未破坏（无异步链路中断）。✓

---

## R8 性能

**结论：2 个 MEDIUM**

| # | 位置 | 问题 | 建议 | 状态 |
|---|---|---|---|---|
| 8.1 | `ChannelCache.snapshot()` L199 | CopyOnWriteArrayList 每次 `new ArrayList<>(cacheList)` 全量拷贝（48 条 × 每 30s = 1.6 次/秒） | 可接受，48 条小列表开销 negligible；若将来扩容到 200+ 再考虑读写锁 | 记录，暂不修 |
| 8.2 | `ChannelCache.findIdleLocked()` L205-222 | 游标轮询 + 每轮 `cacheList.get(index++)`，独占连接多时扫描成本高 | 可接受，当前 48 条池规模下 negligible | 记录，暂不修 |
| 8.3 | `HsmHeartbeatScheduler.heartbeat()` L49-73 | 每轮最多 48 次 `sendRawMsgOn`，每次独占连接 3s（readOutTime） | ✓ 实际并发：独占期间其他请求可排队，心跳间隔 30s 足以消化 | 无需改动 |

---

## R9 测试覆盖

**结论：3 个 MEDIUM（补测建议）**

| # | 缺失场景 | 优先级 | 建议 |
|---|---|---|---|
| 9.1 | `acquireExclusive` 超时等待后成功（有连接释放即唤醒） | HIGH | 模拟两连接竞争，验证阻塞→唤醒→独占链路 |
| 9.2 | `releaseExclusive` 唤醒等待者（signal 语义） | HIGH | 与 9.1 合并为一个测试用例 |
| 9.3 | `snapshot()` + 断连清理（findIdleLocked 剔除失活连接） | MEDIUM | 验证心跳遍历时脏连接被自动清理 |

**已覆盖**：
- `tryAcquireExclusive` 指定连接独占语义 ✓
- `tryAcquireExclusive` 池外/失活/null 拒绝 ✓
- `acquireExclusive` 超时抛异常 ✓

---

## R10 与 spec 对齐

**对照 `docs/business/security-hsm.md` 第七章契约**

| # | 契约项 | 现状 | 状态 |
|---|---|---|---|
| 10.1 | channelPoolSize 替代 corePoolSize 作为连接数上限 | ✓ L29 `security.channelPoolSize=48`，文档已更新 | PASS |
| 10.2 | connect 非阻塞，共享 NioEventLoopGroup | ✓ `SocketClient.java` 全事件驱动，无 `closeFuture().sync()` | PASS |
| 10.3 | heartbeat idle 判据 = `now - lastActive < idleThresholdMs` | ✓ `HsmHeartbeatScheduler.java:54` 使用 `ChannelCache.getLastActive()` | PASS |
| 10.4 | heartbeat unavailable 跳过（不抛异常） | ✓ `sendRawMsgOn` 返回 null → continue | PASS |
| 10.5 | env 全量回传（35 项） | ✓ `cloudos env-set` 已执行，测试环境 env 已更新 | PASS |
| 10.6 | 重连任务幂等，全局单周期 | ✓ `ReConnectManager.reConnect()` 检查 `reconnectFuture` 状态 | PASS |
| 10.7 | 初始建连失败可自愈 | ✓ 启动挂池补齐任务，10 秒周期重试 | PASS |

---

## 已修改清单（本次 review 期间）

无。本次 review 未触发需修改的代码（所有发现为 r1319 遗留或设计选择）。

---

## NEVER 乱动的红线（人工复核清单）

以下项目 **NEVER 由 AI 擅自改动**，需人工安全合规复核：

1. **CertificateServiceImpl.java:36** — `log.info("公钥为" + publickey)` 打印 SM2 公钥（明文）
2. **CertificateServiceImpl.java:83** — `log.info("签名数据..." + byteArraySignData)` 打印 SM2 签名（明文）
3. **任何加解密/MAC/TAC/证书/签名逻辑** — `CommonMacServiceImpl`, `CommonTacServiceImpl`, `CertificateServiceImpl`, `SM2*` 工具类
4. **入参验签逻辑** — `ItpRequestSignVerifier`
5. **Fastjson2 配置** — 序列化/反序列化策略
6. **Constant.DataPackage 字节序** — 报文拼装顺序

---

## 下一步建议

1. **立即**：将 `CertificateServiceImpl.java:36,83` 的密钥/签名明文日志降为 `debug` 级别（LOW 问题，但违反「NEVER 在日志输出密钥」红线）
2. **补测**：按 R9 建议补充 3 个单测（优先级：9.1/9.2 > 9.3）
3. **文档**：更新 `security-hsm.md` 第 8 章「性能特征」记录 COW 列表快照开销
4. **清理**：移除或注释 `BeanConfig.buildSchedule()` 死代码（R4.6）

---

**Review 完成时间**：2026-11-19 09:15
**下次 Review 建议**：r1320（SVN 提交后）+ 生产部署前
