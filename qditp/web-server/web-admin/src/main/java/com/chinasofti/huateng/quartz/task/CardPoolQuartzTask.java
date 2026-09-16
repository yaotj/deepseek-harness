package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * card-pool-server 的 Quartz 调用桥接任务。
 *
 * <p>任务必须位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下，
 * Quartz 仅调用本 Bean；跨服务调用由 CardPoolClient 完成。</p>
 *
 * <p>card-pool-server 本模块**不注册 {@code @Scheduled}**（见 docs/business/card-pool.md
 * 「编码约束」），卡池的回收 / 补货 / 批次推进全靠本任务按 cron 触发，因此这个 Bean 一旦不跑，
 * 卡池就只会被消耗、不会被补充。</p>
 */
@Component("cardPoolQuartzTask")
public class CardPoolQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(CardPoolQuartzTask.class);

    private final CardPoolClient cardPoolClient;

    public CardPoolQuartzTask(CardPoolClient cardPoolClient)
    {
        this.cardPoolClient = cardPoolClient;
    }

    /**
     * 前台调用目标填写 cardPoolQuartzTask.runMaintenance() 时执行。
     *
     * <p>触发一轮卡池维护：回收超时预占、按 {@code card-pool.threshold} 补货、推进
     * {@code CREATED} / {@code DOWNLOADING} / {@code IMPORTING} 批次与可重试的 {@code FAILED} 批次。</p>
     *
     * <p>traceId 由 {@link QuartzTraceUtils#runWithTrace} 统一处理：Quartz 路径复用
     * AbstractQuartzJob 放进 MDC 的值，非 Quartz 路径自行兜底。</p>
     */
    public void runMaintenance()
    {
        QuartzTraceUtils.runWithTrace(this::invokeOnce);
    }

    /**
     * 只调一次下游，失败 MUST 抛异常：Quartz 只以异常判定失败，静默返回会让调度日志记成成功。
     *
     * <p>下游是**受理式**接口：提交给单线程维护池后立刻返回，ACC 申请、FTP 下载、十万行入库都在
     * card-pool-server 后台跑。因此本方法返回成功只代表「已受理」，**NEVER 把它当成「这一轮已导完」**——
     * 导入结果 MUST 看 card-pool-server 日志与 {@code /card-pools/summary} 的水位。</p>
     *
     * <p>{@code accepted=false}（上一轮未结束）**不抛异常**：一次十万行导入远超 5 分钟的调度间隔，
     * 常态会被限流丢弃。这里抛异常会让前台调度日志长期一片红，真故障反而被淹掉。</p>
     *
     * <p>⚠️ card-pool-server 侧要真正把 traceparent 续成 MDC 的 {@code traceId}，需要该模块
     * {@code management.tracing.enabled=true}（已在 card-pool-server/application.properties 打开；
     * {@code resource/micro/web/src/main/resources/web.properties} 里的默认值仍是 false）。</p>
     */
    private void invokeOnce(String traceId)
    {
        CardPoolActionResult result = cardPoolClient.runMaintenance(QuartzTraceUtils.traceHeaders(traceId));
        if (result == null)
        {
            throw new IllegalStateException("card-pool-server 卡池维护接口未返回响应");
        }
        if (!result.isSuccess())
        {
            throw new IllegalStateException("card-pool-server 卡池维护调用失败: outcome=" + result.getOutcome()
                    + ", message=" + result.getMessage());
        }
        log.info("card-pool-server 卡池维护受理成功, {}", result.getMessage());
    }
}
