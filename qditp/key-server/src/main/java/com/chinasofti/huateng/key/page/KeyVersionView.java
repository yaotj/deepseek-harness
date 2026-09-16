package com.chinasofti.huateng.key.page;

/**
 * 综管台密钥版本查看的展示对象（只读）。
 *
 * <p><b>安全红线</b>：本视图 NEVER 携带任何密钥材料明文（KEY_VALUE / KEY_PRIVATE /
 * KEY_PUBLIC / KEY_WRAP_VALUE*），只放版本号、状态、时间等元信息。填充方 MUST 逐字段
 * 核对来源 SQL 的 select 列表。</p>
 */
public class KeyVersionView {
    /** 密钥域：AGM_KEY（闸机密钥）/ CA_KEYSTORE（CA 密钥仓库）/ HCE_STATIC_KEY（HCE 静态密钥）。 */
    private String keyDomain;
    /** 接入方编码（仅 AGM 密钥按接入方分组，其余域为 null）。 */
    private String providerId;
    /** 当前版本标识：AGM 为批次号，CA 为 KEY_IDX，HCE 为卡数汇总。 */
    private String currentVersion;
    /** 状态码（域内各自口径，页面原样展示）。 */
    private String status;
    /** 状态中文描述。 */
    private String statusDesc;
    /** 生效日期（源列原样，yyyyMMdd）。 */
    private String effectiveDate;
    /** 最近更新时间。 */
    private String updateTime;

    public String getKeyDomain() {
        return keyDomain;
    }

    public void setKeyDomain(String keyDomain) {
        this.keyDomain = keyDomain;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusDesc() {
        return statusDesc;
    }

    public void setStatusDesc(String statusDesc) {
        this.statusDesc = statusDesc;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
