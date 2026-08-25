package com.chinasofti.huateng.fep.app.service;

/**
 * 手机号更换服务接口。
 */
public interface PhoneChangeAppService {

    /**
     * 更换手机号。
     *
     * @param thirdUserId 第三方用户ID
     * @param newMsisdn   新手机号
     * @return 是否更新成功
     */
    boolean updatePhone(String thirdUserId, String newMsisdn);
}
