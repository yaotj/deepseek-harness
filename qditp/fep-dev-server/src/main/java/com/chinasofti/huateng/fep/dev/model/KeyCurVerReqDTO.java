package com.chinasofti.huateng.fep.dev.model;

/** IF1A-02 密钥同步请求-密钥当前版本信息。 */
public class KeyCurVerReqDTO {

    private String issueChannelCode;
    private String keyId;
    private String keyBathNumber;

    public String getIssueChannelCode() {
        return issueChannelCode;
    }

    public void setIssueChannelCode(String issueChannelCode) {
        this.issueChannelCode = issueChannelCode;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeyBathNumber() {
        return keyBathNumber;
    }

    public void setKeyBathNumber(String keyBathNumber) {
        this.keyBathNumber = keyBathNumber;
    }
}
