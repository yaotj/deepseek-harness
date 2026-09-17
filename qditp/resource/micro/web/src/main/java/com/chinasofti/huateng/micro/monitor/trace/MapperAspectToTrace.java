package com.chinasofti.huateng.micro.monitor.trace;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MapperAspectToTrace {
    public static Logger log = LoggerFactory.getLogger(MapperAspectToTrace.class);

    @Autowired
    SimpleObservationMonitor simpleObservationMonitor;
    @Autowired
    ObservationRegistry observationRegistry;

    @Pointcut("within(@org.apache.ibatis.annotations.Mapper *)")
    public void mapper() {
    }

    private static final String OBSERVATION_NAME = "mapper";
    private static final String TAG_KEY_METHOD = "method";
    private static final String TAG_KEY_STATUS = "status";

    /**
     * 观测 mapper 调用耗时，并原样抛出业务异常。
     */
    // NEVER 回退成 observation.observe(Supplier)：异常会被包一层，幂等兜底 catch 全部落空（ADR-D53）
    @Around("mapper()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        if (simpleObservationMonitor.shouldSkipAopTraceLogic()) {
            return pjp.proceed();
        }

        String simpleMethodName = pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName();
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                .lowCardinalityKeyValue(TAG_KEY_METHOD, simpleMethodName);

        simpleObservationMonitor.registerObservation(observation);

        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            Object result = pjp.proceed();
            observation.lowCardinalityKeyValue(TAG_KEY_STATUS, "success");
            return result;
        } catch (Throwable e) {
            observation.lowCardinalityKeyValue(TAG_KEY_STATUS, "fail");
            observation.error(e);
            log.error("Mapper method execute error: {}", simpleMethodName, e);
            throw e;
        } finally {
            observation.stop();
        }
    }

}
