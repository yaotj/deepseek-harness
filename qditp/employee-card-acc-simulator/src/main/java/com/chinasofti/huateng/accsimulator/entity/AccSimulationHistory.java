package com.chinasofti.huateng.accsimulator.entity;

import java.time.LocalDateTime;

/**
 * 模拟 ACC 调用记录，持久化后重启服务仍可在管理端查询。
 *
 * <p>对应表 ACC_SIMULATION_HISTORY，记录每次模拟通知的完整请求与响应信息，
 * 便于审计和失败排查。</p>
 */
public class AccSimulationHistory {

    /**
     * 主键，自增。
     */
    private Long id;

    /**
     * 操作类型，如"状态通知"或"资料变更通知"。
     */
    private String operation;

    /**
     * 目标地址。
     */
    private String targetUrl;

    /**
     * 传输是否成功。
     */
    private Boolean transportSuccess;

    /**
     * HTTP 状态码。
     */
    private Integer httpStatus;

    /**
     * 请求体。
     */
    private String requestBody;

    /**
     * 响应体。
     */
    private String responseBody;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 耗时毫秒数。
     */
    private Long elapsedMs;

    /**
     * 读取主键。
     *
     * @return 主键，自增
     */
    public Long getId() { return id; }

    /**
     * 设置主键。
     *
     * @param id 主键
     */
    public void setId(Long id) { this.id = id; }

    /**
     * 读取操作类型。
     *
     * @return 操作类型，如"状态通知"或"资料变更通知"
     */
    public String getOperation() { return operation; }

    /**
     * 设置操作类型。
     *
     * @param operation 操作类型
     */
    public void setOperation(String operation) { this.operation = operation; }

    /**
     * 读取目标地址。
     *
     * @return 目标地址
     */
    public String getTargetUrl() { return targetUrl; }

    /**
     * 设置目标地址。
     *
     * @param targetUrl 目标地址
     */
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    /**
     * 读取传输是否成功。
     *
     * @return 传输是否成功
     */
    public Boolean getTransportSuccess() { return transportSuccess; }

    /**
     * 设置传输是否成功。
     *
     * @param transportSuccess 传输是否成功
     */
    public void setTransportSuccess(Boolean transportSuccess) { this.transportSuccess = transportSuccess; }

    /**
     * 读取 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public Integer getHttpStatus() { return httpStatus; }

    /**
     * 设置 HTTP 状态码。
     *
     * @param httpStatus HTTP 状态码
     */
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    /**
     * 读取请求体。
     *
     * @return 请求体
     */
    public String getRequestBody() { return requestBody; }

    /**
     * 设置请求体。
     *
     * @param requestBody 请求体
     */
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    /**
     * 读取响应体。
     *
     * @return 响应体
     */
    public String getResponseBody() { return responseBody; }

    /**
     * 设置响应体。
     *
     * @param responseBody 响应体
     */
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    /**
     * 读取错误信息。
     *
     * @return 错误信息
     */
    public String getErrorMessage() { return errorMessage; }

    /**
     * 设置错误信息。
     *
     * @param errorMessage 错误信息
     */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    /**
     * 读取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 读取耗时毫秒数。
     *
     * @return 耗时毫秒数
     */
    public Long getElapsedMs() { return elapsedMs; }

    /**
     * 设置耗时毫秒数。
     *
     * @param elapsedMs 耗时毫秒数
     */
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }
}
