package com.chinasofti.huateng.paysign.arch;

import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 护栏：SIGN_STATUS 只准走 4 条 CAS，updateBySeq 仅允许回填非状态字段，javax 只禁 Java EE 那几个包。 */
class SignStatusArchTest {

    /** 只扫本模块主代码，`DoNotIncludeTests` 避免把门禁自身算进依赖图。 */
    private static final JavaClasses PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinasofti.huateng.paysign");

    private static final String CONTRACT_DOMAIN =
            "com.chinasofti.huateng.paysign.service.impl.ContractDomainServiceImpl";

    /** {@code updateBySeq} 的 WHERE 只有 REQUEST_SIGN_SEQ，是「迟到回调覆盖已解约状态」的来源。 */
    @Test
    void updateBySeqOnlyCallableFromContractDomainServiceImpl() {
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName(CONTRACT_DOMAIN)
                .should().callMethod(PaySignInfoMapper.class, "updateBySeq", PaySignInfo.class)
                .because("updateBySeq 的 WHERE 只有 REQUEST_SIGN_SEQ，改状态会被并发回调互相覆盖；"
                        + "改 SIGN_STATUS MUST 走 markSigned / markSignFailed / markUnsigned / reactivateForResign");
        rule.check(PAY_SIGN_CLASSES);
    }

    /** CAS 是状态机的唯一并发保证，调用它必须紧跟「返 0 行则回查当前状态再分流」的处理。 */
    @Test
    void casMethodsOnlyCallableFromServiceImpl() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.chinasofti.huateng.paysign.service.impl")
                .should().callMethod(PaySignInfoMapper.class, "markSigned",
                        String.class, String.class, String.class, LocalDateTime.class)
                .orShould().callMethod(PaySignInfoMapper.class, "markSignFailed", String.class)
                .orShould().callMethod(PaySignInfoMapper.class, "markUnsigned", String.class, LocalDateTime.class)
                .orShould().callMethod(PaySignInfoMapper.class, "reactivateForResign", String.class)
                .because("CAS 返 0 行不等于失败，必须紧跟回查与幂等短路判断，这段编排 MUST 留在 service 层");
        rule.check(PAY_SIGN_CLASSES);
    }

    /** Spring Boot 3.x 已迁到 {@code jakarta.*}。 */
    @Test
    void noJavaEeJavaxPackages() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javax.servlet..",
                        "javax.validation..",
                        "javax.persistence..",
                        "javax.annotation..")
                .because("Spring Boot 3.x 强制 jakarta.*，混用 javax EE 包会在运行期报 NoClassDefFoundError");
        rule.check(PAY_SIGN_CLASSES);
    }
}
