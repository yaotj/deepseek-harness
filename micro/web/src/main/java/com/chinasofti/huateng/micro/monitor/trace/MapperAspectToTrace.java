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

    @Around("mapper()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        if (simpleObservationMonitor.shouldSkipAopTraceLogic()) {
            return pjp.proceed();
        }

        String simpleMethodName = pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName();
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                .lowCardinalityKeyValue(TAG_KEY_METHOD, simpleMethodName);

        simpleObservationMonitor.registerObservation(observation);

        Object result;
        boolean success = true;
        try {
            result = observation.observe(() -> {
                try {
                    return pjp.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Throwable e) {
            success = false;
            log.error("Mapper method execute error: {}", simpleMethodName, e);
            throw e;
        } finally {
            observation.lowCardinalityKeyValue(TAG_KEY_STATUS, success ? "success" : "fail");
        }
        return result;
    }

}
