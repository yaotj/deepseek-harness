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


class AlipayPaySignTransactionBoundaryArchTest {

    private static final String BASE_PACKAGE = "com.chinasofti.huateng.alipay.paysign";

    private static final JavaClasses ALIPAY_PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    private static final Map<String, String> OUTBOUND_SINKS = Map.of(
            "com.chinasofti.huateng.rpc.", "RPC",
            BASE_PACKAGE + ".util.PayCenterClient", "支付中心网关",
            BASE_PACKAGE + ".service.impl.notify.PaymentNotifyAdapter", "支付中心通知",
            BASE_PACKAGE + ".service.impl.termination.TerminationNotifier", "销卡通知投递");


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
