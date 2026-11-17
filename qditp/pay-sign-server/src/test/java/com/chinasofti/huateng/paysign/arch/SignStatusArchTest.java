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

class SignStatusArchTest {

    private static final JavaClasses PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinasofti.huateng.paysign");

    private static final String CONTRACT_DOMAIN =
            "com.chinasofti.huateng.paysign.service.impl.ContractDomainServiceImpl";

    @Test
    void updateBySeqOnlyCallableFromContractDomainServiceImpl() {
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName(CONTRACT_DOMAIN)
                .should().callMethod(PaySignInfoMapper.class, "updateBySeq", PaySignInfo.class)
                .because("updateBySeq 的 WHERE 只有 REQUEST_SIGN_SEQ，改状态会被并发回调互相覆盖；"
                        + "改 SIGN_STATUS MUST 走 markSigned / markSignFailed / markUnsigned / reactivateForResign");
        rule.check(PAY_SIGN_CLASSES);
    }

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
