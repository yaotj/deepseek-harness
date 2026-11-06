package com.chinasofti.huateng.micro.monitor.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SimpleObservationMonitor {
    private static final Logger log = LoggerFactory.getLogger(SimpleObservationMonitor.class);

    private static final String TARGET_CLASS_NAME = "io.micrometer.observation.SimpleObservation";

    @Value("#{(${management.tracing.otlp.tracing.batch.max-queue-size:20000}) / 2}")
    private long maxInstanceCount;

    @Value("${management.tracing.enabled:false}")
    private boolean tracingEnabled;

    private final Set<WeakReference<Object>> observationWeakRefs = ConcurrentHashMap.newKeySet();

    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

    private final AtomicLong activeInstanceCount = new AtomicLong(0);

    public SimpleObservationMonitor() {
        Thread cleanThread = new Thread(this::cleanInvalidReferences, "observation-ref-clean-thread");
        cleanThread.setDaemon(true);
        cleanThread.setPriority(Thread.MIN_PRIORITY);
        cleanThread.start();
    }

    public void registerObservation(Object observation) {
        if (observation == null || !TARGET_CLASS_NAME.equals(observation.getClass().getName())) {
            return;
        }

        WeakReference<Object> weakRef = new WeakReference<>(observation, referenceQueue);
        observationWeakRefs.add(weakRef);
        activeInstanceCount.incrementAndGet();
    }

    public long getActiveInstanceCount() {
        cleanInvalidReferencesOnce();
        return activeInstanceCount.get() + 1;
    }

    public boolean shouldSkipAopTraceLogic() {
        if (!tracingEnabled) {
            return true;
        }
        long currentCount = getActiveInstanceCount();
        boolean reachMaxCnt = currentCount >= maxInstanceCount;
        if (reachMaxCnt) {
            if (log.isWarnEnabled() && System.currentTimeMillis() % 10000 < 100) {
                log.warn("SimpleObservation实例数超限(当前：{}，阈值：{})，跳过AOP Trace补充逻辑", currentCount, maxInstanceCount);
            }
        }
        return reachMaxCnt;
    }

    private void cleanInvalidReferences() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Reference<?> invalidRef = referenceQueue.remove(1000);
                if (invalidRef != null) {
                    observationWeakRefs.remove(invalidRef);
                    activeInstanceCount.decrementAndGet();
                    invalidRef.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Observation引用清理线程被中断", e);
                break;
            } catch (Exception e) {
                log.error("清理失效Observation引用失败", e);
            }
        }
    }


    private void cleanInvalidReferencesOnce() {
        try {
            Reference<?> invalidRef;
            while ((invalidRef = referenceQueue.poll()) != null) {
                observationWeakRefs.remove(invalidRef);
                activeInstanceCount.decrementAndGet();
                invalidRef.clear();
            }
        } catch (Exception e) {
            log.error("单次清理失效Observation引用失败", e);
        }
    }

    public long getMaxInstanceCount() {
        return maxInstanceCount;
    }
}