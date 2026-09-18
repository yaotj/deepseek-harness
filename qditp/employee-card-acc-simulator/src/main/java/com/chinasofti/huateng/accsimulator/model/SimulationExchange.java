package com.chinasofti.huateng.accsimulator.model;

/**
 * 模拟 ACC 调用的完整交互记录。
 *
 * <p>记录一次模拟通知的请求与响应信息，用于管理端展示和持久化到历史记录表。</p>
 *
 * @param operation        操作类型，如"状态通知"或"资料变更通知"
 * @param targetUrl        目标地址
 * @param transportSuccess 传输是否成功
 * @param httpStatus       HTTP 状态码
 * @param requestBody      请求体
 * @param responseBody     响应体
 * @param errorMessage     错误信息
 * @param createdAt        创建时间，ISO 格式字符串
 * @param elapsedMs        耗时毫秒数
 */
public record SimulationExchange(
        String operation,
        String targetUrl,
        boolean transportSuccess,
        Integer httpStatus,
        String requestBody,
        String responseBody,
        String errorMessage,
        String createdAt,
        long elapsedMs) {
}
