package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.service.AlipayAccountService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import org.springframework.stereotype.Service;

/**
 * 支付宝账户域对外服务的门面：只做委派，**NEVER 在这里写任何业务逻辑**（ADR-D143）。
 *
 * <p>本类原先是 349 行、5 个 public 方法把开户 / 换号 / 只读查询三种职责混在一起。
 * 2026-09-18 按依赖簇拆成三个协作者后它只剩委派：
 * <ul>
 *   <li>{@link AlipayRegistrationService} —— 开卡申请四步（卡池预占 → 注册乘车状态 → 短事务落两表 → confirm）</li>
 *   <li>{@link AlipayUserQueryService} —— 用户档案读取与支付通道回写</li>
 *   <li>{@link AlipayPhoneChangeService} —— 换号</li>
 * </ul>
 *
 * <p>保留这层门面而不让 Controller 直接注三个协作者，是为了**让本次拆分对外零改动**：
 * `FepAlipayTripRequestApplicationController` 与 `AlipayAccountService` 接口一行没动，
 * 因此拆分本身不可能改变任何对外行为。
 */
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
