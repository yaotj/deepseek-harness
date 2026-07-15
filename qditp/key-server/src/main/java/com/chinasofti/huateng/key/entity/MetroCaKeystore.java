package com.chinasofti.huateng.key.entity;

import java.util.Date;

/**
 * 地铁CA密钥仓库实体。
 */
public class MetroCaKeystore {
    /**
     * 主键。
     */
    private Long id;

    /**
     * ITP密钥索引。
     */
    private String keyIdx;

    /**
     * ITP私钥。
     */
    private String keyPrivate;

    /**
     * ITP公钥。
     */
    private String keyPublic;

    /**
     * 有效期。
     */
    private String keyEffectiveDate;

    /**
     * 状态：0初始化，1使用，2暂停。
     */
    private Integer keyStatus;

    /**
     * 管理员编码。
     */
    private String managerId;

    /**
     * 预留字段。
     */
    private String reserve;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 修改日期。
     */
    private Date updateDate;

    /**
     * 注册日期。
     */
    private Date regDate;

    /**
     * SM2密钥对。
     */
    private String keyPair;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyIdx() {
        return keyIdx;
    }

    public void setKeyIdx(String keyIdx) {
        this.keyIdx = keyIdx;
    }

    public String getKeyPrivate() {
        return keyPrivate;
    }

    public void setKeyPrivate(String keyPrivate) {
        this.keyPrivate = keyPrivate;
    }

    public String getKeyPublic() {
        return keyPublic;
    }

    public void setKeyPublic(String keyPublic) {
        this.keyPublic = keyPublic;
    }

    public String getKeyEffectiveDate() {
        return keyEffectiveDate;
    }

    public void setKeyEffectiveDate(String keyEffectiveDate) {
        this.keyEffectiveDate = keyEffectiveDate;
    }

    public Integer getKeyStatus() {
        return keyStatus;
    }

    public void setKeyStatus(Integer keyStatus) {
        this.keyStatus = keyStatus;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getReserve() {
        return reserve;
    }

    public void setReserve(String reserve) {
        this.reserve = reserve;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public String getKeyPair() {
        return keyPair;
    }

    public void setKeyPair(String keyPair) {
        this.keyPair = keyPair;
    }
}
