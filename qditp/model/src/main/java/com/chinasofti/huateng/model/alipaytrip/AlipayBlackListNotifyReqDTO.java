package com.chinasofti.huateng.model.alipaytrip;

import lombok.Data;

/**
 * 支付宝黑名单变更通知请求参数。
 */
@Data
public class AlipayBlackListNotifyReqDTO {

    /**
     * 卡ID。
     */
    private String cardId;

    /**
     * 三方用户ID。
     */
    private String thirdUserId;

    /**
     * 卡类型编码。
     */
    private String cardType;

    /**
     * 黑名单类型，1：加入黑名单，2：移除黑名单。
     */
    private String blackListType;

    /**
     * 操作时间，格式：yyyyMMddHHmmss。
     */
    private String optionDate;

    /**
     * 失效时间，格式：yyyyMMddHHmmss。
     */
    private String expireTime;

    private String reason;
}
