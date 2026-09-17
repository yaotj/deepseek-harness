package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.common.response.ResultVO;

/**
 * ACC 出向响应「算不算成功」的<b>唯一判据</b>。
 */
public final class AccResultCode {
    private AccResultCode() {
    }

    /**
     * ACC 响应码是否表示成功。
     */
    public static boolean isSuccess(String retCode) {
        return AccountErrorCodeEnum.SUCCESS.getCode().equals(retCode)
                || ResultVO.SUCCESS_CODE.equals(retCode);
    }
}
