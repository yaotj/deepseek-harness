package com.chinasofti.huateng.para.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
@ConfigurationProperties(prefix = "para.device-sync")
public class ParaDeviceSyncProperties {

    private String baseUrl = "http://172.20.211.100:48000";


    private List<String> manageLevels = new ArrayList<>(List.of("04"));


    private int pageSize = 10000;

    private int connectTimeoutMillis = 10000;

    private int requestTimeoutMillis = 30000;

    private boolean withGlobal = true;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public List<String> getManageLevels() { return manageLevels; }
    public void setManageLevels(List<String> manageLevels) { this.manageLevels = manageLevels; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
    public int getRequestTimeoutMillis() { return requestTimeoutMillis; }
    public void setRequestTimeoutMillis(int requestTimeoutMillis) { this.requestTimeoutMillis = requestTimeoutMillis; }
    public boolean isWithGlobal() { return withGlobal; }
    public void setWithGlobal(boolean withGlobal) { this.withGlobal = withGlobal; }
}
