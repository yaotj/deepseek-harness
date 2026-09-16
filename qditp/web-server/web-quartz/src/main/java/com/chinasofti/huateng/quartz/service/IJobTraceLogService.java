package com.chinasofti.huateng.quartz.service;

import com.alibaba.fastjson2.JSONObject;

/**
 * 调度日志的「执行日志」检索服务。
 *
 * <p>数据来源不是数据库，而是 VictoriaLogs：`AbstractQuartzJob.before()` 每次调度生成 traceId 放进 MDC，
 * `after()` 把它追加到 `sys_job_log.job_message`（形如 `，traceId=xxx`），下游服务的日志带同一个 traceId
 * 推到 VictoriaLogs。本服务按该 traceId 反查全链路日志行。</p>
 *
 * @author zmzhang
 */
public interface IJobTraceLogService
{
    /**
     * 按调度日志 ID 检索本次执行的全链路日志。
     *
     * @param jobLogId 调度日志 ID
     * @return traceId / 时间窗 / 日志行集合
     */
    public JSONObject selectTraceLogByJobLogId(Long jobLogId);
}
