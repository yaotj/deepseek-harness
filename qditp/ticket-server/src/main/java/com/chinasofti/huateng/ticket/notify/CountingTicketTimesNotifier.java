package com.chinasofti.huateng.ticket.notify;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.app.AppCountingTicketTimesNotifyReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IF8B 多日票次数扣减通知链路（甲方规格 R6 §3.63，{@code /app/receiveCountingTicketTimes}）。
 *
 * <p>触发点是 {@code GateDailyTicketCoordinator.markUsedOnExit} 里
 * **daily-ticket 已确认扣次成功之后**（`outcome.accepted()`）—— 扣次被拒或 RPC 技术失败时不通知，
 * 否则会造成「APP 以为扣了、实际没扣」。**NEVER 把调用点上移到 markUsed 之前。**
 *
 * <p>失败处置沿用本包既有口径：只打 WARN、**不落库不重试**（与 {@code IndustryDataNotifier} 一致）。
 * 这意味着**最后一次扣次的通知推失败即永久丢**；要补偿得先有载体表，见 {@code docs/domain/outbox.md} §七①。
 */
@Component
class CountingTicketTimesNotifier extends FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(CountingTicketTimesNotifier.class);
    /** APP 网关受理成功的 retCode。 */
    private static final String APP_GATEWAY_SUCCESS_RET_CODE = "0000";
    /** 日志与告警用的链路名。 */
    private static final String LINK_NAME = "多日票次数扣减通知";

    /**
     * 通知地址。**空默认值 + 由 K8s env 注入**：APP 侧地址未定时不猜、不写死测试域名
     * （本模块已有 1 处待清的 testngbackV2 硬编码，NEVER 再新增一处，见 AGENTS.md §8）。
     */
    @Value("${app.notify.counting-ticket-times-url:}")
    private String countingTicketTimesUrl;

    /** 开关，**默认关**：地址没回填前不产生无效外呼。 */
    @Value("${app.notify.counting-ticket-times-enabled:false}")
    private boolean countingTicketTimesNotifyEnabled;

    CountingTicketTimesNotifier(@Qualifier("appNotifyHttpClient") OkHttpClient httpClient,
                                NotifyFormRequestFactory formRequestFactory) {
        super(httpClient, formRequestFactory);
    }

    /**
     * 推送本次扣减次数给 APP。
     *
     * @param request 闸机上送的出站报文，{@code transSeq} 取其 {@code ticketTransSeq}
     * @param times 本次扣减次数，由调用方给出（当前恒为 1）
     */
    void notifyCountingTimes(NotifyVerifyResultReqDTO request, int times) {
        if (!countingTicketTimesNotifyEnabled) {
            log.info("{}已关闭, cardId={}, ticketTransSeq={}",
                    LINK_NAME, request.getCardId(), request.getTicketTransSeq());
            return;
        }
        if (!StringUtils.hasText(countingTicketTimesUrl)) {
            log.warn("{}地址未配置（app.notify.counting-ticket-times-url 为空），本次跳过, cardId={}",
                    LINK_NAME, request.getCardId());
            return;
        }
        AppCountingTicketTimesNotifyReqDTO notifyRequest = buildNotifyRequest(request, times);
        if (!post(countingTicketTimesUrl, JSON.toJSONString(notifyRequest),
                request.getDeviceId(), LINK_NAME)) {
            log.warn("{}未受理，本次不补偿, cardId={}, thirdUserId={}, transSeq={}, times={}",
                    LINK_NAME, notifyRequest.getCardId(), notifyRequest.getThirdUserId(),
                    notifyRequest.getTransSeq(), notifyRequest.getTimes());
        }
    }

    /** 组装业务参数，字段以甲方规格 R6 §3.63 表129 为准（4 个字段，NEVER 增减）。 */
    private AppCountingTicketTimesNotifyReqDTO buildNotifyRequest(NotifyVerifyResultReqDTO request, int times) {
        AppCountingTicketTimesNotifyReqDTO notifyRequest = new AppCountingTicketTimesNotifyReqDTO();
        notifyRequest.setThirdUserId(request.getItpUserId());
        notifyRequest.setCardId(request.getCardId());
        notifyRequest.setTransSeq(request.getTicketTransSeq());
        notifyRequest.setTimes(String.valueOf(times));
        if (!StringUtils.hasText(request.getTicketTransSeq())) {
            log.warn("{}缺 ticketTransSeq，已按原值推送，APP 侧可能对不上单, cardId={}",
                    LINK_NAME, request.getCardId());
        }
        return notifyRequest;
    }

    /** 应答判定钩子 —— 与行业数据链路同口径：HTTP 2xx 且 {@code retCode=0000} 才算受理。 */
    @Override
    protected boolean isAccepted(boolean httpSuccessful, String responseBody) {
        return httpSuccessful && APP_GATEWAY_SUCCESS_RET_CODE.equals(parseRetCode(responseBody));
    }

    /** 从应答报文中取 {@code retCode}。 */
    private String parseRetCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return JSON.parseObject(responseBody).getString("retCode");
        } catch (Exception e) {
            log.warn("{}应答无法解析为 JSON, response={}", LINK_NAME, responseBody);
            return null;
        }
    }
}
