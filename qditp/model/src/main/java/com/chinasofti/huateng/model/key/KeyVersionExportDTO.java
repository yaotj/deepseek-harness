package com.chinasofti.huateng.model.key;

import java.io.Serializable;

/**
 * 密钥版本异步导出行（综管台「密钥管理」页全量导出用）。
 * 只读展示元信息，NEVER 携带密钥材料。
 */
public class KeyVersionExportDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 密钥域编码。 */
    private String keyDomain;
    /** 接入方编码。 */
    private String providerId;
    /** 当前版本。 */
    private String currentVersion;
    /** 状态描述。 */
    private String statusDesc;
    /** 生效日期。 */
    private String effectiveDate;
    /** 更新时间。 */
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
