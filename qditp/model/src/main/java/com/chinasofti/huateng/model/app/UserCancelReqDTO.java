package com.chinasofti.huateng.model.app;

/**
 * IF8A-42 用户销户请求（接口规范 §3.46）。
 */
public class UserCancelReqDTO {

    /** 第三方用户标识，注销口径以此为准。 */
    private String thirdUserId;

    /**
     * 手机号。规格表 91 要求 APP 上送，当前仅登记在日志里、不参与注销口径。
     */
    private String phone;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    /** 手机号。 */
    @Override
    public String toString() {
        return "UserCancelReqDTO{thirdUserId='" + thirdUserId + "', phone='" + maskPhone(phone) + "'}";
    }

    private static String maskPhone(String value) {
        if (value == null || value.length() < 8) {
            return value;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
