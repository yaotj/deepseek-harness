package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.service.AlipayAccountService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import org.springframework.stereotype.Service;


@Service
public class AlipayAccountServiceImpl implements AlipayAccountService {

    private final AlipayRegistrationService registrationService;
    private final AlipayUserQueryService userQueryService;
    private final AlipayPhoneChangeService phoneChangeService;

    public AlipayAccountServiceImpl(AlipayRegistrationService registrationService,
                                   AlipayUserQueryService userQueryService,
                                   AlipayPhoneChangeService phoneChangeService) {
        this.registrationService = registrationService;
        this.userQueryService = userQueryService;
        this.phoneChangeService = phoneChangeService;
    }

    @Override
    public AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request) {
        return registrationService.requestApplication(request);
    }

    @Override
    public AlipayUserInfoDTO selectByThirdUserId(String thirdUserId) {
        return userQueryService.selectByThirdUserId(thirdUserId);
    }

    @Override
    public AlipayUserInfoDTO selectByCardId(String cardId) {
        return userQueryService.selectByCardId(cardId);
    }

    @Override
    public boolean updatePaymentChannel(String thirdUserId, String thirdPayId, String reqContractNo) {
        return userQueryService.updatePaymentChannel(thirdUserId, thirdPayId, reqContractNo);
    }

    @Override
    public boolean updatePhone(String thirdUserId, String newMsisdn) {
        return phoneChangeService.updatePhone(thirdUserId, newMsisdn);
    }
}
