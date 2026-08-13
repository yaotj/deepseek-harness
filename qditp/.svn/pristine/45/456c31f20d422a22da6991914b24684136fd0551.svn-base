package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;

/**
 * 支付宝出行-黑名单结果通知请求参数。
 */
public class AlipayTripBlackListNotifyReqDTO extends CommonResult {

    /**
     * 黑名单列表，最多25笔。
     */
    private List<BlackListItem> blackList;

    public List<BlackListItem> getBlackList() {
        return blackList;
    }

    public void setBlackList(List<BlackListItem> blackList) {
        this.blackList = blackList;
    }

    /**
     * 黑名单单条记录。
     */
    public static class BlackListItem {
        /**
         * 第三方用户ID。
         */
        private String thirdUserId;

        /**
         * 地铁会员卡号。
         */
        private String cardId;

        /**
         * 卡类型编码。
         */
        private String cardType;

        /**
         * 黑名单类型。
         * 1：加入黑名单
         * 2：移除黑名单
         */
        private String blackListType;

        /**
         * 操作时间，格式：YYYYMMDDHH24mmss。
         */
        private String optionDate;

        /**
         * 黑名单有效期，格式：YYYYMMDDHH24mmss。
         * 当 blackListType 为 1 时有效。
         */
        private String expireTime;

        public String getThirdUserId() {
            return thirdUserId;
        }

        public void setThirdUserId(String thirdUserId) {
            this.thirdUserId = thirdUserId;
        }

        public String getCardId() {
            return cardId;
        }

        public void setCardId(String cardId) {
            this.cardId = cardId;
        }

        public String getCardType() {
            return cardType;
        }

        public void setCardType(String cardType) {
            this.cardType = cardType;
        }

        public String getBlackListType() {
            return blackListType;
        }

        public void setBlackListType(String blackListType) {
            this.blackListType = blackListType;
        }

        public String getOptionDate() {
            return optionDate;
        }

        public void setOptionDate(String optionDate) {
            this.optionDate = optionDate;
        }

        public String getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(String expireTime) {
            this.expireTime = expireTime;
        }
    }
}
