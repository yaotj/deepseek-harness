package com.chinasofti.huateng.model.app;


public class UserCancelReqDTO {

    private String thirdUserId;


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
