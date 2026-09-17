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

/** IF1A-01 卡种与签约信息富化器 —— 用账户侧的真实数据覆盖闸机上送值，并回写 HCE 卡数据。 */
@Component
class GateCardTypeEnricher {

    private static final Logger log = LoggerFactory.getLogger(GateCardTypeEnricher.class);
    private static final String RET_SUCCESS = "0000";

    @Autowired
    private AccountClient accountClient;

    /** 支付宝出行账户域。 */
    @Autowired
    private AlipayAccountClient alipayAccountClient;

    /** 两个账户域共同提供的签约信息。 */
    private record SignInfo(String channel, String reqContractNo, String thirdPayId) { }

    /** 一个字段的落值动作。 */
    private interface SignFieldBinding {
        void apply(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response, String value);
    }

    private record SignField(String name, Function<SignInfo, String> reader, SignFieldBinding binding) { }

    private static final String FIELD_PAYMENT_VENDOR = "支付渠道编码";
    private static final String FIELD_REQUEST_SIGN_SEQ = "签约流水号";
    private static final String FIELD_PAY_USER_ID = "第三方支付账户标识";

    /** 签约信息字段表。 */
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

    /**
     * 按 {@link #SIGN_FIELDS} 落值：有值即 trim 后写入，为空则保留闸机上送值并留痕。
     *
     * @param silentWhenBlank 本来源允许缺失的字段名，缺失时不打 WARN。
     */
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

    /** HCE 卡闸机交易完成后，将 reserve1 的 64 字节卡数据回写到账户服务。 */
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

    /** 查询并应用真实卡类型。 */
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
            applySignInfo(new SignInfo(result.getChannel(), result.getReqContractNo(), result.getThirdPayId()),
                    request, response, cardId, "用户信息", Set.of());
            log.info("IF1A-01 回传签约信息给 fep-dev-server, cardId={}, paymentVendor={}, requestSignSeq={}",
                    cardId, request.getPaymentVendor(), response.getRequestSignSeq());
            applyFreeRideAmountReset(request, cardId, actualCardType, "");
        } catch (Exception e) {
            log.error("IF1A-01 查询真实卡类型异常，保留闸机上送值（签约信息 / companionFlag / 支付渠道可能缺失）, cardId={}", cardId, e);
        }
    }

    /** account 域查不到该卡时，回落支付宝出行账户域（{@code ALIPAY_USER_INFO}）补齐卡种与签约渠道。 */
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
        log.info("IF1A-01 已按支付宝账户域补齐, cardId={}, cardType={}, paymentVendor={}, requestSignSeq={}",
                cardId, request.getCardType(), request.getPaymentVendor(), response.getRequestSignSeq());

        applyFreeRideAmountReset(request, cardId, actualCardType, "(支付宝渠道)");
    }

    /** 员工票（0444）与日票（0445~0448）免费乘车，金额字段 */
    private void applyFreeRideAmountReset(NotifyVerifyResultReqDTO request, String cardId,
                                          String actualCardType, String channelTag) {
        if (CardTypeCodeEnum.isEmployeeCard(actualCardType) || CardTypeCodeEnum.isDailyTicket(actualCardType)) {
            request.setTrxAmount("0");
            request.setOvertimeAmount("0");
            log.info("IF1A-01 免扣费{}, cardId={}, cardType={}", channelTag, cardId, actualCardType);
        }
    }
}
