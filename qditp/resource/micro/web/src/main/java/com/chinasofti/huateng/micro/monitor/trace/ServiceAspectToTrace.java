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
public class ServiceAspectToTrace {
    public static Logger log = LoggerFactory.getLogger(ServiceAspectToTrace.class);

    private static final String OBSERVATION_NAME = "service";
    private static final String TAG_KEY_METHOD = "method";
    private static final String TAG_KEY_STATUS = "status";

    @Autowired
    SimpleObservationMonitor simpleObservationMonitor;
    @Autowired
    private ObservationRegistry observationRegistry;

    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void service() {
    }


    @Around("service()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        if (simpleObservationMonitor.shouldSkipAopTraceLogic()) {
            return pjp.proceed();
        }

        String simpleMethodName = pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName();
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry).lowCardinalityKeyValue(TAG_KEY_METHOD, simpleMethodName);

        simpleObservationMonitor.registerObservation(observation);

        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            Object result = pjp.proceed();
            observation.lowCardinalityKeyValue(TAG_KEY_STATUS, "success");
            return result;
        } catch (Throwable e) {
            observation.lowCardinalityKeyValue(TAG_KEY_STATUS, "fail");
            observation.error(e);
            log.error("Service method execute error: {}", simpleMethodName, e);
            throw e;
        } finally {
            observation.stop();
        }
    }

}
