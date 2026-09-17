package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;

/**
 * IF8A-02 请求同步密钥应答参数。
 */
public class RequestKeyListResult extends CommonResult {
    /**
     * 签名类型。当前按接口样例返回 01，表示 sha1withrsa。
     */
    private String signType;

    /**
     * 签名值。当前项目暂未实现响应报文私钥签名，返回空字符串。
     */
    private String sign;

    /**
     * 密钥列表。二维码卡返回一条 keyId=01 的用户非对称密钥；HCE 卡返回一条 keyId=00 的 DPK。
     */
    private List<KeyItemDTO> keyList;

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public List<KeyItemDTO> getKeyList() {
        return keyList;
    }

    public void setKeyList(List<KeyItemDTO> keyList) {
        this.keyList = keyList;
    }
}
