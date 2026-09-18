package com.chinasofti.huateng.alipay.paysign.arch;

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
 * 护栏：冻结「带 {@code @Transactional} 的方法」与「事务内出网」两份清单，形态照
 * {@code pay-sign-server} 的 {@code PaySignTransactionBoundaryArchTest}（ADR-D90）。
 *
 * <p><b>与 pay-sign 那份的关系</b>：pay-sign 的 {@code KNOWN_TRANSACTIONAL_OUTBOUND} 是
 * **空集 = 已达标**；本模块这份起点非空（`addContract` 事务内两次 RPC），批次 1（ADR-D129）
 * 收口后**已同为空集**。该集合<b>只允许为空、NEVER 再加行</b>：新增一条就等于把
 * 「事务内出网」这个已修掉的缺陷放回来。</p>
 *
 * <p>两个断言红了都 MUST 先改代码；确为有意调整事务边界时 MUST 先在
 * {@code docs/domain/decisions.md} 立 ADR，再同步这里的期望值。</p>
 */
class AlipayPaySignTransactionBoundaryArchTest {

    private static final String BASE_PACKAGE = "com.chinasofti.huateng.alipay.paysign";

    private static final JavaClasses ALIPAY_PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    /** 出网出口：owner 前缀 → 人类可读标签。顺序无关，命中一个即记。 */
    private static final Map<String, String> OUTBOUND_SINKS = Map.of(
            "com.chinasofti.huateng.rpc.", "RPC",
            BASE_PACKAGE + ".util.PayCenterClient", "支付中心网关",
            BASE_PACKAGE + ".service.impl.notify.PaymentNotifyAdapter", "支付中心通知",
            BASE_PACKAGE + ".service.impl.termination.TerminationNotifier", "销卡通知投递");

    /**
     * 当前带 {@code @Transactional} 的方法全集（批次 1 / ADR-D129 收口后只剩 1 个）。
     *
     * <p>批次 1 摘掉了两处：{@code AlipayContractServiceImpl#addContract}（链路里有两次出网，
     * 事务包不住，改为「本地 INSERT 自动提交 + `CHANNEL_SYNC_*` outbox」）与
     * {@code AlipayContractServiceImpl#terminateContract}（纯转发壳，事务在被委派的那层，
     * 外面再套一层什么都不做、只会掩盖真正的边界）。<b>两者都 NEVER 加回。</b></p>
     */
    private static final Set<String> EXPECTED_TRANSACTIONAL_METHODS = new TreeSet<>(List.of(
            "TerminationRegistrationService#terminateContract"));

    @Test
    void transactionalMethodsAreExactlyTheFrozenSet() {
        Set<String> actual = new TreeSet<>();
        for (JavaClass javaClass : ALIPAY_PAY_SIGN_CLASSES) {
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
     * 现存的「事务包住出网调用」清单 —— <b>批次 1（ADR-D129）起为空集，NEVER 加行</b>。
     *
     * <p>原先唯一一条是 {@code AlipayContractServiceImpl.addContract}：它在
     * {@code @Transactional(rollbackFor = Exception.class)} 内先调
     * {@code alipayAccountClient.selectByThirdUserId} 查开户、再在本地 INSERT 之后调
     * {@code updatePaymentChannel} 回填支付通道。两条都是 RPC，行锁持有时长因此等于
     * 对端响应时长（成因与 2026-08-26 那次 pay-sign 生产事故同源）。现已摘掉事务注解、
     * 通道同步改走 {@code CHANNEL_SYNC_*} outbox，本集合收成空集。</p>
     */
    private static final Set<String> KNOWN_TRANSACTIONAL_OUTBOUND = new TreeSet<>();

    @Test
    void transactionalMethodsReachingOutboundCallsAreExactlyTheKnownOnes() {
        Map<String, JavaClass> byName = new HashMap<>();
        ALIPAY_PAY_SIGN_CLASSES.forEach(javaClass -> byName.put(javaClass.getName(), javaClass));

        Set<String> actual = new TreeSet<>();
        for (JavaClass javaClass : ALIPAY_PAY_SIGN_CLASSES) {
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
                "「事务包住出网调用」的清单发生变化。本集合是迁移期欠账清单，只允许变短；"
                        + "变长说明新引入了「事务包住网络调用」，MUST 改代码而不是改期望值");
    }

    /** 从 {@code owner#methodName} 出发做调用图 BFS，返回命中的出网出口标签集合。 */
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
