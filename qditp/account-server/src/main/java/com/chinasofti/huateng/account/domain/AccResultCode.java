package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.common.response.ResultVO;


public final class AccResultCode {
    private AccResultCode() {
    }


    public static boolean isSuccess(String retCode) {
        return AccountErrorCodeEnum.SUCCESS.getCode().equals(retCode)
                || ResultVO.SUCCESS_CODE.equals(retCode);
    }
}
