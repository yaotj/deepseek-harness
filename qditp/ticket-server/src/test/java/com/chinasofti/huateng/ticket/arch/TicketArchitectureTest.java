package com.chinasofti.huateng.ticket.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ticket-server 的架构门禁。**这四条规则是「已经达到的状态」，不是「希望达到的目标」** ——
 * 2026-09-14 加入时全部零违规，任何一条变红都说明是新改动踩了线，MUST 改代码而不是改规则。
 *
 * <p><b>为什么必须有这层门禁。</b>2026-09-14 的耦合治理把 888 行的 {@code GateTicketHandler}
 * 拆成八个包、把 {@code QRCODE_STATUS} 的四包直连收口到 {@code gate/QRCodeStatusStore}
 * 一处。这些成果**在编译期毫无保护**：随手补一个 {@code @Autowired} 就能把包循环、
 * controller 直连 mapper、绕过 CAS 的第二个写入方重新引回来，而编译、现有单测、
 * {@code xmllint} 全都发现不了。**NEVER 因为某条规则挡了路就加白名单或删规则**，
 * 那等于把治理成果原地退回。
 *
 * <p><b>Javadoc 的 {@code @link} 对本门禁完全不可见</b>，**NEVER 反过来记**。
 * 2026-09-14 实测（javac 21 编译一个仅在 javadoc 里 {@code @link} 引用 Target 的类，
 * {@code javap -v -p} 输出中 Target 出现 0 次）：javadoc 不进 class 文件，
 * 而 ArchUnit 读的是字节码。因此 {@code ridestatus} 那两处
 * {@code @link ...gate.AgmRideStatusService} 改成纯文本**对本测试的结果没有任何影响** ——
 * 那是可读性取舍，不是门禁要求。真正让 {@code ridestatus -> gate} 成为实依赖的是
 * {@code TicketRideStatusServiceImpl} 对 {@code gate/QRCodeStatusStore} 的 import（r812）。
 */
class TicketArchitectureTest {

    private static final String TICKET_ROOT = "com.chinasofti.huateng.ticket";

    private static JavaClasses ticketClasses;

    @BeforeAll
    static void importClasses() {
        ticketClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(TICKET_ROOT);
    }

    /**
     * 业务包之间无循环依赖。2026-09-14 按 import 实测，当前是一条 DAG，
     * 主干为 {@code controller -> {query, ridestatus, supplement, entrytxn, gate} -> gate -> entrytxn}，
     * 另有 {@code gate -> notify}、{@code query -> alipay}、{@code gate/query -> merchant}
     * 以及三个包 {@code -> station}。**这不是完整边集**，只是够说明「没有回边」；
     * 判断某条依赖存不存在 MUST 现 grep import，NEVER 引用本段。
     *
     * <p><b>循环不是洁癖问题</b>：代价是「改任何一边都要读完另一边」，
     * 参照 ADR-D63 那条 ticket-server -&gt; fep-dev-server -&gt; ticket-server 的跨服务环。
     */
    @Test
    void 业务包之间不得存在循环依赖() {
        ArchRule rule = slices()
                .matching(TICKET_ROOT + ".(*)..")
                .should().beFreeOfCycles();
        rule.check(ticketClasses);
    }

    /**
     * {@code QRCODE_STATUS} 的 owner 是 {@code gate}：只有 {@code gate/QRCodeStatusStore}
     * 能持有 {@code QRCodeStatusMapper}，其余包一律经它访问。
     *
     * <p>改造前 4 个包直接持有该 mapper、其中 3 个在写，于是 {@code upsertWithCas} 的
     * 并发保证可以被另一个包的无条件 {@code upsert} 静默绕过。**这条规则守的是那个 CAS**。
     */
    @Test
    void QRCodeStatusMapper只允许gate包引用() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(TICKET_ROOT + ".gate", TICKET_ROOT + ".mapper")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(TICKET_ROOT + ".mapper.QRCodeStatusMapper");
        rule.check(ticketClasses);
    }

    /**
     * controller 不得直连 mapper（AGENTS.md §3.3）。
     *
     * <p>踩过的实例：{@code controller/page} 两个运营后台 controller 曾直接注
     * {@code QRCodeTxnDetailMapper} / {@code QRCodeStatusMapper}，把分页边界、状态白名单、
     * 审计日志全写在 controller 里 —— 那些逻辑因此只能靠打 HTTP 验证。
     */
    @Test
    void controller不得直接依赖mapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(TICKET_ROOT + ".controller..")
                .should().dependOnClassesThat()
                .resideInAPackage(TICKET_ROOT + ".mapper..");
        rule.check(ticketClasses);
    }

    /**
     * mapper 层不得反向依赖任何业务包。mapper 只准碰 {@code entity} 与 {@code model}，
     * 否则「数据访问层」就变成了又一个业务编排点。
     */
    @Test
    void mapper层不得依赖业务包() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(TICKET_ROOT + ".mapper..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        TICKET_ROOT + ".gate..",
                        TICKET_ROOT + ".query..",
                        TICKET_ROOT + ".ridestatus..",
                        TICKET_ROOT + ".supplement..",
                        TICKET_ROOT + ".notify..",
                        TICKET_ROOT + ".alipay..",
                        TICKET_ROOT + ".entrytxn..",
                        TICKET_ROOT + ".controller..");
        rule.check(ticketClasses);
    }
}
