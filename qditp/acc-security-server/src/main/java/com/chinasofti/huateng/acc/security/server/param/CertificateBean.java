package com.chinasofti.huateng.acc.security.server.param;

/**
 * Description:
 *
 * @author houkepan
 * @date 2019/2/21 13:49
 */
public class CertificateBean {

    /**
     * 命令类型
     */
    private byte[] orderType = {(byte) 0xD3};

    /**
     * 命令
     */
    private byte[] order = {(byte) 0x02};

    /**
     * 算法标识
     */
    private byte[] algorithmFlag = {(byte) 0x07};

    /**
     * 秘钥标识
     */
    private byte[] secretFlag = {(byte) 0x0100};

    /**
     * 秘钥索引
     */
    private byte[] secretIndex = {(byte) 0x0001};

    /**
     * 秘钥口令
     */
    private byte[] secretPassword = {(byte) 0x0000000000000000};

    public byte[] getOrderType() {
        return orderType;
    }

    public void setOrderType(byte[] orderType) {
        this.orderType = orderType;
    }

    public byte[] getOrder() {
        return order;
    }

    public void setOrder(byte[] order) {
        this.order = order;
    }

    public byte[] getAlgorithmFlag() {
        return algorithmFlag;
    }

    public void setAlgorithmFlag(byte[] algorithmFlag) {
        this.algorithmFlag = algorithmFlag;
    }

    public byte[] getSecretFlag() {
        return secretFlag;
    }

    public void setSecretFlag(byte[] secretFlag) {
        this.secretFlag = secretFlag;
    }

    public byte[] getSecretIndex() {
        return secretIndex;
    }

    public void setSecretIndex(byte[] secretIndex) {
        this.secretIndex = secretIndex;
    }

    public byte[] getSecretPassword() {
        return secretPassword;
    }

    public void setSecretPassword(byte[] secretPassword) {
        this.secretPassword = secretPassword;
    }
}
