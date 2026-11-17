package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;


public class RequestKeyListResult extends CommonResult {

    private String signType;


    private String sign;


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
