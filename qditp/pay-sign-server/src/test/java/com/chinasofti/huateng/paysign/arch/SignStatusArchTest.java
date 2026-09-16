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

/**
 * 架构门禁：把 {@code APP_PAY_SIGN_INFO.SIGN_STATUS} 状态机的边界固化成会失败的构建。
 * <p>
 * 这些规则**在 2026-09-11 补 CAS 时全部为绿**，加进来是为了让「下一个人退回旧写法」这件事
 * 在编译期就被拦住，而不是等生产上出现「已解约通道显示为已签约」。
 * <b>规则失败时 NEVER 改规则去迁就代码</b> —— 先读 {@code docs/domain/state-machines.md} §二
 * 与 {@code decisions.md} ADR-D12，确认到底是新写法有理由，还是又踩了同一个坑。
 */
class SignStatusArchTest {

    /** 只扫本模块主代码，`DoNotIncludeTests` 避免把门禁自身算进依赖图。 */
    private static final JavaClasses PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinasofti.huateng.paysign");

    private static final String CONTRACT_DOMAIN =
            "com.chinasofti.huateng.paysign.service.impl.ContractDomainServiceImpl";

    /**
     * {@code updateBySeq} 的 WHERE 只有 REQUEST_SIGN_SEQ，是「迟到回调覆盖已解约状态」的来源。
     * 它现在只保留一个用途：在 IF8A-22 里回填 PAY_ACCOUNT_ID / PAY_AGREEMENT_NO 等**非状态字段**，
     * 因此唯一允许的调用方是 {@code ContractDomainServiceImpl.applyGatewayStatus}
     * （2026-09-15 随签约组由 {@code PaySignWorkflow} 搬迁而来，宿主类改名、规则语义不变）。
     * 新增调用点 **MUST** 改用 4 条 CAS。
     */
    @Test
    void updateBySeqOnlyCallableFromContractDomainServiceImpl() {
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName(CONTRACT_DOMAIN)
                .should().callMethod(PaySignInfoMapper.class, "updateBySeq", PaySignInfo.class)
                .because("updateBySeq 的 WHERE 只有 REQUEST_SIGN_SEQ，改状态会被并发回调互相覆盖；"
                        + "改 SIGN_STATUS MUST 走 markSigned / markSignFailed / markUnsigned / reactivateForResign");
        rule.check(PAY_SIGN_CLASSES);
    }

    /**
     * CAS 是状态机的唯一并发保证，调用它必须紧跟「返 0 行则回查当前状态再分流」的处理，
     * 这段判断属于业务编排、**MUST 留在 service 层**。controller 直接调等于把状态机决策
     * 散到接入层，回查与幂等短路必然被漏写。
     */
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

    /**
     * Spring Boot 3.x 已迁到 {@code jakarta.*}。
     * <b>只禁 Java EE 那几个包，NEVER 写成禁整个 {@code javax..}</b> ——
     * {@code javax.crypto} / {@code javax.net} / {@code javax.sql} 是 JDK 自带包，
     * 签名与加密链路正当使用，一刀切会把门禁做成永久红灯。
     */
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
