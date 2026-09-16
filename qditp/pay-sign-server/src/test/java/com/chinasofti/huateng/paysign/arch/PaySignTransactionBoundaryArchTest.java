package com.chinasofti.huateng.paysign.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务边界不变量（批次 0 护栏）：把「哪些方法带 {@code @Transactional}」与「其中哪些会出网」
 * 冻结成两份清单，任何改动都必须显式改这里。
 *
 * <p><b>为什么要冻结而不是写成禁止规则</b>：AGENTS.md §5.2 明令「{@code @Transactional} 方法内
 * NEVER 发起任何 RPC / 网络调用」，而本模块历史上**有 6 个方法违反**（7 条出口）。写成禁止规则会立刻
 * 全红、随后被人 {@code @Disabled} 掉，护栏归零。改成「冻结现状 + 逐条注明」后：
 * <ul>
 *   <li>纯搬迁批次（抽 writeLog / 抽响应装配 / 抽网关适配 / 按能力拆真实现）**必须**让这两份清单
 *       一字不变 —— 变了就说明改了事务语义，不是重构；</li>
 *   <li>专门修事务边界的批次会让清单变短，届时 MUST 同批删掉对应条目并在 ADR 里记明。</li>
 * </ul>
 *
 * <p><b>批次 5A（2026-09-15，行为变更批次）已让两份清单同时变短</b>：{@code ContractDomainServiceImpl}
 * 的 {@code requestSignInfo} / {@code requestContractAdvisory} / {@code requestContractResult} /
 * {@code requestTermination} 四个方法摘掉了 {@code @Transactional}（各自方法头写明了理由，
 * 并点名 Druid {@code remove-abandoned-timeout} 与 2026-08-26 生产事故，**NEVER 加回**）。
 * 共同判据是那个注解的净效果为负：三条返回路径末尾的 {@code catch (Exception)} 吞掉一切、
 * 事务从来不会因业务失败回滚；事务内又只有一次审计 INSERT（{@code requestTermination} 的钱包分支
 * 甚至零 DB 写）；唯一的实际效果是把支付中心 / 账户域出网包进未提交的事务，连接被强杀时
 * 连「留证据」的那条 INSERT 一起丢弃。因此本批**不需要**新增状态列或补偿端点 —— ADR-D8 那条
 * 「移出事务 + 落同步状态 + 补偿 MUST 同批」的前提是「移出后有动作失去一致性保护」，这里没有。
 *
 * <p><b>批次 5B（2026-09-15，行为变更批次）又让两份清单同时变短一条</b>：
 * {@code PaymentDomainServiceImpl#requestRefund} 摘掉了 {@code @Transactional}
 * （方法头写明了理由并点名 Druid {@code remove-abandoned-timeout}，**NEVER 加回**）。
 * 与 5A 那四个不同，这条**适用** ADR-D8 的前提 —— 它移出事务后确实有动作失去一致性保护
 * （明细置终态与「重算原单已退总额」不再原子），因此同批补了配套补偿：
 * {@code PaymentDomainServiceImpl#compensateRefundQuery} + {@code POST /internal/payment/compensateRefundQuery}，
 * 由 web-admin Quartz 触发，回查支付中心 §3.2 refundQuery 并在收口后无条件重算一次汇总。
 * <b>删掉那个补偿端点等于只做了 ADR-D8 的前一半，NEVER 删。</b>
 *
 * <p><b>批次 5C（2026-09-15，ADR-D90）收掉了最后一条，{@link #KNOWN_TRANSACTIONAL_OUTBOUND} 自此为空集</b>：
 * {@code TerminationInternalServiceImpl#notifyTerminationFailed} 摘掉了 {@code @Transactional}。
 * 它与 5A / 5B 都不同型 —— 事务里只有**一条 CAS UPDATE**（{@code rejectPending}，一条语句同时落
 * FAILED / 失败原因 / 完成时间 / {@code NOTIFY_STATUS='PENDING'} / 轮次归零），单语句本身原子、
 * 事务保护不了任何东西；而它把 {@code asyncNotifyTerminationFailed} 的**任务提交圈在 commit 之前**，
 * 且 {@code catch} 分支是真的往外抛（事务真的会回滚），于是「APP 已收到解约失败通知、库里状态回退」
 * 是可达路径。ADR-D8 的「落同步状态 + 补偿」在这里**本来就齐了**（同步状态由那条 CAS 一并写入，
 * 补偿是在跑的 {@code sys_job} 7 解约结果通知补发 → {@code /internal/termination/compensateNotify}），
 * 所以本批是**纯摘注解、不需要新写补偿**。2026-09-15 只读核实过它的调用面：全仓库只有一个调用方，
 * 即本模块 {@code TerminationInternalController} 的 {@code POST /internal/termination/notifyFailed}；
 * {@code rpc} 的 {@code PaySignClient} 没有对应包装方法；{@code TerminationProcessor} 里同名的是它
 * 自己的**私有**方法，与本条无关。
 *
 * <p><b>仍带事务但不属于欠账的三个</b>（BFS 未命中出网出口）：
 * {@code CallbackDomainServiceImpl#receiveSignResult}、
 * {@code ContractDomainServiceImpl#alipayTripRequestSignInfo} / {@code #removeSignAgreement}。
 * 其中 {@code alipayTripRequestSignInfo} 曾被列入批次 5A 候选、经核实**与那批不同型**：
 * 它零出网（支付宝渠道不调支付平台），且事务真的包住了两条业务写
 * （{@code APP_PAY_SIGN_INFO} + {@code APP_PAY_SIGN_REQUEST}），故**未摘**。
 * {@code receiveSignResult} 同理（多表多写需要原子性），它的通知已于 2.0.75 改成 {@code AFTER_COMMIT} 事件。
 *
 * <p><b>NEVER 靠放宽本类来让构建变绿</b>：清单变红时先分清是「搬迁不小心带上了事务」还是
 * 「有意修边界」，两者的处置完全相反。
 *
 * <p><b>「出网」的判定是保守的近似</b>：从每个 {@code @Transactional} 方法出发在本模块内做调用图
 * BFS，遇到下列出口即记一笔 —— {@code rpc} 模块的任意 Client、{@code PayGatewayClient}、
 * {@code AppNotifyService}（APP 通知投递）、{@code AccountDomainPort}、{@code ChannelSyncDeliverer}。
 * 同名重载合并处理（宁可多报不可少报）。<b>NEVER 把某个出口从 {@link #OUTBOUND_SINKS} 里删掉来消警</b>。
 */
class PaySignTransactionBoundaryArchTest {

    private static final String BASE_PACKAGE = "com.chinasofti.huateng.paysign";

    private static final JavaClasses PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    /** 出网出口：owner 前缀 → 人类可读标签。顺序无关，命中一个即记。 */
    private static final Map<String, String> OUTBOUND_SINKS = Map.of(
            "com.chinasofti.huateng.rpc.", "RPC",
            BASE_PACKAGE + ".client.PayGatewayClient", "支付中心网关",
            BASE_PACKAGE + ".client.AppNotificationClient", "APP通知",
            BASE_PACKAGE + ".service.AppNotifyService", "APP通知",
            BASE_PACKAGE + ".service.impl.AppNotifyServiceImpl", "APP通知",
            BASE_PACKAGE + ".port.AccountDomainPort", "账户域端口",
            BASE_PACKAGE + ".port.AccountDomainRpcAdapter", "账户域端口",
            BASE_PACKAGE + ".service.impl.ChannelSyncDeliverer", "通道清理投递");

    /**
     * 当前带 {@code @Transactional} 的方法全集（**批次 5C 后实测 3 个**，2026-09-15）。
     *
     * <p>本清单在批次 5A 里由 9 条减为 5 条（删掉 {@code ContractDomainServiceImpl} 的
     * {@code requestSignInfo} / {@code requestContractAdvisory} / {@code requestContractResult} /
     * {@code requestTermination}），批次 5B 又减为 4 条（删掉
     * {@code PaymentDomainServiceImpl#requestRefund}），批次 5C 减为 3 条（删掉
     * {@code TerminationInternalServiceImpl#notifyTerminationFailed}，ADR-D90）。
     * 理由与「NEVER 加回」都写在各自方法头，摘要见本类 javadoc。
     * <b>NEVER 把这六条加回来</b> —— 加回等于把「事务包住出网」这个资损级形状恢复。
     *
     * <p>{@code PaymentDomainServiceImpl} 的 {@code requestPay} / {@code receivePayResult} /
     * {@code compensateRefundQuery} / {@code compensateRefundSummary}、
     * {@code CallbackDomainServiceImpl} 的 {@code receiveTerminationResult} **刻意不在此列**
     * （各自方法头有长注释说明理由，其中两个还各对应一次生产事故）。
     * <b>谁把它们加回来，这条断言就会红 —— NEVER 直接改期望值。</b>
     */
    private static final Set<String> EXPECTED_TRANSACTIONAL_METHODS = new TreeSet<>(List.of(
            "CallbackDomainServiceImpl#receiveSignResult",
            "ContractDomainServiceImpl#alipayTripRequestSignInfo",
            "ContractDomainServiceImpl#removeSignAgreement"));

    @Test
    void transactionalMethodsAreExactlyTheFrozenSet() {
        Set<String> actual = new TreeSet<>();
        for (JavaClass javaClass : PAY_SIGN_CLASSES) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (method.isAnnotatedWith(Transactional.class)) {
                    actual.add(javaClass.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertEquals(EXPECTED_TRANSACTIONAL_METHODS, actual,
                "带 @Transactional 的方法集合发生变化。纯搬迁批次 MUST 不改动它；"
                        + "确为有意修改事务边界时，MUST 先在 docs/domain/decisions.md 立 ADR，再同步本期望值");
    }

    /**
     * 现存的「事务包住出网调用」清单 —— <b>批次 5C 起为空集</b>（2026-09-15，ADR-D90）。
     * 本模块已不存在任何被事务包住的出网调用，这是 AGENTS.md §5.2 那条约束的达标状态。
     *
     * <p>清单的收敛过程（每一步都在对应 ADR 里逐条记明，<b>NEVER 反向加回</b>）：
     * <ul>
     *   <li>批次 5A：8 条 → 2 条，删掉的 6 条全属 {@code ContractDomainServiceImpl}
     *       （{@code requestSignInfo -> 支付中心网关}、{@code requestContractAdvisory -> 支付中心网关}、
     *       {@code requestContractResult -> 支付中心网关}、{@code requestContractResult -> RPC}、
     *       {@code requestTermination -> RPC}、{@code requestTermination -> 账户域端口}）。
     *       <b>后两条是同一次出网被重复计数</b>：{@code releaseWalletBinding} 只调了一次
     *       {@code accountDomainPort.agreeRelease}，但 {@code rejected.retMsg()} /
     *       {@code unreachable.cause()} 的 owner 是
     *       {@code com.chinasofti.huateng.rpc.outcome.RpcOutcome$*}，命中 {@link #OUTBOUND_SINKS}
     *       里 {@code "com.chinasofti.huateng.rpc."} 那条前缀匹配，于是同时记成「RPC」和「账户域端口」——
     *       摘掉 {@code requestTermination} 的事务后这两条一起消失，<b>不是漏改</b>。</li>
     *   <li>批次 5B：2 条 → 1 条，删掉 {@code PaymentDomainServiceImpl#requestRefund -> 支付中心网关}。
     *       该方法摘事务的同批按 ADR-D8 补了「PROCESSING 退款回查」补偿
     *       （{@code compensateRefundQuery} + {@code /internal/payment/compensateRefundQuery}）。</li>
     *   <li>批次 5C：1 条 → 0 条，删掉 {@code TerminationInternalServiceImpl#notifyTerminationFailed
     *       -> APP通知}。它的事务只包着一条 CAS UPDATE（保护不了任何东西），却把异步通知的提交
     *       圈在 commit 之前；而 ADR-D8 要求的「落同步状态 + 补偿」本来就齐了
     *       （{@code NOTIFY_STATUS='PENDING'} 由那条 CAS 一并写入，补偿是在跑的
     *       {@code sys_job} 7 解约结果通知补发），因此是纯摘注解。详见该方法头。</li>
     * </ul>
     *
     * <p><b>本集合为空是「已达标」，不是「还没填」。</b>它变非空只有两种可能：有人给某个
     * 出网方法加了 {@code @Transactional}，或有人给某个带事务的方法新增了出网调用 —— 两者都是回归。
     * 三个补偿端点（{@code compensateRefundQuery} / {@code compensateRefundSummary} /
     * {@code compensateTerminationNotify}）本身都出网，<b>它们不带事务，所以不该出现在这里</b>；
     * 若它们出现了，说明有人给补偿方法加了事务，等于把刚修好的形状原地复现。
     * <b>断言红了 MUST 去改代码，NEVER 往这个集合里补条目。</b>
     */
    private static final Set<String> KNOWN_TRANSACTIONAL_OUTBOUND = new TreeSet<>();

    @Test
    void transactionalMethodsReachingOutboundCallsAreExactlyTheKnownOnes() {
        Map<String, JavaClass> byName = new HashMap<>();
        PAY_SIGN_CLASSES.forEach(javaClass -> byName.put(javaClass.getName(), javaClass));

        Set<String> actual = new TreeSet<>();
        for (JavaClass javaClass : PAY_SIGN_CLASSES) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (!method.isAnnotatedWith(Transactional.class)) {
                    continue;
                }
                String label = javaClass.getSimpleName() + "#" + method.getName();
                for (String sink : reachableSinks(byName, javaClass.getName(), method.getName())) {
                    actual.add(label + " -> " + sink);
                }
            }
        }
        assertEquals(KNOWN_TRANSACTIONAL_OUTBOUND, actual,
                "「事务包住出网调用」的清单发生变化。纯搬迁批次 MUST 不改动它（改了说明搬迁顺带动了事务语义）；"
                        + "修边界的批次 MUST 让它变短并在 ADR 里逐条记明");
    }

    /**
     * 从 {@code owner#methodName} 出发做调用图 BFS，返回命中的出网出口标签集合。
     *
     * <p>同名重载合并、只在本模块内展开（模块外的类没导入、无法展开，正好也不需要 —— 它们要么是出口、
     * 要么是 JDK / Spring 的东西）。<b>刻意不做精确重载区分</b>：宁可多报一条让人来看，
     * 也不要因为解析不到而漏掉一条真实的「事务内出网」。
     */
    private Set<String> reachableSinks(Map<String, JavaClass> byName, String ownerName, String methodName) {
        Set<String> sinks = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(ownerName + "#" + methodName);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            String currentOwner = current.substring(0, current.indexOf('#'));
            String currentMethod = current.substring(current.indexOf('#') + 1);
            JavaClass javaClass = byName.get(currentOwner);
            if (javaClass == null) {
                continue;
            }
            for (JavaMethodCall call : callsFrom(javaClass, currentMethod)) {
                String targetOwner = call.getTargetOwner().getName();
                String sink = matchSink(targetOwner);
                if (sink != null) {
                    sinks.add(sink);
                    continue;
                }
                if (targetOwner.startsWith(BASE_PACKAGE)) {
                    queue.add(targetOwner + "#" + call.getName());
                }
            }
        }
        return sinks;
    }

    private List<JavaMethodCall> callsFrom(JavaClass javaClass, String methodName) {
        List<JavaMethodCall> calls = new ArrayList<>();
        for (JavaMethod method : javaClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                calls.addAll(method.getMethodCallsFromSelf());
            }
        }
        return calls;
    }

    private String matchSink(String targetOwner) {
        for (Map.Entry<String, String> entry : OUTBOUND_SINKS.entrySet()) {
            if (targetOwner.equals(entry.getKey()) || targetOwner.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
