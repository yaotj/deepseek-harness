package com.chinasofti.huateng.acc.security.feign.domain.centercode;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;


public class CenterMacParm implements Serializable {

    private static final long serialVersionUID = 2965554131449040885L;


    @NotBlank(message = "中心票号不允许为空")
    private String centerCode;

    public String getCenterCode() {
        return centerCode;
    }

    public void setCenterCode(String centerCode) {
        this.centerCode = centerCode;
    }
}

