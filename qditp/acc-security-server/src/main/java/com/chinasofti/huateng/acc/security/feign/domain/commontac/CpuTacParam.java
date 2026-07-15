package com.chinasofti.huateng.acc.security.feign.domain.commontac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author houkepan
 * @date 2020/5/6 10:57
 */
public class CpuTacParam {

    /**
     * 16位逻辑卡号
     */
    @NotBlank(message = "逻辑卡号不能为空")
    @Size(min = 16, max = 16, message = "逻辑卡号长度为16位")
    private String cardNo;

    /**
     * 公共TAC
     */
    @NotBlank(message = "TAC数据块不允许为空")
    private String commonTacStr;

    @NotBlank(message = "TAC不能为空")
    @Size(min = 8, max = 8, message = "TAC长度为8位")
    private String tac;

    public String getCommonTacStr() {
        return commonTacStr;
    }

    public void setCommonTacStr(String commonTacStr) {
        this.commonTacStr = commonTacStr;
    }

    public String getTac() {
        return tac;
    }

    public void setTac(String tac) {
        this.tac = tac;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }
}

