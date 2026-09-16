package com.chinasofti.huateng.model.app;

/**
 * IF8A-42 用户销户请求（接口规范 §3.46）。
 *
 * <p>APP 发起顺序是 IF8A-35 → IF8A-42 → IF8A-75，因此本接口执行时用户的支付渠道
 * <b>尚未解绑</b>。销户只把 {@code USER_ITP_REG_INFO.DEL_YN} 置 0，
 * <b>NEVER</b> 顺手删 {@code USER_PAY_CHANNEL}——那是 IF8A-75 解绑时才做的事，
 * 提前删会让后续解绑找不到渠道信息。</p>
 */
public class UserCancelReqDTO {

    /** 第三方用户标识，注销口径以此为准。 */
    private String thirdUserId;

    /**
     * 手机号。规格表 91 要求 APP 上送，<b>当前仅登记在日志里、不参与注销口径</b>：
     * {@code USER_ITP_REG_INFO} 没有手机号列，无法据此做归属校验。
     * 若后续要用它做二次校验，MUST 先确认手机号的权威来源表。
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

    /** 手机号属个人信息，toString 只保留前 3 后 4，避免请求日志整串回显。 */
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
