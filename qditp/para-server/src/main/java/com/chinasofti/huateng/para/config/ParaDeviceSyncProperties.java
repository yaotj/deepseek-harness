package com.chinasofti.huateng.para.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ACC 设备台账同步配置（TBL_DEVICE_INFO 数据来源）。
 * <p>ACC 接口：{@code GET {base-url}/api/v1/Config/device?currentPage=&pageSize=&manageLevel=&withGlobal=true}。</p>
 */
@Component
@ConfigurationProperties(prefix = "para.device-sync")
public class ParaDeviceSyncProperties {

    /** ACC 设备配置接口基地址，K8s env ACC_DEVICE_API_URL 注入。 */
    private String baseUrl = "http://172.20.211.100:48000";

    /**
     * 要同步的 ACC manageLevel 码值列表。业主口径是全量设备，
     * 但 ACC 各 manageLevel 码值含义尚未提供，默认先同步已实测的 04，
     * ACC 给出全量码值清单后改配置即可，NEVER 改代码。
     */
    private List<String> manageLevels = new ArrayList<>(List.of("04"));

    /** 每页拉取条数。 */
    private int pageSize = 500;

    /** 连接超时（毫秒）。 */
    private int connectTimeoutMillis = 10000;

    /** 单次请求读超时（毫秒）。 */
    private int requestTimeoutMillis = 30000;

    /** 是否携带 withGlobal=true（按 ACC 接口约定固定带 true）。 */
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
