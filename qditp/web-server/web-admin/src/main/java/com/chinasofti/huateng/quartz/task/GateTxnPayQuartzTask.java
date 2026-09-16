package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gate-txn-pay-server 的 Quartz 调用桥接任务：离线码金额补偿 + 公交换乘推送。
 *
 * <p>任务类必须位于白名单包 {@code com.chinasofti.huateng.quartz.task} 下
 * （{@code web-common/.../constant/Constants.java} 的 {@code JOB_WHITELIST_STR}），
 * 放别的包前台直接报「违规」。</p>
 *
 * <p><b>本任务是这两条补偿链路的唯一调度源。</b>gate-txn-pay-server 2.0.73 起
 * {@code OfflineFareRecoveryProcessor} 与 {@code MetroTransferPushTaskProcessor} 都没有
 * {@code @Scheduled} 了，频率完全由 {@code sys_job} 的 cron 决定（两条都是 {@code 0 0/1 * * * ?}）。
 * **NEVER 在那两个类上加回 {@code @Scheduled}** —— 两套调度源互不知情，离线码那条会并发发起扣款。</p>
 *
 * <p><b>两条任务的 `sys_job` MUST 配「禁止并发」（{@code concurrent='1'}，注意 '0' 才是允许）</b>：
 * 原实现是 {@code fixedDelay}（上一轮跑完再等 N 秒），cron 表达不了这个语义，不重叠只靠禁并发保证。
 * 同时 {@code misfire_policy='3'}（错过不补跑）—— 每分钟一轮，补跑毫无意义只会挤压 worker。</p>
 *
 * <p>下游**恒返 {@code retCode=0000}**（本轮扫表异常属可自愈，下一分钟重入），因此这里的
 * {@code retCode} 判定实际只在「无响应 / 不可达」时生效。本轮结论在 {@code retMsg} 里、会进
 * {@code sys_job_log}，**排查「补偿为什么不动」MUST 先看那一列**（能区分「开关未开启」「扫表异常」
 * 「本轮 0 笔」三种情形）。</p>
 */
@Component("gateTxnPayQuartzTask")
public class GateTxnPayQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayQuartzTask.class);

    private final GateTxnPayClient gateTxnPayClient;

    public GateTxnPayQuartzTask(GateTxnPayClient gateTxnPayClient)
    {
        this.gateTxnPayClient = gateTxnPayClient;
    }

    /**
     * 前台调用目标填写 gateTxnPayQuartzTask.recoverOfflineFare() 时执行。
     *
     * <p>触发一轮离线码金额重算失败订单的补偿（重算票价并补扣款）。**这条链路会真的扣款**，
     * 因此下游的抢占依赖条件更新（{@code applyOfflineFareRecalculated} 返 1 才继续），
     * 早跑一轮最坏只是多一次空扫；但**禁并发那一列 NEVER 改成允许**。</p>
     *
     * <p>失败 MUST 抛异常 —— {@code sys_job_log} 的成功/失败判定只看有没有异常抛出，
     * 只打日志会让「补偿没跑成」在调度日志里显示为成功。</p>
     */
    public void recoverOfflineFare()
    {
        QuartzTraceUtils.runWithTrace(this::recoverOfflineFareOnce);
    }

    /**
     * 前台调用目标填写 gateTxnPayQuartzTask.pushMetroTransfer() 时执行。
     *
     * <p>触发一轮公交换乘推送投递。**轮询周期由原来的 10 秒变成 60 秒**（用户 2026-09-15 裁决），
     * 公交卡系统那侧收到推送的时延上限随之变化；要调回更密只能改这条 cron，
     * 但注意 {@code SYS_JOB_LOG} 是「开始即入库」，cron 每打密一档那张表日增行数就翻一档。</p>
     */
    public void pushMetroTransfer()
    {
        QuartzTraceUtils.runWithTrace(this::pushMetroTransferOnce);
    }

    private void recoverOfflineFareOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.recoverOfflineFare(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "离线码金额补偿");
    }

    private void pushMetroTransferOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.pushMetroTransfer(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "公交换乘推送");
    }

    /**
     * 两条任务共用的收口判定，**NEVER 让两条各写一份** —— 判据一样，写两遍必然有一天走偏。
     */
    private void check(CommonResult response, String action)
    {
        if (response == null)
        {
            throw new IllegalStateException("gate-txn-pay-server " + action + "接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("gate-txn-pay-server " + action + "失败: retCode="
                    + response.getRetCode() + ", retMsg=" + response.getRetMsg());
        }
        log.info("gate-txn-pay-server {}调用成功, retMsg={}", action, response.getRetMsg());
    }
}
