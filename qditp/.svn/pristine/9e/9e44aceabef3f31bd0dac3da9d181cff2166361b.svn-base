package com.chinasofti.huateng.acc.security.feign.domain.asymmetric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author houkepan
 * @date 2020/5/6 10:57
 */
public class AsymmetricParm {

    /**
     * 公钥索引号
     */
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

