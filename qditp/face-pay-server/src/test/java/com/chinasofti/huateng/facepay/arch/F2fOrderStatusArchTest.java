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

/**
 * 架构门禁：把 {@code F2F_ORDER.ORDER_STATUS} 状态机的边界固化成会失败的构建。
 * 形态照 {@code pay-sign-server/.../arch/SignStatusArchTest.java}。
 *
 * <p>两条规则**在 2026-09-14 收口状态机时全部为绿**，加进来是为了拦住「下一个人退回旧写法」，
 * 而不是等设备侧出现「已退款的单还能再退一次」。
 * <b>规则失败时 NEVER 改规则去迁就代码</b> —— 先读 {@code docs/domain/state-machines.md} §二，
 * 确认到底是新写法有理由，还是又踩了同一个坑。</p>
 */
class F2fOrderStatusArchTest {

    /** 只扫本模块主代码，`DoNotIncludeTests` 避免把门禁自身算进依赖图。 */
    private static final JavaClasses FACE_PAY_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinasofti.huateng.facepay");

    private static final String SERVICE_PACKAGE = "com.chinasofti.huateng.facepay.service";

    private static final Path SERVICE_SOURCE_DIR =
            Path.of("src", "main", "java", "com", "chinasofti", "huateng", "facepay", "service");

    /**
     * {@code F2F_ORDER.ORDER_STATUS} 的 11 个取值。
     * <b>这里只列订单域</b>：票（ISSUED / FAULT）、支付（INIT / SUCCESS / FAILED / PROCESSING）、
     * 退款（另加 MANUAL）是**另外三个取值域**，NEVER 混进来。
     */
    private static final List<String> ORDER_STATUS_LITERALS = List.of(
            "CREATED", "PAYING", "PAID", "PAY_FAILED", "EXPIRED", "FULFILLED",
            "FULFILL_FAILED", "REFUNDING", "REFUNDED", "CANCELED", "TOPUP_SUSPECT");

    /**
     * 允许保留裸字面量的**唯一形态**：常量名带取值域前缀。
     * 活样例是 {@code F2fBomOrderService.TICKET_REFUNDING = "REFUNDING"} ——
     * 它是 {@code F2F_TICKET.TICKET_STATUS}，与订单状态**拼写相同、取值域不同**。
     * 前缀就是人给出的「我知道这不是订单状态」的显式声明；<b>NEVER 靠加白名单文件名放行</b>，
     * 那等于把整个类豁免掉。
     */
    private static final Pattern DOMAIN_TAGGED_CONSTANT =
            Pattern.compile("\\b(TICKET|PAYMENT|REFUND|NOTIFY|REPORT|TOPUP)_[A-Z0-9_]+\\s*=");

    /** 三条 CAS 的调用点。它们的返回行数是唯一输出，丢掉等于把条件更新退化成「更新不到就算了」。 */
    private static final Pattern CAS_CALL =
            Pattern.compile("orderMapper\\.(updateStatus|markPaid|activateForDevice)\\s*\\(");

    /**
     * 认定「返回值被接住了」的四种形态：赋给变量、或交给三个解读入口之一。
     * {@code classify} 直接对应 {@code F2fOrderStatusTransition.classify}。
     */
    private static final List<String> CAS_RESULT_CONSUMERS =
            List.of("=", "warnIfConflict(", "reportPaidConflict(", "classify(");

    /**
     * 唯一的豁免形态：行内显式写 {@code // CAS-DISCARD: <理由>}。
     * <b>NEVER 改成按文件名或方法名豁免</b> —— 那会把整个类放行；
     * 也 NEVER 只写标记不写理由，理由是给下一个人判断「这条豁免还成立吗」的依据。
     */
    private static final String CAS_DISCARD_MARKER = "// CAS-DISCARD:";

    /**
     * 三条 CAS 是 {@code F2F_ORDER.ORDER_STATUS} 的唯一并发保证，调用它们必须紧跟
     * 「返 0 行则回查当前状态再分流」的处理（{@code F2fOrderStatusTransition.classify}），
     * 这段判断属于业务编排、<b>MUST 留在 service 包</b>。controller / 定时任务直接调等于把状态机
     * 决策散到接入层，回查与幂等短路必然被漏写。
     *
     * <p>本模块的服务类**直接放在 {@code service} 包下**（没有 {@code service.impl} 层），
     * 这与 pay-sign-server 不同，NEVER 照抄那边的包名。</p>
     */
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

    /**
     * 订单状态字面量不得再出现在 service 包里，MUST 走 {@code F2fOrderStatus.X.name()}。
     *
     * <p>为什么不用 ArchUnit：<b>它看不见字符串常量</b>（字节码里 {@code String} 常量池不在
     * ArchUnit 的领域模型内），所以这条只能扫源码。形态照
     * {@code account-server/.../UserItpRegInfoMapperSqlTest} 那种「离线解析工程文件」的做法，
     * 工作目录是模块根，不依赖数据库也不启 Spring。</p>
     *
     * <p>收益不在洁癖：11 个取值由 DDL 的 {@code CK_F2F_ORDER_STATUS} 授权，散写的字面量
     * 拼错一个字母编译期完全无感，运行时 CAS 静默返 0 行 —— 表现是「订单永远推不动」，
     * 而不是报错。收进枚举后拼错就编译失败。</p>
     */
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

    /** 去掉注释，避免 Javadoc 里引用状态名被误判。块注释按「首字符是 * 」的行近似处理，够用。 */
    private static String stripComment(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            return "";
        }
        int lineComment = trimmed.indexOf("//");
        return lineComment >= 0 ? trimmed.substring(0, lineComment) : trimmed;
    }

    /**
     * 三条 CAS 的返回行数不得被丢弃。
     *
     * <p><b>这条是本组门禁里价值最高的一条</b>：2026-09-14 首次跑它时，40 个调用点里有 12 个
     * 直接丢掉返回值 —— 其中 {@code F2fTvmOrderService} 查询到支付成功那处 {@code markPaid}
     * 属于「钱已收、状态可能没落上」，是人工逐条 review 时漏掉的。
     * CAS 的返回行数是它唯一的输出，丢掉就等于把条件更新退化成无条件更新。</p>
     *
     * <p>为什么也是扫源码：ArchUnit 看不到「返回值有没有被使用」（字节码里那是一条 POP 指令，
     * 不在它的领域模型内）。判定窗口取<b>匹配行 + 上一行</b>，因为项目里的写法有两种：
     * 同行 {@code warnIfConflict(orderNo, orderMapper.updateStatus(...))}，
     * 以及跨行的 {@code ... = F2fOrderStatusTransition.classify(\n orderMapper.updateStatus(...)}。</p>
     */
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
