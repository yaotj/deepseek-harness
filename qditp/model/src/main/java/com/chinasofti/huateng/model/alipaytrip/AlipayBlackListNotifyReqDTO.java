package com.chinasofti.huateng.model.alipaytrip;

import lombok.Data;


@Data
public class AlipayBlackListNotifyReqDTO {


    private String cardId;


    private String thirdUserId;


    private String cardType;


    private String blackListType;


    private String optionDate;


    private String expireTime;

    private String reason;
}
