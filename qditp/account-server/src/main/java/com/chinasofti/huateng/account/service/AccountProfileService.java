package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;

/**
 * 账户资料：按用户 / 卡维度读取开户信息，以及维护 HCE 卡数据。
 *
 * <p>2026-09-11 第六轮拆分：原 {@code AccountApplicationService} 是杂物间，已整体删除。
 * 三个入口的共同点是<b>只读写 {@code USER_ITP_REG_INFO} 自身的资料字段、不碰任何状态机</b>。</p>
 *
 * <p><b>NEVER 往本接口加销户、开户或支付通道方法</b>：它们分别在 {@link AccountCancelService}、
 * {@link AccountRegistrationService}、{@link PayChannelService}。</p>
 */
public interface AccountProfileService {

    /**
     * 查询用户信息。
     *
     * @param request 查询用户信息参数
     * @return 查询用户信息结果
     */
    QueryUserInfoResult queryUserInfo(QueryUserInfoReqDTO request);

    /**
     * 按逻辑卡号反查票种与用户信息。
     *
     * @param cardId 逻辑卡号
     * @return 查询结果；未找到返回 8004
     */
    QueryUserInfoResult queryCardTypeByCardId(String cardId);

    /**
     * 保存 IF1A-01 reserve1 中携带的闸机处理后 HCE 卡数据。
     *
     * @param request HCE 数据更新参数
     * @return 更新结果
     */
    UpdateHceDataResult updateHceData(UpdateHceDataReqDTO request);
}
