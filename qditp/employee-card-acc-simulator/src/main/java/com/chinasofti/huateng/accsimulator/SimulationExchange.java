package com.chinasofti.huateng.accsimulator;

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
