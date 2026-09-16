package com.chinasofti.huateng.ticket.notify;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.app.AppIndustryDataNotifyReqDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.utils.SignChannelUtils;
import com.chinasofti.huateng.rpc.industry.IndustryDataClient;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 行业数据反向推码链路 —— 调 {@code industry-data-server} 生码，再推给 APP 网关。
 *
 * <p>2026-09-14 从 {@code AppNotifyServiceImpl}（425 行）拆出（ADR-D61）。那个类里住着**两条完全不同的
 * 外发链路**：本链路（生码 + 推 APP 网关，判 retCode）与支付宝行程推送（查线路 + 推支付宝，只判 HTTP），
 * 两者只共享 form-data 骨架与线程池。混住的代价是 8 个 {@code @Value} 分不清归属、
 * 两个取值相同语义无关的 {@code 0000} 常量并列在一起。
 *
 * <p><b>本类是一条完整链路的收口</b>：生码入参口径、码体签约渠道判定、推送目标 URL 选择、
 * 应答 retCode 判定全在这里，**NEVER 把其中任何一步挪回编排层** —— 编排层只负责异步提交。
 */
@Component
class IndustryDataNotifier extends FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(IndustryDataNotifier.class);
    /** APP 网关受理成功的 retCode。非该值即视为未受理，见 {@link #doNotifyIndustryData}。 */
    private static final String APP_GATEWAY_SUCCESS_RET_CODE = "0000";
    /**
     * industry-data-server 生码成功的 retCode。
     *
     * <p>与 {@link #APP_GATEWAY_SUCCESS_RET_CODE} **取值相同但语义无关**（一个是内部 RPC、一个是 APP 网关），
     * 因此故意分成两个常量，**NEVER 合并成一个** —— 任一侧改码值时合并的那个常量会同时改错另一侧。
     */
    private static final String INDUSTRY_BUILD_SUCCESS_RET_CODE = "0000";

    private final IndustryDataClient industryDataClient;

    @Value("${app.notify.industry-data-url:http://127.0.0.1:8080/ci/app/receiveCardDataFromItp}")
    private String industryDataNotifyUrl;

    @Value("${app.notify.industry-data-enabled:true}")
    private boolean industryDataNotifyEnabled;

    @Value("${app.notify.alipay-industry-data-url:https://dtcustomer.bestonepay.com/ngopenplatform/notify/receiveCardDataFromItp}")
    private String alipayIndustryDataNotifyUrl;

    IndustryDataNotifier(IndustryDataClient industryDataClient,
                         @Qualifier("appNotifyHttpClient") OkHttpClient httpClient,
                         NotifyFormRequestFactory formRequestFactory) {
        super(httpClient, formRequestFactory);
        this.industryDataClient = industryDataClient;
    }

    /**
     * 生码 + 推送整条链路。**在调用方的异步任务里执行，本方法内 NEVER 再起线程。**
     *
     * <p>三种「不推」的情形都只打日志、不抛异常（调用方是异步任务，抛出去也没人接）：
     * 开关关闭、生码失败、推送未受理。其中**未受理是补偿的挂点** —— 当前无补偿链路，
     * 先把它显式记成 WARN 便于按 cardId 追查。
     */
    void notifyVerifyResult(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, String gateCardType) {
        if (!industryDataNotifyEnabled) {
            log.info("APP行业数据推送已关闭, cardId={}, ticketTransSeq={}",
                    request.getCardId(), request.getTicketTransSeq());
            return;
        }
        log.info("异步推送 APP 行业数据开始, request={}, qrCodeStatus={}, gateCardType={}",
                request, qrCodeStatus, gateCardType);

        IndustryCardDataBuildReqDTO cardDataRequest = buildCardDataRequest(request, qrCodeStatus, gateCardType);
        log.info("调用 industry-data-server 生成卡数据, request={}", JSON.toJSONString(cardDataRequest));
        IndustryCardDataBuildRespDTO cardDataResp = industryDataClient.buildCardData(cardDataRequest);
        log.info("调用 industry-data-server 生成卡数据完成, response={}", JSON.toJSONString(cardDataResp));
        if (cardDataResp == null || !INDUSTRY_BUILD_SUCCESS_RET_CODE.equals(cardDataResp.getRetCode())) {
            log.warn("异步推送 APP 行业数据失败, 生成卡数据失败, cardId={}, retCode={}, retMsg={}",
                    request.getCardId(),
                    cardDataResp == null ? null : cardDataResp.getRetCode(),
                    cardDataResp == null ? null : cardDataResp.getRetMsg());
            return;
        }

        AppIndustryDataNotifyReqDTO notifyRequest = buildNotifyRequest(request, cardDataResp.getCardData());
        String notifyUrl = isAlipayTripChannel(request) ? alipayIndustryDataNotifyUrl : industryDataNotifyUrl;
        // 返回 boolean 的方法 MUST 显式判返回值（§5.2）。当前无补偿链路，先只把「未受理」显式记下来，
        // 便于按 cardId 追查；补偿（落库 + @Scheduled 扫表重试）挂在这个分支上。
        if (!doNotifyIndustryData(notifyRequest, notifyUrl, request.getDeviceId())) {
            log.warn("APP 行业数据推送未受理，本次不补偿, cardId={}, thirdUserId={}, ticketStatus={}, txnSeq={}",
                    request.getCardId(), request.getItpUserId(),
                    qrCodeStatus == null ? null : qrCodeStatus.getCodeStatus(),
                    qrCodeStatus == null ? null : qrCodeStatus.getTxnSeq());
        }
    }

    /**
     * 组装生码入参，口径 MUST 与 IF8A-03（fep-app-server 的 IndustryDataServiceImpl#buildCardDataRequest）一致：
     * <ul>
     *   <li>票种位取上游上送的原始 cardType（{@code gateCardType}），**NEVER** 用 account 开户卡种覆盖后的
     *       {@code request.getCardType()}——那会让同一张码在「APP 主动取码」与「过闸后反向推码」两条路径上
     *       解析出不同票种；</li>
     *   <li>签约渠道位取 account 的 {@code USER_ITP_REG_INFO.CHANNEL}，即 applyActualCardType 写进
     *       {@code request.paymentVendor} 的值，**NEVER** 直接用闸机上送的 {@code signChannelCode}——
     *       闸机侧该字段承载的是票种语义（{@code GateDailyTicketCoordinator} 用它判日票 12~15、
     *       {@code GateResponseAssembler} 用它判离线码 17），与账户签约渠道不是一回事。</li>
     * </ul>
     * 例外：{@code issueChannelCode} 的爱山东判定仍用 account 卡种（见 {@link #resolveIssueChannelCode}）。
     */
    private IndustryCardDataBuildReqDTO buildCardDataRequest(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus,
                                                            String gateCardType) {
        IndustryCardDataBuildReqDTO cardDataRequest = new IndustryCardDataBuildReqDTO();
        cardDataRequest.setThirdUserId(request.getItpUserId());
        cardDataRequest.setCardId(request.getCardId());
        cardDataRequest.setCardType(StringUtils.hasText(gateCardType)
                ? gateCardType.trim() : request.getCardType());
        cardDataRequest.setTicketStatus(qrCodeStatus.getCodeStatus());
        cardDataRequest.setLastTxnStation(qrCodeStatus.getLastTxnStation());
        cardDataRequest.setLastTxnTime(qrCodeStatus.getLastTxnTime());
        cardDataRequest.setGateInStation(qrCodeStatus.getGateInStation());
        cardDataRequest.setGateInTime(qrCodeStatus.getGateInTime());
        cardDataRequest.setTxnSeq(qrCodeStatus.getTxnSeq());
        cardDataRequest.setIssueChannelCode(resolveIssueChannelCode(request));
        cardDataRequest.setSignChannelCode(resolveSignChannelCode(request));
        return cardDataRequest;
    }

    /**
     * 解析码体签约渠道位。
     *
     * <p><b>反向推码一律推在线码</b>（用户 2026-09-11 明确要求「推送要推送在线码」）：本方法只取 account 的
     * {@code USER_ITP_REG_INFO.CHANNEL}，即 {@code GateCardTypeEnricher#applyActualCardType} 写入的
     * {@code request.paymentVendor}，与 IF8A-03 口径一致。account CHANNEL 缺失时才回退闸机上送值并告警。</p>
     *
     * <p><b>NEVER 再加回「闸机上送 17 就沿用 17」的分支。</b>此前那条分支的后果是：乘客用过一次离线码
     * （APP 调 IF8D-03，该接口把 {@code signChannelCode} 硬编码为 17）之后，闸机每次都把 17 回传上来，
     * 反向推码就把 17 一路保留，于是**即使网络恢复，推给 APP 的码仍是离线码、永远不自愈**，只能靠 APP
     * 重新调 IF8A-03 才回到在线渠道（2026-09-11 实测：卡 0426090942000095 从 14:09 起 8 笔全是 17，
     * 而 account CHANNEL 一直是 03）。</p>
     *
     * <p>离线码标识并未因此丢失，另有两处承载，改动本方法时 **MUST** 保持它们不变：
     * IF8D-03（{@code fep-app-server} 的 {@code IndustryDataServiceImpl}）生成离线码时仍硬编码 17；
     * IF1A-01 给闸机的应答里 {@code offlineFlag} 仍由 {@code GateResponseAssembler#resolveOfflineFlag}
     * 按闸机上送值判 17，本笔交易「刷的是离线码」这个事实照旧上报。</p>
     */
    private String resolveSignChannelCode(NotifyVerifyResultReqDTO request) {
        String accountChannel = SignChannelUtils.resolve(request.getPaymentVendor());
        if (StringUtils.hasText(accountChannel)) {
            String gateChannel = SignChannelUtils.resolve(request.getSignChannelCode());
            if (!accountChannel.equals(gateChannel)) {
                log.info("码体签约渠道改用 account CHANNEL 下发在线码, cardId={}, gateSignChannelCode={}, accountChannel={}",
                        request.getCardId(), request.getSignChannelCode(), accountChannel);
            }
            return accountChannel;
        }
        log.warn("码体签约渠道缺少 account CHANNEL，回退闸机上送值, cardId={}, gateSignChannelCode={}",
                request.getCardId(), request.getSignChannelCode());
        return SignChannelUtils.resolve(request.getSignChannelCode());
    }

    /**
     * 爱山东（044A）的发行渠道强制为 01。这里 **MUST** 用 account 开户卡种（applyActualCardType 覆盖后的
     * {@code request.cardType}）判定：闸机对鲁通码可能仍上送 0441，用上送值判会漏掉这个强制。
     */
    private String resolveIssueChannelCode(NotifyVerifyResultReqDTO request) {
        return CardTypeMapping.isAiShanDong(request.getCardType()) ? "01" : request.getIssueChannelCode();
    }


    /**
     * 支付宝出行渠道（且非爱山东）时，行业数据推的是支付宝那个 URL、不是 APP 网关。
     */
    private boolean isAlipayTripChannel(NotifyVerifyResultReqDTO request) {
        return request != null
                && IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode())
                && !CardTypeMapping.isAiShanDong(request.getCardType());
    }

    /**
     * 组装推给 APP 的业务参数，字段以甲方规范表 55「行业数据推送请求参数信息」为准。
     *
     * <p>{@code companionFlag} 取 {@code request.getCompanionFlag()}，来源是 account 的
     * {@code USER_ITP_REG_INFO.COMPANION_FLAG}，由 {@code GateCardTypeEnricher#applyActualCardType} 写入。
     * 该赋值发生在编排链路的「卡种富化」步骤，而本方法所在的异步任务在「下游推送」步骤才提交，
     * 因此读到的一定是 account 的真值——**NEVER 把卡种富化挪到 notifyVerifyResult 之后**。
     */
    private AppIndustryDataNotifyReqDTO buildNotifyRequest(NotifyVerifyResultReqDTO request, String cardData) {
        AppIndustryDataNotifyReqDTO notifyRequest = new AppIndustryDataNotifyReqDTO();
        notifyRequest.setThirdUserId(request.getItpUserId());
        notifyRequest.setCardId(request.getCardId());
        notifyRequest.setCardType(request.getCardType());
        notifyRequest.setCardData(cardData);
        notifyRequest.setCompanionFlag(request.getCompanionFlag());
        notifyRequest.setSignType("00");
        notifyRequest.setSign("");
        return notifyRequest;
    }

    /**
     * 推送码体给 APP 网关。
     *
     * <p>HTTP 收发与统一日志已上提到 {@link FormDataNotifyTemplate#post}，本方法只负责选目标与传参。
     * 「未受理」的业务上下文（cardId / thirdUserId / 状态 / txnSeq）由调用方
     * {@link #notifyVerifyResult} 那条 WARN 承载，**NEVER 因为骨架里的 WARN 没带这些字段就以为丢了**。
     *
     * @return true 仅当对方明确返回 {@code 0000}，判定口径见 {@link #isAccepted}
     */
    private boolean doNotifyIndustryData(AppIndustryDataNotifyReqDTO notifyRequest, String targetUrl, String deviceId) {
        return post(targetUrl, JSON.toJSONString(notifyRequest), deviceId, "行业数据推送");
    }

    /**
     * 应答判定钩子 —— <b>本链路 MUST 同时判 HTTP 2xx 与业务 {@code retCode}</b>。
     *
     * <p>2026-09-11 实测：该接口对无效卡号返回 {@code {"retCode":"7004","retMsg":"处理过程出现错误!"}}
     * 而 <b>HTTP 仍是 200</b>，因此「HTTP 通了」不等于「对方受理了」。此前只打印 httpCode 与 body、
     * 不做任何判定，一旦对方返 7004 日志里照样是「推送完成」，运维完全看不见，
     * <b>NEVER 退回那种写法，也 NEVER 退化成与支付宝链路同款的「只判 2xx」</b>。</p>
     */
    @Override
    protected boolean isAccepted(boolean httpSuccessful, String responseBody) {
        return httpSuccessful && APP_GATEWAY_SUCCESS_RET_CODE.equals(parseRetCode(responseBody));
    }

    /**
     * 从应答报文中取 {@code retCode}。
     *
     * <p>该接口的 {@code Content-Type} 实测是 {@code text/plain;charset=UTF-8} 而报文体是 JSON，
     * 因此 **NEVER 依赖 Content-Type 判断可否解析**，一律按 JSON 试解析，失败返回 null 交由调用方判失败。
     */
    private String parseRetCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return JSON.parseObject(responseBody).getString("retCode");
        } catch (Exception e) {
            log.warn("行业数据推送应答无法解析为 JSON, response={}", responseBody);
            return null;
        }
    }
}
