# 状态机（qd-itp）

本项目**不使用状态机框架**（Spring Statemachine 等），统一用「枚举 + 迁移白名单 + CAS UPDATE」三件套。
否决理由见 `decisions.md` ADR-D9 / ADR-D10。

## 一、在跑的状态机清单

| # | 状态机 | 载体 | 状态取值 | CAS | 白名单 | 枚举 |
|---|---|---|---|---|---|---|
| 1 | 解约请求 | `APP_TERMINATION_REQUEST.TERMINATION_STATUS` | `PENDING` / `SCANNING` / `SUCCESS` / `FAILED` | ✅ 7 条 | ✅ 枚举 + mapper XML 注释 | ✅ `model/domain/TerminationStatus`，经 `TerminationStatusTransition` 接线（ADR-D47） |
| 2 | 解约通知 | 同表 `NOTIFY_STATUS` + `NOTIFY_RETRY_COUNT` | `PENDING` / `SUCCESS` / `FAILED` | 部分 | 部分 | ❌ |
| 3 | 账户有效性 | `USER_ITP_REG_INFO.DEL_YN` | `1`=有效 / `0`=已注销（**极性反直觉**）；DDL 允许 NULL，**NULL 是幽灵态** | ✅ | ✅ | **刻意不加**（见已知缺口） |
| 4 | 通道签约 | `APP_PAY_SIGN_INFO.SIGN_STATUS` | `NOT_SIGNED` / `SIGNED` / `UNSIGNED` / `FAILED` | ✅ 4 条 | ✅ 写在 mapper XML 注释里 | ✅ `model/domain/SignStatus`，经 `SignStatusTransition` 接线 |
| 5 | 对账批次 | `RECON_BATCH.STATUS` 等 5 个枚举 | 8 态线性图 | ✅ | ✅ | ✅ **全仓唯一 0 字面量的模块** |

**参考实现优先级：`recon-server/src/main/java/com/chinasofti/huateng/recon/model/ReconBatchStatus.java`（枚举写法）+ `pay-sign-server/src/main/resources/mapper/AppTerminationRequestMapper.xml`（CAS 写法与注释密度）。新写状态机 MUST 照抄这两处。**

### 已知缺口

- ~~**#4 的状态更新没有 CAS**~~ **已于 2026-09-11 补齐**：`PaySignInfoMapper.xml` 新增 4 条 CAS（`markSigned` / `markSignFailed` / `markUnsigned` / `reactivateForResign`）+ 回查用 `selectSignStatusBySeq`，迁移白名单写在同处 XML 注释里。两个写状态的调用点已改造：`ContractDomainServiceImpl.removeSignAgreement`（`SIGNED -> UNSIGNED`，CAS 返 0 行时回查，已是 `UNSIGNED` 判幂等成功、其余返 409）与 `ContractDomainServiceImpl.applyGatewayStatus`（IF8A-22 查询接口按网关状态迁移，白名单不允许的迁移**只告警不落库**并把本地真实状态回给调用方；**2026-09-15 前宿主是 `PaySignWorkflow`，该类已删除，见 ADR-D87**）。`updateBySeq` 保留但**只用于回填 `PAY_ACCOUNT_ID` / `PAY_AGREEMENT_NO` 等非状态字段**，**NEVER 再用它改 `SIGN_STATUS`**。
- ~~**`APP_PAY_SIGN_INFO.REQUEST_SIGN_SEQ` 无任何索引**~~ **已于 2026-09-11 补齐**：`CREATE UNIQUE INDEX UK_APPSI_REQUEST_SIGN_SEQ ON APP_PAY_SIGN_INFO (REQUEST_SIGN_SEQ)`，已写入 `pay-sign-server/src/main/resources/sql/pay-sign-schema.sql` 并在唯一目标库 `AFCITPDB` 执行（执行前实测 20 行 / 20 distinct / 0 NULL，执行后 `USER_INDEXES` 三条索引全 `VALID`）。回滚 `DROP INDEX UK_APPSI_REQUEST_SIGN_SEQ`。
- **测试库 20 行 `APP_PAY_SIGN_INFO` 全是 `SIGNED`**，`UNSIGNED` / `FAILED` / `NOT_SIGNED` 零行 ⇒ 解约成功后 `SIGN_STATUS` 很可能从未被回写。因此新加的 4 条 CAS **只做过「SQL 语义级」验证**（2026-09-11 用一行合成数据把 7 条迁移断言逐条跑过、跑完删除，详见 `decisions.md` ADR-D12），**没有经过 MyBatis + Druid 的服务级真实业务触发**；服务级目前只验证到「启动不报 mapper 绑定错」。
- **#1 已枚举化（2026-09-12 ADR-D47）；#2 `NOTIFY_STATUS` 仍是 `private static final String` 常量。**
  **本条此前记载的「`PaySignWorkflow:87` 定义了 `STATUS_SUCCESS`，同文件里却有 10 处裸 `"SUCCESS"`，说明靠自觉用常量无效」这个结论 NEVER 回退，但那 10 处的构成一直被误读，据此推不出「解约状态机没收口」。** 2026-09-12 复核，`pay-sign-server/src/main/java` 里 `"SUCCESS"` 共 **14 个匹配行**，逐处归属：
  - **`TERMINATION_STATUS`（解约状态机）：0 处。** 四个类的常量已全部改成从枚举派生（`TerminationStatus.SUCCESS.name()` 等）。
  - `PaySignWorkflow` 10 行 —— `CHANNEL_SYNC_SUCCESS` 常量声明（`:170`）1 处；**支付 / 退款状态** 7 处（`:741` / `:843` / `:1579` / `:2138` 两个 / `:2286` / `:2287`）；**支付平台回调报文的 `status` 字段** 2 处（`:1104` / `:1231`）；`PAY_CALLBACK_LOG.HANDLE_STATUS` 1 处（`:2276`）。**注：该类已于 2026-09-15 拆分删除（ADR-D87），这 10 行随各自业务组落到 `ContractDomainServiceImpl` / `PaymentDomainServiceImpl` / `CallbackDomainServiceImpl`；上面的行号只对当时的快照有效，要重新计数 MUST 现 grep 这三个类，NEVER 直接引用这些行号。值域归属的结论不变。**
  - `TerminationProcessor:68` `CALLBACK_STATUS_SUCCESS` —— **支付平台报文取值，刻意保留字面量**：它与我方状态机同名但值域由对方定义，跟着枚举改会在对方改协议时静默错配。
  - `AppNotifyServiceImpl` 3 处 —— 出向通知报文的 `terminationResult`（`:211`）与 `NOTIFY_STATUS`（`:443` / `:455`）。
  **结论：剩下的 14 处分属四个互不相同的值域（支付状态 / 回调报文 / `NOTIFY_STATUS` / `CHANNEL_SYNC_STATUS`，后者还多一个 `MANUAL`），NEVER 拿一个枚举去统一它们** —— 那是把「字面量相同」当成「概念相同」，正是判据里最典型的错。要继续收口 MUST 逐个值域单独立项。
- **#4 的枚举已接线（2026-09-12 ADR-D40 收口；此前记载的「类已建、主代码零引用」已作废）。**
  - `model/src/main/java/com/chinasofti/huateng/model/domain/SignStatus.java`（76 行）承载 `ALLOWED` 白名单、`canTransitTo`、`isTerminal` 与宽松解析 `parseOrNull`。
  - 主代码引用点 **2 处**：`ContractDomainServiceImpl.removeSignAgreement` 与 `PaySignWorkflow.applyGatewayStatus`，都是经 `SignStatusTransition.classify(...)` 传入目标态。
  - **但 `canTransitTo` 在主代码里仍然没有调用点，这是刻意的**，理由见 §二③ 约束 2：白名单的权威在 CAS 的 WHERE，枚举只负责解析与文档化。**NEVER 因为「枚举有个方法没人用」就去加 CAS 前置校验。**
  - 同目录的 `SyncStatus` 接线于 `account-server/.../PhoneChangeServiceImpl.java:12`（3 处使用）。
- **ArchUnit 门禁已落地（本条此前记载「仍未落地」已作废）。** `pay-sign-server/pom.xml:113~117` 有 `archunit-junit5:1.3.0`（`test` scope），`src/test/.../arch/SignStatusArchTest.java` 3 条规则：①`updateBySeq` 只允许 `ContractDomainServiceImpl` 调（**2026-09-15 随签约组搬迁改的宿主类名，规则语义一字未变**，见 ADR-D87）；②4 条 CAS 只允许 `service.impl` 包调；③禁 4 个 Java EE `javax..` 包（**刻意不禁整个 `javax..`**，`javax.crypto` / `javax.net` / `javax.sql` 是 JDK 自带、签名链路正当使用）。同目录 `TerminationStatusArchTest` 另有 3 条（ADR-D47）：`updateStatus` / `updateFailReason` 无调用方、`COMPLETE_TIME` 只在 CAS 内写、CAS 只允许 `service.impl` 调。2026-09-12 实测 `mise exec -- mvn -o test -pl pay-sign-server -Djkube.skip=true` → **36/36 通过**、BUILD SUCCESS（此前记载的「9/9」是 ADR-D40 当时的数字，非现状）。
- **§二③ 的调用模板已落地（2026-09-12 ADR-D40；本条此前记载「仍然只是伪代码」已作废）。** 收口成 `SignStatusTransition.classify(...)`，两个调用点都改为经它判定。**注意收口的范围比原伪代码小**：原伪代码里的 `TransitResult` / `IllegalStateTransitionException` / `transit(key, from, to)` / `logMapper.insertTransition` **全部没有实现，也不会实现** —— 前三个与仓库里 4 条各带副作用列的 CAS 形态不符，最后一个依赖的「迁移日志表」本项目不存在。详见 §二③ 的三条 NEVER。

- **#3 `DEL_YN` 刻意不加 Java 枚举，但 SQL 侧已收口（2026-09-12 ADR-D41 裁决）。** 不加枚举的理由：①能被 Java 枚举替换的字面量只有 ~4 处，而 10+ 处 `DEL_YN = 1` 写在 mapper XML 里，**枚举对它们完全不可达**；②`UserItpRegInfo.isActive()` / `isCanceled()` 已经是 Java 侧唯一判断点（8 + 1 处调用），语义收口已完成，枚举只是换一层壳；③白名单的权威在 CAS 的 WHERE（`updateCancelByThirdUserId` 是 `WHERE THIRD_USER_ID=? AND DEL_YN=1`），与 §二③ 约束 2 及 ADR-D40 的裁决一致。**但「枚举不可达」不等于「放着不管」**：同日已把 `UserItpRegInfoMapper.xml` 里散落的 10 处 `DEL_YN = 1` + 1 处 `DEL_YN = 0` 收口成两个 `<sql>` 片段 `Del_Yn_Active_Filter` / `Del_Yn_Canceled_Filter`（命名沿用 gate-txn-pay-server 的 `Debit_Result_Filter` 约定），字面量从 11 处降到 2 处。两条边界：`set DEL_YN = 0` 是写入值不是过滤条件、无对应片段；`selectAnyByThirdUserIdAndCardIdAndCardType` 与 `selectAnyListByThirdUserIdForUpdate` **刻意不带过滤，NEVER 给它们加 include**（销户后的兜底查询，加了残留通道就再也清不掉）。语义由三个测试锁死：`UserItpRegInfoActivenessTest`（4 例，实体侧，**NEVER 把 `isCanceled()` 简化成 `!isActive()`**）、`UserItpRegInfoMapperSqlTest`（7 例，离线解析 mapper XML 断言渲染出的 SQL，含 `for update` 未被删、无语句渲染出 `where and`、整份 XML 的 `DEL_YN` 等值判断恰好 3 处；**反向断言一律用大小写无关正则 `DEL_YN\s*=`，NEVER 退回 `contains("DEL_YN = 1")`**）、`AccountArchiveServiceTest`（4 例，钉住 `Propagation.MANDATORY` 与「归档必须包在 transactionTemplate 里」）。这 4 条新护栏都做过变异验证。
- **#3 的 NULL 是幽灵态（待 MCP 恢复后验证）。** `DEL_YN` 的 DDL **没有 NOT NULL、没有 DEFAULT、没有 CHECK**，而 `isActive()` 与 `isCanceled()` 对 NULL **都返回 false**。因此一行 `DEL_YN IS NULL` 的记录：对所有 `DEL_YN = 1` 的查询不可见 ⇒ 用户既登不进也无法重新开户；`deleteCanceledByThirdUserId`（条件是 `DEL_YN = 0`）删不掉；`AccountArchiveServiceImpl.archiveIfLastChannelRemoved` 的 `allMatch(isCanceled)` 恒 false ⇒ 归档永久卡住。**没有任何自愈路径。** 验证 SQL：`SELECT COUNT(*) FROM USER_ITP_REG_INFO WHERE DEL_YN IS NULL`；为 0 才补 `NOT NULL` + `DEFAULT 1`，非 0 则先定数据修复口径。**NEVER 在验证前就动 DDL。**

## 二、三件套规范

三件套缺一不可：只有①是文档；只有①②是单机正确、并发失效；只有③能跑但没人看得懂迁移图。

### ① 枚举 + 迁移白名单（放 `model/domain/`）

```java
/**
 * 通道签约状态机。载体 APP_PAY_SIGN_INFO.SIGN_STATUS。
 *
 * 流转（白名单）：
 *   NOT_SIGNED -> SIGNED | FAILED
 *   FAILED     -> SIGNED | NOT_SIGNED
 *   SIGNED     -> UNSIGNED
 *   UNSIGNED   -> NOT_SIGNED（复位重签）
 *
 * NEVER 允许 UNSIGNED -> SIGNED：已解约通道被迟到的签约回调覆盖，
 * 会让 APP 显示通道有效而渠道侧协议已注销。
 */
public enum SignStatus {
    NOT_SIGNED, SIGNED, UNSIGNED, FAILED;

    private static final Map<SignStatus, Set<SignStatus>> ALLOWED = Map.of(
        NOT_SIGNED, EnumSet.of(SIGNED, FAILED),
        FAILED,     EnumSet.of(SIGNED, NOT_SIGNED),
        SIGNED,     EnumSet.of(UNSIGNED),
        UNSIGNED,   EnumSet.of(NOT_SIGNED));

    /** 只做快速失败与错误提示，NEVER 当作并发保证。并发保证是 ② 的 CAS。 */
    public boolean canTransitTo(SignStatus t) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(t);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
```

### ② CAS UPDATE —— 唯一的并发保证

```xml
<update id="markUnsigned">
    update APP_PAY_SIGN_INFO
    set SIGN_STATUS = 'UNSIGNED',
        TERMINATION_TIME = #{terminationTime,jdbcType=TIMESTAMP}
    where REQUEST_SIGN_SEQ = #{requestSignSeq,jdbcType=VARCHAR}
      and SIGN_STATUS = 'SIGNED'
</update>
```

**SQL 正文 NEVER 写 `--` 或 `/* */` 注释**（Druid WallFilter，`resource/micro/sql-datasource/src/main/resources/sql.properties:40`；`dm.properties:5` 关了 WallFilter，所以该缺陷**只在 Oracle 生产暴露**）。说明写在 `<!-- -->` 里。

### ③ 调用模板 —— 已落地为 `SignStatusTransition`（ADR-D40）

**位置**：`pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/domain/SignStatusTransition.java`，测试 `src/test/.../domain/SignStatusTransitionTest.java`（5 例）。

它只做一件事：**解读 CAS 的结果**，产出 `DONE` / `IDEMPOTENT` / `CONFLICT` 三态 + 回查到的原始状态。

```java
SignStatusTransition.Result transit = SignStatusTransition.classify(updatedRows, SignStatus.UNSIGNED,
        () -> paySignInfoMapper.selectSignStatusBySeq(key));
if (transit.isConflict()) { /* 各入口自行决定：拒绝 / 只告警 */ }
```

三条设计约束，**NEVER 改**：

1. **`currentStatusLoader` 只在 `updatedRows == 0` 时被调用。**「CAS 命中就不回查」是规则的一部分，改成先无条件回查等于给每次成功迁移加一次多余 DB 往返。`SignStatusTransitionTest` 用计数器锁住了这条。
2. **NEVER 在本类里加 CAS 前的白名单校验**（`SignStatus.canTransitTo`）。权威白名单是 4 条 CAS 自己的 WHERE；调用方手里的「当前状态」来自更早一次 select、随时可能过期，用过期值提前拦一道只会**误拦**，且比 CAS 返 0 行更难排查。**枚举在本项目的职责就限于解析与文档化，并发保证一律由 SQL 承担**。
3. **NEVER 扩成「通用状态机引擎」。** 4 条 CAS 各带不同的业务副作用列（`SIGN_TIME` / `TERMINATION_TIME` / `PAY_ACCOUNT_ID` / `PAY_AGREEMENT_NO`），归一成一条 `transit(key, from, to)` 会把这些列丢掉 —— **本节此前那段伪代码就是这么写的，已否决**。

**`CONFLICT` 分支 NEVER 静默忽略**，但**两个调用点的处置刻意不同**，这是本设计不做成「统一处置」的原因：

- `ContractDomainServiceImpl.removeSignAgreement`（状态变更型接口）：冲突 **MUST 拒绝并返 409**。
- `ContractDomainServiceImpl.applyGatewayStatus`（IF8A-22 只读查询）：冲突**只告警不落库、不对上游报错**，并把本地真实状态回给调用方。这里若照抄 409，一次只读查询会因状态不一致而失败。

「返回 boolean / 只打日志就放行」是本项目已发生事故的形态（历史样例 `PaySignWorkflow.removeAccountPayChannel` 的 `catch → return false`；**该方法与所在类已于 2026-09-15 删除，事故形态本身仍然有效**，见 ADR-D87）。

## 三、适用边界

**适合**（有真实的状态所有权）：解约请求、通道签约、账户有效性、票据生命周期（`DAILY_TICKET_INSTANCE`：`INIT` / `ACTIVATED` / `USED`）、卡池批次、后付费扣款、对账批次。

**不适合，硬套会出事故**：

1. **collect-pay 的支付状态** —— 支付结果由支付中心决定，我方是记录方。回调可能乱序、重复、先成功后处理中。严格白名单会拒掉合法回调。这里只保护「终态不可回退」，中间态之间允许任意跳转。
2. **过闸交易流水**（`QRCODE_TXN_DETAIL`、`GATE_TXN_PAY` 的 txn 部分）—— 是事件流水，一行代表一次已发生的过闸，没有生命周期。给它套状态机是把事件误当实体。注意区分：同表的 `DEBIT_STATUS` 是实体状态，适合。

## 四、待处理项

- ~~`QRCodeStatusMapper.xml` 的 `MERGE INTO` 无条件覆盖 `CODE_STATUS` / `GATE_STATUS`，乘车码状态是「最后写入者赢」~~ **过闸链路已于 2026-09-14 补 CAS（观察期，尚未拒绝请求）**。形态与 §二② 的 `update ... where 前置状态` 不同，是**带 CAS 条件的 `MERGE`**：`QRCodeStatusMapper.upsertWithCas` 的 `ON` 条件写成 `T.CARD_ID = S.CARD_ID AND T.TXN_SEQ = #{expectedTxnSeq}`，序号已被推进时 UPDATE 分支命中 0 行；**INSERT 分支（首次开卡）不受 CAS 影响**，这是它必须写成 `MERGE` 而不能拆成纯 `update` 的原因。调用点 `GateTicketWriter.saveTxnAndAdvanceStatus(detail, nextStatus, expectedTxnSeq)`（第三参传 `currentStatus.getTxnSeq()`，由 `GateTicketHandler` 传入），CAS 返 0 行时**三级降级**：回查库内状态 → 命中即以库内状态为准并打 `IF1A-01 CAS upsert 未命中` WARN → 回查为空才降级走旧的无条件 `upsert` 兜底。
  **当前是观察期，冲突不拒绝请求**，与 §二③ 那两个调用点的 `CONFLICT` 处置都不同（既不返 409、也不只告警不落库，而是「按库内真实状态继续走完过闸应答」）——闸机侧拿不到「稍后重试」这种语义，拒绝等于把乘客关在闸机里。**改成拒绝的前提是先看 WARN 频率**：按 `IF1A-01 CAS upsert 未命中` 统计，若绝大多数是 AGM 超时重发（同 `cardId` 短时间内多次、`actualTxnSeq` 已等于期望值 +1），说明 CAS 正在正确挡住重复推进、保持观察即可；若出现 `actualTxnSeq` 跳变多格或与任何一次上送都对不上，才需要立项改成拒绝 + 工单。
  **旧的无条件 `upsert` 保留不删、NEVER 删**：运营端 `updateCodeStatus`、`registerRideStatus`（开卡复位）与本方法的兜底分支都还在用它。
  遗留：`USE_COUNT` / `TXN_SEQ` 仍是**相对增量**（`buildNextStatus` 取 `current + 1`），CAS 只保证「基于同一个 `TXN_SEQ` 的并发写只有一个赢」，**没有**把增量改成绝对值，`docs/business/ride-code.md` 那条「upsert 写的是相对增量」仍然成立。
- **`QRCODE_STATUS` 的 `CODE_STATUS` + `GATE_STATUS` 正交性已定义（2026-09-14）：不正交，`GATE_STATUS` 是 `CODE_STATUS` 的冗余输入快照，NEVER 把它当第二台状态机、NEVER 给它加 CAS 或白名单。** 依据（代码 + 库内实测，非推断）：
  - **写它的只有两处，且都不是独立决策**：`GateTicketHandler.buildNextStatus:478` 直接写 `request.getTrxType()`（闸机上送的原始交易类型 `01`/`02`/`03`/`04`/`99`），同一方法 `:472` 的 `resolveCodeStatus(trxType, excessFareType, adviceOpt)` 才是真正的状态机——`CODE_STATUS` 由 `trxType` **加上** `adviceOpt` 分支推导（`018→04`、`005→08`、`006→09`）。因此 `GATE_STATUS` 承载的信息是 `CODE_STATUS` 的**真子集**。另一处是 `TicketRideStatusServiceImpl:95` 开卡复位写配置默认值 `ticket.default-gate-status`。
  - **读它的地方是零**：全仓 `getGateStatus()` 只有实体自身与 `toString` 引用，mapper 无任何 `WHERE GATE_STATUS`，也不出现在任何应答 DTO 或 `/page/**` 查询里。它是**只写列**。
  - 库内实测（2026-09-14，`AFCITPDB`，84 行）：`(GATE_STATUS, CODE_STATUS)` 组合为 `('02','05')`20 / `('01','04')`7 / `('03','06')`3 —— 与上面的派生关系完全一致；`('0000','03')`50 / `('00','03')`3 是开卡态。**同时存在 `00` 与 `0000` 两种开卡编码**，而仓库默认值是 `00`（`application.properties:15`），那 50 行 `0000` 与仓库不符（Deployment env 覆盖或历史版本所致），**要动这列 MUST 先查线上 env，NEVER 假定仓库值就是线上值**。
  - 唯一一行自相矛盾：卡 `0178229100072732` 是 `CODE_STATUS=03` / `GATE_STATUS=01` / `TXN_SEQ=0` / `USE_COUNT=26`。它是 `registerRideStatus` 复位过的痕迹，但复位**不重置 `USE_COUNT`**（`:87` 的 `setUseCount(0)` 只在新建行时执行），`GATE_STATUS` 也停在旧值。**这行是「复位不是全字段一致」的物证，不是并发覆盖的物证**，排查时 NEVER 混为一谈。
  - 连带结论：真要给乘车码上状态机，**载体是 `CODE_STATUS` 单列**；`GATE_STATUS` 的正确归宿是要么删（需先确认 ACC / 运营侧无人读库）、要么明确降级为「最近一次上送的 `trxType` 快照」并在建表注释里写死。**NEVER 因为它叫 `*_STATUS` 就补一套白名单** —— 那是把「字面量像状态」当成「概念是状态」，与 §一「已知缺口」里 14 处 `"SUCCESS"` 的错误同型。
- `DAILY_TICKET_ORDER` 的 `ORDER_STATUS` + `PAY_STATUS` 仍未定义正交性（其 CAS 用复合前置条件，见 `DailyTicketOrderMapper.xml:119-134`）。
- `GATE_TXN_PAY.DEBIT_STATUS` 只 grep 到 `PROCESSING` / `SUCCESS`，**未命中失败态**。若确认无 `FAILED`，则「重试 N 次转人工」当前无法表达。需 `SELECT DISTINCT` 确认。
- `PAY_REFUND_DETAIL.REQUEST_COUNT` / `PAY_TXN_DETAIL.REQUEST_COUNT` 只见 `+1` 累加，WHERE 未见上限过滤；对比 `AppTerminationRequestMapper.xml:121` 的 `NVL(NOTIFY_RETRY_COUNT,0) < #{maxRetryCount}` 才是正确形态。需确认扣款/退款是否有重试刹车。
- 达重试上限后需要**异常工单**接住（有进无出、有出无进、扣款卡单、重试超限、回调无法处理、金额不符）。当前状态（2026-09-11）：
  - 账户域已有工单实体 `ACCOUNT_EXCEPTION_TICKET`（`account-server/src/main/resources/sql/account-server-schema.sql`，已在 `AFCITPDB` 建好），唯一键 `(TICKET_TYPE, BIZ_KEY)` 保证同一业务键只开一单，靠 `DuplicateKeyException` 兜底幂等。首个接入方是签约展示账号同步重试超限（`AccountApplicationServiceImpl.openTicketIfRetryExhausted`，`TICKET_TYPE='SIGN_SYNC_RETRY_EXHAUSTED'`）。
  - **仍然没有的**：处理流程（关单只能手工 UPDATE）、后台入口、其它域接入（支付域的 `PAY_CALLBACK_LOG.HANDLE_STATUS = 'MANUAL'` 标记位仍是唯一出口，见 `PayCallbackLogMapper.xml:61`）。**这张表是 account-server 独占的，其它域 NEVER 直接写**——跨域要开单需先决定是各域自建同构表还是提取公共服务。
