package com.chinasofti.huateng.model.app;

/**
 * IF8A-02 单条密钥信息。
 *
 * <p>该对象对应接口文档中 keyList 数组的一条记录。当前实现只组装用户 SM2 非对称密钥对，
 * 即 keyId=01、keyType=1。</p>
 */
public class KeyItemDTO {
    /**
     * 密钥编码，01表示用户非对称密钥对。
     */
    private String keyId;

    /**
     * 密钥类型，当前按样例返回1。
     */
    private String keyType;

    /**
     * 密钥用户ID，由 thirdUserId 转为四字节HEX得到，例如 00000018 转为 00000012。
     */
    private String keyUserId;

    /**
     * APP侧加密后的用户私钥。
     *
     * <p>acc-security-server 导出的私钥先用 acc.3des.key 解密，再用 appserver.3des.key 加密后返回。</p>
     */
    private String keyPrivate;

    /**
     * 用户公钥XY，固定为128位HEX字符串，前64位为X分量，后64位为Y分量。
     */
    private String keyPublic;

    /**
     * 用户公钥有效期，四字节HEX，按 2000-01-01 00:00:00 起算的秒数生成。
     */
    private String keyPublicEffectiveDate;

    /**
     * CA签名用户公钥数据，由 acc-security-server 使用 CA 密钥对用户公钥X分量签名生成。
     */
    private String signData;

    /**
     * CA密钥索引，来自 METRO_CA_KEYSTORE.KEY_IDX。
     */
    private String caIdx;

    /**
     * 密钥包装值，当前接口样例为空，预留字段。
     */
    private String keyWrapValue;

    /**
     * 密钥有效期，当前接口样例为空，预留字段。
     */
    private String keyEffectiveDate;

    /**
     * 密钥校验值，当前接口样例为空，预留字段。
     */
    private String kvc;

    /**
     * 预留字段，当前接口样例为空。
     */
    private String reserve;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getKeyUserId() {
        return keyUserId;
    }

    public void setKeyUserId(String keyUserId) {
        this.keyUserId = keyUserId;
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

    public String getKeyPublicEffectiveDate() {
        return keyPublicEffectiveDate;
    }

    public void setKeyPublicEffectiveDate(String keyPublicEffectiveDate) {
        this.keyPublicEffectiveDate = keyPublicEffectiveDate;
    }

    public String getSignData() {
        return signData;
    }

    public void setSignData(String signData) {
        this.signData = signData;
    }

    public String getCaIdx() {
        return caIdx;
    }

    public void setCaIdx(String caIdx) {
        this.caIdx = caIdx;
    }

    public String getKeyWrapValue() {
        return keyWrapValue;
    }

    public void setKeyWrapValue(String keyWrapValue) {
        this.keyWrapValue = keyWrapValue;
    }

    public String getKeyEffectiveDate() {
        return keyEffectiveDate;
    }

    public void setKeyEffectiveDate(String keyEffectiveDate) {
        this.keyEffectiveDate = keyEffectiveDate;
    }

    public String getKvc() {
        return kvc;
    }

    public void setKvc(String kvc) {
        this.kvc = kvc;
    }

    public String getReserve() {
        return reserve;
    }

    public void setReserve(String reserve) {
        this.reserve = reserve;
    }
}
