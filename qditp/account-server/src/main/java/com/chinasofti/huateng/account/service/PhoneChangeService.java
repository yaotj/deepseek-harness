package com.chinasofti.huateng.account.service;

/**
 * 换号与「显示账号变更」向支付域投递的补偿。
 */
public interface PhoneChangeService {
    /**
     * 更换手机号，语义见 {@link AccountApplicationService#updatePhone}。
     *
     * @param thirdUserId 第三方用户ID
     * @param newMsisdn   新手机号
     * @return 是否成功
     */
    boolean updatePhone(String thirdUserId, String newMsisdn);

    /**
     * 补偿扫表重推，语义见 {@link AccountApplicationService#compensateSignSync}。
     *
     * @return 本批扫描 / 成功 / 失败条数
     */
    SignSyncCompensateResult compensateSignSync();

    /**
     * 补偿批次结果，仅用于日志与调用方回执，不落库。
     *
     * @param scanned 本批扫出的待重推条数
     * @param success 重推成功并已置 SUCCESS 的条数
     * @param failed  仍失败、已累加重试次数的条数
     */
    record SignSyncCompensateResult(int scanned, int success, int failed) {
    }
}
