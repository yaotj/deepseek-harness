package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.app.model.CommonFormRequest;

/**
 * APP FormData 接口的公共处理基类。
 *
 * <p>APP 将公共字段以表单字段提交，业务参数统一放在 {@code bizData} JSON 字符串中。
 * 子类仅负责接口语义和服务编排，本类负责将其反序列化为对应的业务 DTO。</p>
 */
abstract class BaseAppController {

    /**
     * 将 FormData 中的业务 JSON 转换为目标 DTO。
     *
     * <p>空 {@code bizData} 按空 JSON 对象处理，以保持历史接口的反序列化行为。</p>
     *
     * @param request APP 公共 FormData 请求
     * @param targetType 业务 DTO 类型
     * @return 反序列化后的业务 DTO
     */
    protected <T> T parseBizData(CommonFormRequest request, Class<T> targetType) {
        String bizData = request == null ? null : request.getBizData();
        return JSON.parseObject(bizData == null || bizData.trim().isEmpty() ? "{}" : bizData, targetType);
    }
}
