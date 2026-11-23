package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.chinasofti.huateng.model.enums.CardTypeCodeEnum.isHceCard;

@Component
class GateCardTypeEnricher {

    private static final Logger log = LoggerFactory.getLogger(GateCardTypeEnricher.class);
    private static final String RET_SUCCESS = "0000";

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    private record SignInfo(String channel, String reqContractNo, String thirdPayId) { }

    private interface SignFieldBinding {
        void apply(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response, String value);
    }

    private record SignField(String name, Function<SignInfo, String> reader, SignFieldBinding binding) { }

    private static final String FIELD_PAYMENT_VENDOR = "支付渠道编码";
    private static final String FIELD_REQUEST_SIGN_SEQ = "签约流水号";
    private static final String FIELD_PAY_USER_ID = "第三方支付账户标识";

    private static final List<SignField> SIGN_FIELDS = List.of(
            new SignField(FIELD_PAYMENT_VENDOR, SignInfo::channel,
                    (request, response, value) -> request.setPaymentVendor(value)),
            new SignField(FIELD_REQUEST_SIGN_SEQ, SignInfo::reqContractNo,
                    (request, response, value) -> {
                        request.setRequestSignSeq(value);
                        response.setRequestSignSeq(value);
                    }),
            new SignField(FIELD_PAY_USER_ID, SignInfo::thirdPayId,
                    (request, response, value) -> response.setPayUserId(value)));


    private void applySignInfo(SignInfo info, NotifyVerifyResultReqDTO request,
                               NotifyVerifyResultRespDTO response, String cardId,
                               String sourceTag, Set<String> silentWhenBlank) {
        for (SignField field : SIGN_FIELDS) {
            String value = field.reader().apply(info);
            if (StringUtils.hasText(value)) {
                field.binding().apply(request, response, value.trim());
            } else if (!silentWhenBlank.contains(field.name())) {
                log.warn("IF1A-01 {}中未找到{}，cardId={}", sourceTag, field.name(), cardId);
            }
        }
    }

    public void updateHceDataFromGateTransaction(NotifyVerifyResultReqDTO request) {
        if (!isHceCard(request.getCardType()) || !StringUtils.hasText(request.getReserve1())) {
            return;
        }
        try {
            UpdateHceDataReqDTO updateRequest = new UpdateHceDataReqDTO();
            updateRequest.setCardId(request.getCardId());
            updateRequest.setHceData(request.getReserve1().trim());
            accountClient.updateHceData(updateRequest);
            log.info("IF1A-01 HCE卡数据回写成功, cardId={}", request.getCardId());
        } catch (Exception e) {
            log.error("IF1A-01 回写HCE卡数据失败, cardId={}", request.getCardId(), e);
        }
    }

    public void applyActualCardType(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        String cardId = request.getCardId();
        try {
            QueryUserInfoResult result = accountClient.queryCardTypeByCardId(cardId);
            log.info("IF1A-01 查询真实卡(用户信息)，值：{}", result);
            if (result == null || !RET_SUCCESS.equals(result.getRetCode())
                    || !StringUtils.hasText(result.getCardType())) {
                log.warn("IF1A-01 account 侧未查到该卡，回落支付宝账户域, cardId={}", cardId);
                applyAlipayUserInfo(request, response, cardId);
                return;
            }

            String actualCardType = result.getCardType().trim();
            request.setCardType(actualCardType);
            if (StringUtils.hasText(result.getCompanionFlag())) {
                request.setCompanionFlag(result.getCompanionFlag().trim());
            }
            if (StringUtils.hasText(result.getMsisdn())) {
                request.setPhone(result.getMsisdn().trim());
            }
            applySignInfo(new SignInfo(result.getChannel(), result.getReqContractNo(), result.getThirdPayId()),
                    request, response, cardId, "用户信息", Set.of());
            log.info("IF1A-01 回传签约信息给 fep-dev-server, cardId={}, paymentVendor={}, requestSignSeq={}",
                    cardId, request.getPaymentVendor(), response.getRequestSignSeq());
            applyFreeRideAmountReset(request, cardId, actualCardType, "");
        } catch (Exception e) {
            log.error("IF1A-01 查询真实卡类型异常，保留闸机上送值（签约信息 / companionFlag / 支付渠道可能缺失）, cardId={}", cardId, e);
        }
    }

    private void applyAlipayUserInfo(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response, String cardId) {
        AlipayUserInfoDTO alipayUser = alipayAccountClient.selectByCardId(cardId);
        if (alipayUser == null) {
            log.warn("IF1A-01 支付宝账户域也未查到该卡，保留闸机上送值, cardId={}", cardId);
            return;
        }

        String actualCardType = CardTypeMapping.toIssueCardType(alipayUser.getCardType());
        if (StringUtils.hasText(actualCardType)) {
            request.setCardType(actualCardType);
        } else {
            actualCardType = request.getCardType();
            log.warn("IF1A-01 支付宝卡种映射为空，保留闸机上送卡种, cardId={}, alipayCardType={}",
                    cardId, alipayUser.getCardType());
        }
        applySignInfo(new SignInfo(alipayUser.getChannel(), alipayUser.getReqContractNo(),
                        alipayUser.getThirdPayId()),
                request, response, cardId, "支付宝用户信息", Set.of(FIELD_REQUEST_SIGN_SEQ));
        if (StringUtils.hasText(alipayUser.getThirdUserId())) {            request.setItpUserId(alipayUser.getThirdUserId().trim());
        }
        if (StringUtils.hasText(alipayUser.getPhone())) {
            request.setPhone(alipayUser.getPhone().trim());
        }
        log.info("IF1A-01 已按支付宝账户域补齐, cardId={}, cardType={}, paymentVendor={}, requestSignSeq={}",
                cardId, request.getCardType(), request.getPaymentVendor(), response.getRequestSignSeq());

        applyFreeRideAmountReset(request, cardId, actualCardType, "(支付宝渠道)");
    }

    /** BOM 补站单 adviceOpt 白名单（与 gate-txn-pay-server 保持一致） */
    private static final Set<String> BOM_SUPPLEMENT_ADVICE_OPTS = Set.of("005", "006", "020");

    /**
     * 检查后付费卡种（0441/0442/0443）是否已签约。
     * 未签约用户不允许扫码过闸，必须先在 APP 完成签约。
     *
     * 排除场景（不拒绝）：
     * - 免费卡种（员工票 0444、日票族 0445-0448）：天然免扣费，无需签约
     * - BOM 补站单（adviceOpt ∈ {005, 006, 020}）：现场已收款，不由 ITP 扣款
     * - 离线码（signChannelCode = "17"）：无网络场景，签约信息可能缺失
     * - accountClient 查询失败（保留闸机上送值）：降级放行，走补款流程
     *
     * @return true 表示未签约且非排除场景，应拒绝过闸
     */
    public boolean rejectUnsignedPostpaid(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        String cardType = request.getCardType();
        // 免费卡种不需要签约
        if (CardTypeCodeEnum.isEmployeeCard(cardType) || CardTypeCodeEnum.isDailyTicket(cardType)) {
            return false;
        }
        // 后付费卡种必须已签约
        if (CardTypeCodeEnum.QR_POSTPAID.getCode().equals(cardType)
                || CardTypeCodeEnum.HCE_POSTPAID.getCode().equals(cardType)
                || CardTypeCodeEnum.NEW_HCE_POSTPAID.getCode().equals(cardType)) {
            // BOM 补站单：现场已收款，无需签约
            if (isBomSupplement(request)) {
                return false;
            }
            // 离线码：无网络，签约信息可能缺失，放行
            if ("17".equals(request.getSignChannelCode())) {
                return false;
            }
            // 以签约流水号（requestSignSeq）为判据，比 paymentVendor 更直接
            if (!StringUtils.hasText(request.getRequestSignSeq())) {
                log.warn("IF1A-01 后付费卡种未签约（requestSignSeq 为空），拒绝过闸, cardId={}, cardType={}",
                        request.getCardId(), cardType);
                // 不设置 retCode —— 闸机侧报文不能改，由 GateTicketHandler 直接 return 终止流程
                return true;
            }
        }
        return false;
    }

    private boolean isBomSupplement(NotifyVerifyResultReqDTO request) {
        String adviceOpt = request.getAdviceOpt();
        return adviceOpt != null && BOM_SUPPLEMENT_ADVICE_OPTS.contains(adviceOpt.trim());
    }

    private void applyFreeRideAmountReset(NotifyVerifyResultReqDTO request, String cardId,
                                          String actualCardType, String channelTag) {
        if (CardTypeCodeEnum.isEmployeeCard(actualCardType) || CardTypeCodeEnum.isDailyTicket(actualCardType)) {
            request.setTrxAmount("0");
            request.setOvertimeAmount("0");
            log.info("IF1A-01 免扣费{}, cardId={}, cardType={}", channelTag, cardId, actualCardType);
        }
    }
}
