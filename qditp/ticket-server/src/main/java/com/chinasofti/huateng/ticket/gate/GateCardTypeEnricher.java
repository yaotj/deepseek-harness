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

/**
 * IF1A-01 卡种与签约信息富化器 —— 用账户侧的真实数据覆盖闸机上送值，并回写 HCE 卡数据。
 *
 * <p>2026-09-14 从 {@code GateTicketHandler}（887 行）拆出。拆分判据是**这里是唯一与账户域打交道的一段**：
 * 两个远端（{@code account-server} 与 {@code alipay-account-server}）都只在本类出现，
 * 编排类因此不再需要认识这两个 Client。三个方法共享同一条不变量 ——
 * <b>查不到 / 抛异常一律只打日志、保留闸机上送值，NEVER 影响过闸主流程</b>。</p>
 *
 * <p><b>切换已完成（2026-09-14 核实）</b>：唯一调用方是 {@code GateTicketHandler}
 * （`:125` 的 {@link #applyActualCardType}、`:189` 的 {@link #updateHceDataFromGateTransaction}），
 * 编排类里那份同形副本与两个 Client 字段都已删除。<b>本段此前写「本笔只是新增，切换尚未发生、
 * 原副本仍是唯一被调用的一份、两个 Client 仍注在原类上」，与代码完全相反，已作废、NEVER 回退</b>
 * —— 那句话会让人去找一个不存在的副本，或误判改本类不生效。</p>
 */
@Component
class GateCardTypeEnricher {

    private static final Logger log = LoggerFactory.getLogger(GateCardTypeEnricher.class);
    private static final String RET_SUCCESS = "0000";

    @Autowired
    private AccountClient accountClient;

    /** 支付宝出行账户域。account 域按 cardId 查不到时的回落来源，见 {@link #applyAlipayUserInfo}。 */
    @Autowired
    private AlipayAccountClient alipayAccountClient;

    /**
     * 两个账户域共同提供的签约信息。把「从哪来」与「怎么落」分开，
     * <b>NEVER 再在两条链路各写一份字段赋值</b>——那正是 {@link #applyFreeRideAmountReset}
     * 收口前的形状，而这三个字段每一个都出过事故（见 {@link #SIGN_FIELDS} 各行注释）。
     */
    private record SignInfo(String channel, String reqContractNo, String thirdPayId) { }

    /** 一个字段的落值动作。有的只写 request，有的 request + response 都要写。 */
    private interface SignFieldBinding {
        void apply(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response, String value);
    }

    private record SignField(String name, Function<SignInfo, String> reader, SignFieldBinding binding) { }

    private static final String FIELD_PAYMENT_VENDOR = "支付渠道编码";
    private static final String FIELD_REQUEST_SIGN_SEQ = "签约流水号";
    private static final String FIELD_PAY_USER_ID = "第三方支付账户标识";

    /**
     * 签约信息字段表。两条链路共用，**新增字段 MUST 加在这里，NEVER 只在一条链路上加**。
     * <ul>
     *   <li>{@link #FIELD_PAYMENT_VENDOR}：来自 {@code CHANNEL}。只写 request，稍后由
     *       {@code GateResponseAssembler} 赋给 {@code response.payChannelCode}，
     *       <b>NEVER 在 response 上再加一个同义字段</b>。为空会让反向推码改用闸机上送的
     *       {@code signChannelCode}（那是票种语义），码体渠道位出错且刷过离线码后不自愈（2026-09-11）。</li>
     *   <li>{@link #FIELD_REQUEST_SIGN_SEQ}：来自 {@code REQ_CONTRACT_NO}。<b>MUST 同时写 request 与
     *       response</b> —— request 是本进程从 HTTP 报文反序列化出来的副本，只改它 fep-dev-server 拿不到，
     *       下游 gate-txn-pay 的免密扣款就会缺签约协议（2026-08-26 修复）。</li>
     *   <li>{@link #FIELD_PAY_USER_ID}：来自 {@code THIRD_PAY_ID}，钱包支付（0B）扣款用。</li>
     * </ul>
     */
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
     * @param silentWhenBlank 本来源**允许缺失**的字段名，缺失时不打 WARN。
     *                        支付宝侧没有 {@code REQ_CONTRACT_NO} 语义的签约流水号，属预期情况，
     *                        MUST 声明进来，否则每笔支付宝过闸都刷一条无意义 WARN。
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

    /**
     * HCE 卡闸机交易完成后，将 reserve1 的 64 字节卡数据回写到账户服务。
     * 回写失败不影响已完成的交易明细入库和票卡状态更新，闸机重试可再次触发回写。
     */
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

    /**
     * 查询并应用真实卡类型。
     * <p>闸机可能仍上送二维码行业卡类型 0441；这里以 account-server 的开户记录为准，
     * 因此鲁通码会在交易明细入库和后续行业推送前恢复为 044A。
     * <p>员工票（0444）和日票（0445-0448）强制清零金额，确保免费乘车在交易记录和行业推送中不产生费用数据。
     * 查询失败或异常时保留闸机上送值，不影响主流程。
     * <p>签约信息（requestSignSeq / payUserId）**MUST 同时写入 response**：
     * request 是本进程从 HTTP 报文反序列化出来的副本，只改它 fep-dev-server 拿不到，
     * 下游 gate-txn-pay-server 的免密扣款就会缺签约协议（2026-08-26 修复）。
     * 支付渠道编码不在此列——它由本方法写进 request 后，稍后由编排方
     * （当前是 {@code GateTicketHandler.handleGateTransaction}）赋给 {@code response.payChannelCode}
     * 带出去，**NEVER** 在 response 上再加一个同义字段。
     */
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
            // 同行票/第三方票标识：account 返回 Y/N/C，透传下游。支付宝账户域没有该字段，因此不进 SIGN_FIELDS
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

    /**
     * account 域查不到该卡时，回落支付宝出行账户域（{@code ALIPAY_USER_INFO}）补齐卡种与签约渠道。
     *
     * <p><b>为什么必须有这条回落</b>：支付宝出行用户只落在 {@code ALIPAY_USER_INFO}，
     * {@code USER_ITP_REG_INFO} 里没有他们。缺了这条，{@code request.paymentVendor} 永远为空，
     * 反向推码的 {@code AppNotifyServiceImpl#resolveSignChannelCode} 就走回退分支改用**闸机上送的
     * signChannelCode**——而闸机侧那个字段承载的是票种语义（12~15 日票、17 离线码），
     * 推给支付宝的码体渠道位随之出错，且刷过一次离线码后不自愈（2026-09-11 定位，
     * 实测 {@code queryCardTypeByCardId?cardId=2607031119542741} 返回全局异常兜底的 UUID retCode）。
     *
     * <p><b>卡种口径转换 MUST 保留</b>：{@code ALIPAY_USER_INFO.CARD_TYPE} 存的是 APP 口径 2 位码
     * （如 {@code 02}），而本方法要写进 {@code request.cardType} 的是 ACC 4 位发卡票种（{@code 0441}），
     * 因此过 {@link CardTypeMapping#toIssueCardType}。**NEVER 直接把 2 位码塞进去**——下游按 4 位码判
     * 员工票 / 日票，2 位码一律判不中，免扣费与日票扣次会整段失效。
     *
     * <p>支付宝侧没有 {@code REQ_CONTRACT_NO} 语义的签约流水号时不写该字段，让下游沿用既有缺失处理；
     * 查不到或异常一律只打日志、保留闸机上送值，**NEVER** 影响过闸主流程。
     */
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
        // 支付宝侧没有 REQ_CONTRACT_NO 语义的签约流水号，缺失属预期，声明进 silentWhenBlank 不刷 WARN
        applySignInfo(new SignInfo(alipayUser.getChannel(), alipayUser.getReqContractNo(),
                        alipayUser.getThirdPayId()),
                request, response, cardId, "支付宝用户信息", Set.of(FIELD_REQUEST_SIGN_SEQ));
        if (StringUtils.hasText(alipayUser.getThirdUserId())) {            request.setItpUserId(alipayUser.getThirdUserId().trim());
        }
        log.info("IF1A-01 已按支付宝账户域补齐, cardId={}, cardType={}, paymentVendor={}, requestSignSeq={}",
                cardId, request.getCardType(), request.getPaymentVendor(), response.getRequestSignSeq());

        applyFreeRideAmountReset(request, cardId, actualCardType, "(支付宝渠道)");
    }

    /**
     * 员工票（0444）与日票（0445~0448）免费乘车，金额字段 MUST 清零。
     *
     * <p>原先两条链路（account 域 / 支付宝账户域）各写一份完全相同的三行赋值，
     * 只有日志后缀不同 —— 收口成一处，避免「改一处漏一处」把某条链路的免扣费漏掉。
     * <b>清零 MUST 落在 request 上</b>：交易明细与行业推送都从 request 取金额。</p>
     */
    private void applyFreeRideAmountReset(NotifyVerifyResultReqDTO request, String cardId,
                                          String actualCardType, String channelTag) {
        if (CardTypeCodeEnum.isEmployeeCard(actualCardType) || CardTypeCodeEnum.isDailyTicket(actualCardType)) {
            request.setTrxAmount("0");
            request.setOvertimeAmount("0");
            log.info("IF1A-01 免扣费{}, cardId={}, cardType={}", channelTag, cardId, actualCardType);
        }
    }
}
