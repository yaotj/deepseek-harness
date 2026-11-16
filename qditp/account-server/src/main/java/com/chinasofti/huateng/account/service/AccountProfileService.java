package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.QueryActiveCardsReqDTO;
import com.chinasofti.huateng.model.app.QueryActiveCardsResult;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;

/**
 * 账户资料：按用户 / 卡维度读取开户信息，以及维护 HCE 卡数据。
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
     * 查第三方用户号名下有效票卡（{@code DEL_YN} 有效）的逻辑卡号列表。
     *
     * <p>供支付域 IF8A-35 聚合「已进站未出站」数用；账号域持有 thirdUserId → CARD_ID 映射，
     * 行程域持有 CARD_ID → CODE_STATUS，两个域 NEVER 跨库 JOIN（ADR-D175）。
     */
    QueryActiveCardsResult queryActiveCards(QueryActiveCardsReqDTO request);

    /**
     * 保存 IF1A-01 reserve1 中携带的闸机处理后 HCE 卡数据。
     *
     * @param request HCE 数据更新参数
     * @return 更新结果
     */
    UpdateHceDataResult updateHceData(UpdateHceDataReqDTO request);
}
