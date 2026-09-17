package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.domain.EmployeeCardEvent;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;

/**
 * 员工码本地落库服务。
 */
public interface EmployeeCardPersistenceService {
    /**
     * 落库 ACC 员工码状态通知（新增 / 更新 + 写一条事件日志）。
     *
     * @param source ACC 上送的员工码信息
     */
    void saveFromStatusNotify(EmployeeCardInfoDTO source);

    /**
     * ACC 激活 / 禁用返回成功后，提交本地状态与事件日志两条写。
     *
     * @param cardNo       员工码卡号，已 trim
     * @param targetStatus 目标卡状态，1 正常 / 2 禁用
     * @param markOpenTms  为 true 且 {@code OPEN_TMS} 为空时回填开通时间
     * @param remark       事件日志备注
     * @return true 表示卡记录被更新；false 表示按卡号未命中任何行（并发注销等），调用方 MUST 视为不一致
     */
    boolean applyActivationResult(String cardNo, int targetStatus, boolean markOpenTms, String remark);

    /**
     * 用 ACC 返回的资料补全本地员工码行（查询时发现姓名为空才走这里），并回写 {@code UPDATE_TMS}。
     *
     * @param target  本地已存在的员工码行，方法内会被就地修改
     * @param source  ACC 返回的员工码资料
     */
    void refreshProfileFromAcc(UserAccEmployeeCard target, EmployeeCardInfoDTO source);

    /**
     * 只写一条员工码事件日志（{@code USER_ACC_EMPLOYEE_CARD_LOG}），不动员工码主表。
     *
     * @param cardNo     员工码卡号
     * @param eventType  事件类型，取值定义在 {@link EmployeeCardEvent}；NEVER 改回 {@code String}
     * @param cardStatus 记录当时的卡状态
     * @param remark     备注
     */
    void recordEvent(String cardNo, EmployeeCardEvent eventType, Integer cardStatus, String remark);

    /**
     * 开户成功后按手机号把该号名下的活跃员工码挂到这个 ITP 用户上（写 {@code THIRD_USER_ID}）。
     *
     * @param thirdUserId 第三方用户标识
     * @param msisdn      手机号
     */
    void attachEmployeeCardsQuietly(String thirdUserId, String msisdn);
}
