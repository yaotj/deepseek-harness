package com.chinasofti.huateng.facepay.arch;

import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 架构门禁：把 {@code F2F_ORDER.ORDER_STATUS} 状态机的边界固化成会失败的构建。 */
class F2fOrderStatusArchTest {

    /** 只扫本模块主代码，`DoNotIncludeTests` 避免把门禁自身算进依赖图。 */
    private static final JavaClasses FACE_PAY_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinasofti.huateng.facepay");

    private static final String SERVICE_PACKAGE = "com.chinasofti.huateng.facepay.service";

    private static final Path SERVICE_SOURCE_DIR =
            Path.of("src", "main", "java", "com", "chinasofti", "huateng", "facepay", "service");

    /** {@code F2F_ORDER.ORDER_STATUS} 的 11 个取值。 */
    private static final List<String> ORDER_STATUS_LITERALS = List.of(
            "CREATED", "PAYING", "PAID", "PAY_FAILED", "EXPIRED", "FULFILLED",
            "FULFILL_FAILED", "REFUNDING", "REFUNDED", "CANCELED", "TOPUP_SUSPECT");

    /** 允许保留裸字面量的**唯一形态**：常量名带取值域前缀。 */
    private static final Pattern DOMAIN_TAGGED_CONSTANT =
            Pattern.compile("\\b(TICKET|PAYMENT|REFUND|NOTIFY|REPORT|TOPUP)_[A-Z0-9_]+\\s*=");

    /** 三条 CAS 的调用点。 */
    private static final Pattern CAS_CALL =
            Pattern.compile("orderMapper\\.(updateStatus|markPaid|activateForDevice)\\s*\\(");

    /** 认定「返回值被接住了」的四种形态：赋给变量、或交给三个解读入口之一。 */
    private static final List<String> CAS_RESULT_CONSUMERS =
            List.of("=", "warnIfConflict(", "reportPaidConflict(", "classify(");

    /** 唯一的豁免形态：行内显式写 {@code // CAS-DISCARD: <理由>}。 */
    private static final String CAS_DISCARD_MARKER = "// CAS-DISCARD:";

    /** 三条 CAS 是 {@code F2F_ORDER.ORDER_STATUS} 的唯一并发保证，调用它们必须紧跟 */
    @Test
    void casMethodsOnlyCallableFromServicePackage() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(SERVICE_PACKAGE)
                .should().callMethod(F2fOrderMapper.class, "updateStatus",
                        String.class, List.class, String.class, String.class)
                .orShould().callMethod(F2fOrderMapper.class, "markPaid", String.class, LocalDateTime.class)
                .orShould().callMethod(F2fOrderMapper.class, "activateForDevice",
                        String.class, String.class, String.class, String.class, LocalDateTime.class)
                .because("CAS 返 0 行不等于失败，必须紧跟回查与幂等短路判断，"
                        + "这段编排 MUST 留在 service 包（见 docs/domain/state-machines.md §二）");
        rule.check(FACE_PAY_CLASSES);
    }

    /** 订单状态字面量不得再出现在 service 包里，MUST 走 {@code F2fOrderStatus.X.name()}。 */
    @Test
    void orderStatusLiteralsAbsentFromServiceSources() throws IOException {
        assertTrue(Files.isDirectory(SERVICE_SOURCE_DIR),
                "找不到 " + SERVICE_SOURCE_DIR.toAbsolutePath()
                        + "，本门禁靠扫源码实现，路径变了 MUST 同步改这里，NEVER 让它静默通过");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(SERVICE_SOURCE_DIR)) {
            for (Path source : sources.filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                collectViolations(source, violations);
            }
        }
        assertTrue(violations.isEmpty(),
                "service 包内仍有订单状态裸字面量，MUST 改成 F2fOrderStatus.X.name()；"
                        + "确属其它取值域（票 / 支付 / 退款）的，MUST 提成带取值域前缀的常量"
                        + "（如 TICKET_REFUNDING）并在注释里写明理由：\n" + String.join("\n", violations));
    }

    private static void collectViolations(Path source, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String code = stripComment(lines.get(i));
            if (code.isEmpty() || DOMAIN_TAGGED_CONSTANT.matcher(code).find()) {
                continue;
            }
            for (String literal : ORDER_STATUS_LITERALS) {
                if (code.contains('"' + literal + '"')) {
                    violations.add(source.getFileName() + ":" + (i + 1) + " -> \"" + literal + "\"");
                }
            }
        }
    }

    /** 去掉注释，避免 Javadoc 里引用状态名被误判。 */
    private static String stripComment(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            return "";
        }
        int lineComment = trimmed.indexOf("//");
        return lineComment >= 0 ? trimmed.substring(0, lineComment) : trimmed;
    }

    /** 三条 CAS 的返回行数不得被丢弃。 */
    @Test
    void casReturnValueNeverDropped() throws IOException {
        assertTrue(Files.isDirectory(SERVICE_SOURCE_DIR),
                "找不到 " + SERVICE_SOURCE_DIR.toAbsolutePath() + "，路径变了 MUST 同步改这里");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(SERVICE_SOURCE_DIR)) {
            for (Path source : sources.filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                collectDroppedCas(source, violations);
            }
        }
        assertTrue(violations.isEmpty(),
                "CAS 返回值被丢弃。MUST 接住并交给 classify / warnIfConflict / reportPaidConflict；"
                        + "确属无所谓的记账型写入，MUST 在同一行写 " + CAS_DISCARD_MARKER + " <理由>：\n"
                        + String.join("\n", violations));
    }

    private static void collectDroppedCas(Path source, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!CAS_CALL.matcher(line).find() || line.contains(CAS_DISCARD_MARKER)) {
                continue;
            }
            String window = (i > 0 ? lines.get(i - 1) : "") + line;
            if (CAS_RESULT_CONSUMERS.stream().noneMatch(window::contains)) {
                violations.add(source.getFileName() + ":" + (i + 1) + " -> " + line.trim());
            }
        }
    }
}
