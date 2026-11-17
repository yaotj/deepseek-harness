package com.chinasofti.huateng.alipay.account.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;


public interface AlipayAccountService {


    AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request);


    AlipayUserInfoDTO selectByThirdUserId(String thirdUserId);


    AlipayUserInfoDTO selectByCardId(String cardId);


    boolean updatePaymentChannel(String thirdUserId, String thirdPayId, String reqContractNo);


    boolean updatePhone(String thirdUserId, String newMsisdn);

}
