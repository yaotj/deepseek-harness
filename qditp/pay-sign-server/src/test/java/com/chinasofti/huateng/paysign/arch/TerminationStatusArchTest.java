package com.chinasofti.huateng.paysign.arch;

import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构门禁：把 {@code APP_TERMINATION_REQUEST.TERMINATION_STATUS} 状态机的边界固化成会失败的构建。
 * <p>
 * 与 {@link SignStatusArchTest} 同形、语义独立。**这些规则在 2026-09-12 补 CAS 后全部为绿**，
 * 加进来是为了让「下一个人退回旧写法」在构建期被拦住，而不是等生产上出现
 * 「APP 先收到解约失败、再收到解约成功」。
 * <b>规则失败时 NEVER 改规则去迁就代码</b> —— 先读 {@code docs/domain/state-machines.md} §二
 * 与 {@code docs/business/pay-sign.md} §解约链路。
 */
class TerminationStatusArchTest {

    /** 只扫本模块主代码，`DoNotIncludeTests` 避免把门禁自身算进依赖图。 */
    private static final JavaClasses PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinasofti.huateng.paysign");

    /**
     * {@code updateStatus} 与 {@code updateFailReason} 的 WHERE 只有 {@code REQUEST_SIGN_SEQ}，
     * 是无条件覆盖。2026-09-12 之前解约回调的成功 / 失败收口就是靠它们 + {@code updateCompleteTime}
     * + {@code updateNotifyStatus} 三条无 CAS 语句拼出来的，后果是并发下把
     * {@code expireScanning} 已打成的 {@code FAILED} 覆盖成 {@code SUCCESS}（反之亦然）。
     *
     * <p>现在两条语句<b>已无任何调用方</b>，本规则把这个事实钉住。改状态 MUST 走 6 条 CAS。
     * XML 里保留它们只是为了不动历史结构，<b>NEVER 因为「方法还在」就重新拿来写状态</b>。
     */
    @Test
    void nonCasStatusWritersHaveNoCallers() {
        ArchRule rule = noClasses()
                .should().callMethod(AppTerminationRequestMapper.class, "updateStatus", String.class, String.class)
                .orShould().callMethod(AppTerminationRequestMapper.class, "updateFailReason",
                        String.class, String.class, String.class)
                .because("这两条语句的 WHERE 只有 REQUEST_SIGN_SEQ，会无条件覆盖并发已改走的状态；"
                        + "改 TERMINATION_STATUS MUST 走 markScanning / rejectPending / markSuccess / "
                        + "rejectScanning / expireScanning / revertScanningToPending / reactivateFailed");
        rule.check(PAY_SIGN_CLASSES);
    }

    /**
     * {@code updateCompleteTime} 同样无 CAS。它现在只应出现在**已被 CAS 合并掉**之外的场景，
     * 而解约收口的三条路径都已把 COMPLETE_TIME 并进单条 CAS，因此本规则要求它没有调用方。
     *
     * <p>拆开写 = 状态与完成时间不再同生共死，中间崩一次就留下「FAILED 但没有完成时间」的行。
     */
    @Test
    void completeTimeIsWrittenInsideCasOnly() {
        ArchRule rule = noClasses()
                .should().callMethod(AppTerminationRequestMapper.class, "updateCompleteTime",
                        String.class, LocalDateTime.class)
                .because("COMPLETE_TIME MUST 与状态在同一条 CAS 里落，拆开写会留下状态与时间不一致的行");
        rule.check(PAY_SIGN_CLASSES);
    }

    /**
     * CAS 是状态机的唯一并发保证，调用它必须紧跟「返 0 行则回查当前状态再分流」的处理
     * （{@code TerminationStatusTransition}），这段判断属于业务编排、**MUST 留在 service 层**。
     * controller 直接调等于把状态机决策散到接入层，回查与幂等短路必然被漏写。
     */
    @Test
    void casMethodsOnlyCallableFromServiceImpl() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.chinasofti.huateng.paysign.service.impl")
                .should().callMethod(AppTerminationRequestMapper.class, "markScanning",
                        String.class, LocalDateTime.class)
                .orShould().callMethod(AppTerminationRequestMapper.class, "markSuccess",
                        String.class, LocalDateTime.class)
                .orShould().callMethod(AppTerminationRequestMapper.class, "rejectScanning",
                        String.class, String.class, LocalDateTime.class)
                .orShould().callMethod(AppTerminationRequestMapper.class, "rejectPending",
                        String.class, String.class, LocalDateTime.class)
                .orShould().callMethod(AppTerminationRequestMapper.class, "expireScanning",
                        String.class, String.class, LocalDateTime.class)
                .orShould().callMethod(AppTerminationRequestMapper.class, "revertScanningToPending", String.class)
                .because("CAS 返 0 行不等于失败，必须紧跟回查与幂等短路判断，这段编排 MUST 留在 service 层");
        rule.check(PAY_SIGN_CLASSES);
    }
}
