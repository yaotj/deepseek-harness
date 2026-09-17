package com.chinasofti.huateng.quartz.service;

import com.alibaba.fastjson2.JSONObject;

/**
 * 调度日志的「执行日志」检索服务。
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
