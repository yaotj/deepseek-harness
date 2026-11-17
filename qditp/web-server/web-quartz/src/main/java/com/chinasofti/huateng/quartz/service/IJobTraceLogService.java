package com.chinasofti.huateng.quartz.service;

import com.alibaba.fastjson2.JSONObject;


public interface IJobTraceLogService
{

    public JSONObject selectTraceLogByJobLogId(Long jobLogId);
}
