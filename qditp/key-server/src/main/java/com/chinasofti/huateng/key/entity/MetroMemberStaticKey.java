package com.chinasofti.huateng.key.entity;

import java.util.Date;

/** HCE 会员卡静态密钥缓存。NEVER 在库中保存解密后的 DPK 明文，只存 ACC KEK 加密的 DPK。 */
public class MetroMemberStaticKey {
    private Long id;
    private String metroMemberCardNum;
    private String keyWrapValue1;
    private Integer keyStatus;
    private Date updateDate;
    private Date regDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMetroMemberCardNum() {
        return metroMemberCardNum;
    }

    public void setMetroMemberCardNum(String metroMemberCardNum) {
        this.metroMemberCardNum = metroMemberCardNum;
    }

    public String getKeyWrapValue1() {
        return keyWrapValue1;
    }

    public void setKeyWrapValue1(String keyWrapValue1) {
        this.keyWrapValue1 = keyWrapValue1;
    }

    public Integer getKeyStatus() {
        return keyStatus;
    }

    public void setKeyStatus(Integer keyStatus) {
        this.keyStatus = keyStatus;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    public Date getRegDate() {
        return regDate;
    }

    public void setRegDate(Date regDate) {
        this.regDate = regDate;
    }
}
