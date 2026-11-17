package com.chinasofti.huateng.model.key;

import java.io.Serializable;


public class KeyVersionExportDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String keyDomain;
    private String providerId;
    private String currentVersion;
    private String statusDesc;
    private String effectiveDate;
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
