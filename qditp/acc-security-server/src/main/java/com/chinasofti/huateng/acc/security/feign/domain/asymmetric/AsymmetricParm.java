package com.chinasofti.huateng.acc.security.feign.domain.asymmetric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public class AsymmetricParm {


    @NotBlank(message = "公钥索引号不能为空")
    @Size(min = 2, max = 2, message = "公钥索引号长度为2")
    private String pubkeyIndex;

    public String getPubkeyIndex() {
        return pubkeyIndex;
    }

    public void setPubkeyIndex(String pubkeyIndex) {
        this.pubkeyIndex = pubkeyIndex;
    }
}

