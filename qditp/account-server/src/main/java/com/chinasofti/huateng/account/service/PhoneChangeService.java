package com.chinasofti.huateng.account.service;

/**
 * 换号与「显示账号变更」向支付域投递的补偿。
 *
 * <p>由 {@code AccountApplicationServiceImpl} 于 2026-09-11 拆出（原类 1900+ 行；该类已于第六轮整体删除）。
 * 第六轮起本接口即对外契约本身（原先的转发层 AccountApplicationService 已删除），
 * <b>NEVER 让 Controller 直接依赖它</b> —— 上游（{@code ItpUserPageController} /
 * {@code RequestApplicationController} 与 {@code TaskController} 现在直接注入本接口，
 * 换成两套入口就会出现「同一能力两个调用面」。</p>
 *
 * <p>返回类型 {@link SignSyncCompensateResult} 已随第六轮搬入本接口：
 * 它是 {@code TaskController} 已经在用的类型，挪位置等于改对外契约。</p>
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
     * <p>2026-09-11 第六轮拆分从已删除的 {@code AccountApplicationService} 搬到这里 ——
     * 它本来就只被换号补偿链路使用，放在被实现方才是内聚的。</p>
     *
     * @param scanned 本批扫出的待重推条数
     * @param success 重推成功并已置 SUCCESS 的条数
     * @param failed  仍失败、已累加重试次数的条数
     */
    record SignSyncCompensateResult(int scanned, int success, int failed) {
    }
}
