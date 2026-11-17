package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;


public class AlipayTripBlackListNotifyReqDTO extends CommonResult {


    private List<BlackListItem> blackList;

    public List<BlackListItem> getBlackList() {
        return blackList;
    }

    public void setBlackList(List<BlackListItem> blackList) {
        this.blackList = blackList;
    }


    public static class BlackListItem {

        private String thirdUserId;


        private String cardId;


        private String cardType;


        private String blackListType;


        private String optionDate;


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
