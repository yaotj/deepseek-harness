package com.chinasofti.huateng.fep.acc.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;


abstract class BaseAccController {

    protected <T> T parseBizData(ItpCommonFormRequest request, Class<T> targetType) {
        String bizData = request == null ? null : request.getBizData();
        return JSON.parseObject(bizData == null || bizData.trim().isEmpty() ? "{}" : bizData, targetType);
    }
}
