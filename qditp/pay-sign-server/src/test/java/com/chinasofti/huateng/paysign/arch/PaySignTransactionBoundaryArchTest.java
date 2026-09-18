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

/** 护栏：冻结「带 @Transactional 的方法」与「事务内出网」两份清单；后者为空集即达标，断言红了 MUST 改代码、NEVER 改期望值。 */
class PaySignTransactionBoundaryArchTest {

    private static final String BASE_PACKAGE = "com.chinasofti.huateng.paysign";

    private static final JavaClasses PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    /**
     * 出网出口：owner 前缀 → 人类可读标签。顺序无关，命中一个即记。
     *
     * <p>2026-09-17（ADR-D127）由 {@code Map.of} 改为 {@code Map.ofEntries}：
     * {@code AppNotifyService} 按聚合拆成 {@code SignNotifyService} + {@code TerminationNotifyService}
     * 后条目变成 11 个，而 {@code Map.of} 最多只接受 10 对。**NEVER 为了凑回 10 对而删条目**——
     * 少一条即少一个出网出口，护栏会把「事务包住出网」漏判成空集。
     */
    private static final Map<String, String> OUTBOUND_SINKS = Map.ofEntries(
            Map.entry("com.chinasofti.huateng.rpc.", "RPC"),
            Map.entry(BASE_PACKAGE + ".client.PayGatewayClient", "支付中心网关"),
            Map.entry(BASE_PACKAGE + ".client.AppNotificationClient", "APP通知"),
            Map.entry(BASE_PACKAGE + ".port.AppNotifyPort", "APP通知"),
            Map.entry(BASE_PACKAGE + ".service.SignNotifyService", "APP通知"),
            Map.entry(BASE_PACKAGE + ".service.impl.SignNotifyServiceImpl", "APP通知"),
            Map.entry(BASE_PACKAGE + ".service.TerminationNotifyService", "APP通知"),
            Map.entry(BASE_PACKAGE + ".service.impl.TerminationNotifyServiceImpl", "APP通知"),
            Map.entry(BASE_PACKAGE + ".port.AccountDomainPort", "账户域端口"),
            Map.entry(BASE_PACKAGE + ".port.AccountDomainRpcAdapter", "账户域端口"),
            Map.entry(BASE_PACKAGE + ".service.impl.ChannelSyncDeliverer", "通道清理投递"));

    /**
     * 当前带 {@code @Transactional} 的方法全集（**批次 5C 后实测 3 个**，2026-09-15）。
     *
     * <p>2026-09-17（ADR-D120）只改了**宿主类名**：签约回调由 {@code CallbackDomainServiceImpl}
     * 搬到拆分后的 {@code SignResultCallbackHandler}，方法名、注解、事务范围一字未变，
     * 集合大小仍是 3。<b>这不是「改事务边界」</b>；真要增减事务方法 MUST 先立 ADR 再动本集合。
     */
    private static final Set<String> EXPECTED_TRANSACTIONAL_METHODS = new TreeSet<>(List.of(
            "SignResultCallbackHandler#receiveSignResult",
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

    /** 现存的「事务包住出网调用」清单 —— 批次 5C 起为空集（2026-09-15，ADR-D90）。 */
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
