package com.chinasofti.huateng.fep.acc.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;

/**
 * ACC FormData 接口的公共处理基类。
 */
abstract class BaseAccController {
    /**
     * 将 FormData 中的业务 JSON 转换为目标 DTO。
     *
     * @param request ACC 公共 FormData 请求
     * @param targetType 业务 DTO 类型
     * @return 反序列化后的业务 DTO
     */
    protected <T> T parseBizData(ItpCommonFormRequest request, Class<T> targetType) {
        String bizData = request == null ? null : request.getBizData();
        return JSON.parseObject(bizData == null || bizData.trim().isEmpty() ? "{}" : bizData, targetType);
    }
}
