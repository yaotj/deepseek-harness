package com.chinasofti.huateng.ticket.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** ticket-server 的架构门禁。 */
class TicketArchitectureTest {

    private static final String TICKET_ROOT = "com.chinasofti.huateng.ticket";

    private static JavaClasses ticketClasses;

    @BeforeAll
    static void importClasses() {
        ticketClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(TICKET_ROOT);
    }

    /** 业务包之间无循环依赖。 */
    @Test
    void 业务包之间不得存在循环依赖() {
        ArchRule rule = slices()
                .matching(TICKET_ROOT + ".(*)..")
                .should().beFreeOfCycles();
        rule.check(ticketClasses);
    }

    /** {@code QRCODE_STATUS} 的 owner 是 {@code gate}：只有 {@code gate/QRCodeStatusStore} 能持有 {@code QRCodeStatusMapper}，其余包一律经它访问。 */
    @Test
    void QRCodeStatusMapper只允许gate包引用() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(TICKET_ROOT + ".gate", TICKET_ROOT + ".mapper")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(TICKET_ROOT + ".mapper.QRCodeStatusMapper");
        rule.check(ticketClasses);
    }

    /** controller 不得直连 mapper（AGENTS.md §3.3）。 */
    @Test
    void controller不得直接依赖mapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(TICKET_ROOT + ".controller..")
                .should().dependOnClassesThat()
                .resideInAPackage(TICKET_ROOT + ".mapper..");
        rule.check(ticketClasses);
    }

    /** mapper 层不得反向依赖任何业务包。 */
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
