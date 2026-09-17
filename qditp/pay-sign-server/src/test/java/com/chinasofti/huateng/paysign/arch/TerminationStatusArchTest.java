package com.chinasofti.huateng.paysign.arch;

import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 护栏：三条无 CAS 的解约状态写语句 MUST 零调用方，改状态只准走 6 条 CAS。 */
class TerminationStatusArchTest {

    /** 只扫本模块主代码，`DoNotIncludeTests` 避免把门禁自身算进依赖图。 */
    private static final JavaClasses PAY_SIGN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinasofti.huateng.paysign");

    /** {@code updateStatus} 与 {@code updateFailReason} 的 WHERE 只有 {@code REQUEST_SIGN_SEQ}。 */
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

    /** {@code updateCompleteTime} 同样无 CAS。它现在只应出现在**已被 CAS 合并掉**之外的场景。 */
    @Test
    void completeTimeIsWrittenInsideCasOnly() {
        ArchRule rule = noClasses()
                .should().callMethod(AppTerminationRequestMapper.class, "updateCompleteTime",
                        String.class, LocalDateTime.class)
                .because("COMPLETE_TIME MUST 与状态在同一条 CAS 里落，拆开写会留下状态与时间不一致的行");
        rule.check(PAY_SIGN_CLASSES);
    }

    /** CAS 是状态机的唯一并发保证，调用它必须紧跟「返 0 行则回查当前状态再分流」的处理。 */
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
