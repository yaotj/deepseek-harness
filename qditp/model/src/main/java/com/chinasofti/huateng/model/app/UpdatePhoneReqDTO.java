package com.chinasofti.huateng.model.app;

/**
 * 更换手机号请求DTO（if8a_76）。
 */
public class UpdatePhoneReqDTO {

    /** ITP 侧第三方用户标识，用来定位 {@code USER_ITP_REG_INFO} 与该用户名下的员工码。 */
    private String thirdUserId;

    /**
     * 变更后的手机号。
     */
    private String newPhone;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getNewPhone() {
        return newPhone;
    }

    public void setNewPhone(String newPhone) {
        this.newPhone = newPhone;
    }
}
