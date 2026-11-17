package com.chinasofti.huateng.accsimulator.entity;

import java.time.LocalDateTime;


public class AccSimulationHistory {


    private Long id;


    private String operation;


    private String targetUrl;


    private Boolean transportSuccess;


    private Integer httpStatus;


    private String requestBody;


    private String responseBody;


    private String errorMessage;


    private LocalDateTime createdAt;


    private Long elapsedMs;


    public Long getId() { return id; }


    public void setId(Long id) { this.id = id; }


    public String getOperation() { return operation; }


    public void setOperation(String operation) { this.operation = operation; }


    public String getTargetUrl() { return targetUrl; }


    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }


    public Boolean getTransportSuccess() { return transportSuccess; }


    public void setTransportSuccess(Boolean transportSuccess) { this.transportSuccess = transportSuccess; }


    public Integer getHttpStatus() { return httpStatus; }


    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }


    public String getRequestBody() { return requestBody; }


    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }


    public String getResponseBody() { return responseBody; }


    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }


    public String getErrorMessage() { return errorMessage; }


    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }


    public LocalDateTime getCreatedAt() { return createdAt; }


    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }


    public Long getElapsedMs() { return elapsedMs; }


    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }
}
