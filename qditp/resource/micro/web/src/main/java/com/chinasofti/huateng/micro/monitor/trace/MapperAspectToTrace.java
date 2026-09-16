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
     * 观测 mapper 调用耗时，并<b>原样抛出</b>业务异常。
     *
     * <p><b>NEVER 改回 {@code observation.observe(Supplier)} 那种写法</b>（2026-09-14 修，ADR-D53）：
     * {@code observe} 收的是 {@code Supplier}、不能抛受检异常，于是原实现在 lambda 里
     * {@code throw new RuntimeException(e)} 把真实异常包了一层。后果是**调用方按类型 catch 一律失效** ——
     * 本项目的幂等兜底全靠 {@code catch (DataIntegrityViolationException / DuplicateKeyException)}
     * （AGENTS.md §5.1「唯一索引 + DuplicateKeyException 兜底」），包一层后那些 catch 永远进不去，
     * 异常直接冒到全局处理器变成 500。而且**只在打开 tracing 的模块上出现**
     * （{@code shouldSkipAopTraceLogic()} 为 false 才走这段），因此同一份业务代码在 account-server 上正常、
     * 在 card-pool-server 上就崩 —— 2026-09-14 并发开户实测：卡池 {@code reserve} 明明写了
     * {@code catch (DataIntegrityViolationException)} 去回查兄弟请求的预占，却因本条被跳过，
     * 并发同 {@code businessId} 的第二条请求直接 500、APP 收到「暂无卡数据资源」。
     *
     * <p>现在改成手工 {@code start / openScope / error / stop}：观测语义与 {@code observe} 等价
     * （scope 内 MDC 与 traceId 照旧），但异常路径不再新建包装异常。</p>
     */
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
