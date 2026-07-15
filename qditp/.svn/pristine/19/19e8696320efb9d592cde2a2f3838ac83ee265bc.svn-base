package com.chinasofti.huateng.acc.security.feign.domain.passcode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Description:
 *
 * @author houkepan
 * @date 2019/1/21 16:28
 */
public class PassCodeKeyParm {
    /**
     * 通行码 N32
     */
    @NotBlank(message = "通行码不允许为空")
    @Size(min = 32, max = 32, message = "通行码应该为32位")
    private String passcode;

    public PassCodeKeyParm() {
        super();
    }

    public PassCodeKeyParm(String passcode) {
        super();
        this.passcode = passcode;
    }

    public String getPasscode() {
        return passcode;
    }

    public void setPasscode(String passcode) {
        this.passcode = passcode;
    }

}

