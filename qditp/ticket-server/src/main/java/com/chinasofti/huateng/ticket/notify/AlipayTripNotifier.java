package com.chinasofti.huateng.ticket.notify;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.alipaytrip.AlipayPushTransDataReqDTO;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.station.StationLineResolver;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 支付宝行程推送链路 —— 查线路代码，再把行程推给支付宝。 */
@Component
class AlipayTripNotifier extends FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(AlipayTripNotifier.class);
    /** {@code handleDateTime} 的规定长度 yyyyMMddHHmmss。 */
    private static final int HANDLE_DATE_TIME_LENGTH = 14;

    private final StationLineResolver stationLineResolver;

    @Value("${app.notify.alipay-push-trans-data-url:https://dtcustomer.bestonepay.com/ngopenplatform/notify/pushTransData}")
    private String alipayPushTransDataUrl;

    AlipayTripNotifier(StationLineResolver stationLineResolver,
                       @Qualifier("appNotifyHttpClient") OkHttpClient httpClient,
                       NotifyFormRequestFactory formRequestFactory) {
        super(httpClient, formRequestFactory);
        this.stationLineResolver = stationLineResolver;
    }

    /** 组装 + 推送整条链路。 */
    void pushTripData(NotifyVerifyResultReqDTO request) {
        AlipayPushTransDataReqDTO pushRequest = buildAlipayTripPushRequest(request);
        doNotifyAlipayTripData(JSON.toJSONString(pushRequest));
    }

    private AlipayPushTransDataReqDTO buildAlipayTripPushRequest(NotifyVerifyResultReqDTO request) {
        AlipayPushTransDataReqDTO pushRequest = new AlipayPushTransDataReqDTO();
        pushRequest.setLogicCard(request.getCardId());
        pushRequest.setTransType(request.getTrxType());
        pushRequest.setTransTime(convertHandleDateTime(request.getHandleDateTime()));
        pushRequest.setTransSeq(request.getTicketTransSeq());
        pushRequest.setTransStation(request.getHandleStationCode());
        pushRequest.setTransLine(resolveLineCode(request.getHandleStationCode()));
        String itpUserId = request.getItpUserId();
        String handleDateTime = request.getHandleDateTime();
        String trxType = request.getTrxType();
        String tirpNo = (StringUtils.hasText(itpUserId) ? itpUserId : "") +
                (StringUtils.hasText(handleDateTime) ? handleDateTime : "") +
                (StringUtils.hasText(trxType) ? trxType : "");
        if (!StringUtils.hasText(itpUserId) || !StringUtils.hasText(handleDateTime)
                || !StringUtils.hasText(trxType)) {
            log.error("支付宝行程键 tirpNo 缺段，已按原契约照旧推送，对方按该键去重时可能并单, "
                            + "cardId={}, tirpNo={}, itpUserId={}, handleDateTime={}, trxType={}",
                    request.getCardId(), tirpNo, itpUserId, handleDateTime, trxType);
        }
        log.info("生成行程 transSeq={}, itpUserId={}, handleDateTime={}, trxType={}",
                tirpNo, request.getItpUserId(), request.getHandleDateTime(), request.getTrxType());
        pushRequest.setTirpNo(tirpNo);
        pushRequest.setThirdUserId(request.getItpUserId());
        pushRequest.setCardId(request.getCardId());
        pushRequest.setCardType(request.getCardType());
        pushRequest.setSignType("00");
        pushRequest.setSign("");
        return pushRequest;
    }

    private String convertHandleDateTime(String handleDateTime) {
        if (handleDateTime == null || handleDateTime.length() < HANDLE_DATE_TIME_LENGTH) {
            log.warn("支付宝行程推送 handleDateTime 长度不足，按原值推送, handleDateTime={}", handleDateTime);
            return handleDateTime;
        }
        return handleDateTime.substring(0, 4) + "-" +
               handleDateTime.substring(4, 6) + "-" +
               handleDateTime.substring(6, 8) + " " +
               handleDateTime.substring(8, 10) + ":" +
               handleDateTime.substring(10, 12) + ":" +
               handleDateTime.substring(12, 14);
    }

    /** 按车站代码取所属线路代码，供支付宝报文的 {@code transLine} 使用。 */
    private String resolveLineCode(String stationCode) {
        if (!StringUtils.hasText(stationCode)) {
            return null;
        }
        RequestStationLineInfoResult result = stationLineResolver.resolveLineInfo(stationCode);
        if (stationLineResolver.isUsable(result)) {
            return result.getLineCode();
        }
        log.warn("查询车站线路代码未成功, stationCode={}, retCode={}, retMsg={}",
                stationCode,
                result == null ? null : result.getRetCode(),
                result == null ? null : result.getRetMsg());
        log.warn("车站线路代码未找到，按原契约兜底用车站代码替代, stationCode={}", stationCode);
        return stationCode;
    }

    /** 应答判定钩子 —— 本链路只判 HTTP 2xx，{@code responseBody} 只进日志、不参与判定。 */
    @Override
    protected boolean isAccepted(boolean httpSuccessful, String responseBody) {
        return httpSuccessful;
    }

    /** 推送行程给支付宝。 */
    private boolean doNotifyAlipayTripData(String bizData) {
        return post(alipayPushTransDataUrl, bizData, "", "支付宝行程数据推送");
    }
}
