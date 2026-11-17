package com.chinasofti.huateng.acc.security.feign.domain.qrcodesign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public class QrcodeSignParm {


    @NotBlank(message = "私钥索引号不能为空")
    @Size(min = 2, max = 2, message = "私钥索引号长度为2")
    private String prikeyIndex;


    @NotBlank(message = "签名数据块不允许为空")
    private String signBlock;

    public String getPrikeyIndex() {
        return prikeyIndex;
    }

    public void setPrikeyIndex(String prikeyIndex) {
        this.prikeyIndex = prikeyIndex;
    }

    public String getSignBlock() {
        return signBlock;
    }

    public void setSignBlock(String signBlock) {
        this.signBlock = signBlock;
    }
}

