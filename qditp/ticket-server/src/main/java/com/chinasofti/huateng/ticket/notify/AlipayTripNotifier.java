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

/**
 * 支付宝行程推送链路 —— 查线路代码，再把行程推给支付宝。
 *
 * <p>2026-09-14 从 {@code AppNotifyServiceImpl}（425 行）拆出（ADR-D61）。与
 * {@link IndustryDataNotifier} 是**两条不同的外发链路**，判定口径也不同：本链路的应答
 * **只判 HTTP 2xx**（对方没有业务 retCode 约定），而行业数据那条 MUST 显式判 {@code retCode}。
 * 两者混在一个类里时，很容易把其中一侧的判定「顺手统一」到另一侧。
 *
 * <p><b>发出去的报文一字未改</b>：支付宝契约已联调通过（用户 2026-09-14 确认），
 * {@code transTime} / {@code tirpNo} / {@code transLine} 的取值与兜底规则全部保持原样，
 * <b>NEVER 借重构或修可观测性之名改这三个字段的值、或在取值退化时改成拒推</b>。
 */
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

    /**
     * 组装 + 推送整条链路。**在调用方的异步任务里执行，本方法内 NEVER 再起线程。**
     */
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
        // 交易序列号：itpUserId + handleDateTime + trxType
        String itpUserId = request.getItpUserId();
        String handleDateTime = request.getHandleDateTime();
        String trxType = request.getTrxType();
        String tirpNo = (StringUtils.hasText(itpUserId) ? itpUserId : "") +
                (StringUtils.hasText(handleDateTime) ? handleDateTime : "") +
                (StringUtils.hasText(trxType) ? trxType : "");
        // 三段各自兜底成空串，因此任一段缺失时键会变短、全缺时是空串，且该键在「同用户同秒同交易类型」
        // 本身不唯一（真正单调的 ticketTransSeq 没进这个键）。契约已联调通过、NEVER 改键形状，
        // 但缺段时 MUST 留 ERROR：对方若按此键去重，短键 / 空键会把不同笔并成一笔。
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
        // yyyyMMddHHmmss -> yyyy-MM-dd HH:mm:ss
        return handleDateTime.substring(0, 4) + "-" +
               handleDateTime.substring(4, 6) + "-" +
               handleDateTime.substring(6, 8) + " " +
               handleDateTime.substring(8, 10) + ":" +
               handleDateTime.substring(10, 12) + ":" +
               handleDateTime.substring(12, 14);
    }

    /**
     * 按车站代码取所属线路代码，供支付宝报文的 {@code transLine} 使用。
     *
     * <p><b>2026-09-14 起走 para-server 的 {@code /ci/app/requestStationLineInfo}，
     * NEVER 回退成读本模块的 {@code STATION_INFO} 表。</b>原实现读的是本模块自建的一张
     * 同名维表（4 列、无 {@code PARA_VER_NO}、建表脚本里用 {@code MERGE} 硬编码灌了约 190 个车站），
     * 与参数域 owner 的 {@code TBL_STATION_INFO} 是两张表：后者随 ACC 参数文件版本推进，
     * 前者不会动。于是新开线路 / 车站改名 / 线路代码调整之后，两张表**静默不一致**，
     * 而下面那条兜底会把车站代码当成线路代码推给支付宝 —— 表现是「对方收到一个假 `transLine`」
     * 而不是报错。同模块的 {@code AlipayIndustryDetailAssembler.queryStationLineInfo}
     * 早已是这个写法，本方法此前是唯一的例外。
     *
     * <p>兜底规则**一字未改**：查不到 / 调用失败时仍返回 {@code stationCode} 本身。
     * 支付宝契约已按此联调通过，**NEVER 改成 null，也 NEVER 改成拒推** ——
     * 这条链路只判 HTTP 2xx、失败不重试，抛异常等于直接丢掉这笔行程。
     */
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
        // 兜底：线路代码未知时，用车站代码作为线路代码（支付宝契约已按此联调通过，NEVER 改成 null）
        log.warn("车站线路代码未找到，按原契约兜底用车站代码替代, stationCode={}", stationCode);
        return stationCode;
    }

    /**
     * 应答判定钩子 —— <b>本链路只判 HTTP 2xx</b>，{@code responseBody} 只进日志、不参与判定。
     *
     * <p>支付宝侧**没有业务 retCode 约定**，因此这里 <b>NEVER 加 retCode 判定</b>；
     * 反过来 {@code IndustryDataNotifier} 的同名钩子 <b>NEVER 退化成只判 2xx</b>
     * （那条链路实测 HTTP 200 + {@code retCode=7004} 也算失败）。两者是**同一个抽象方法的两份实现**，
     * 由 {@code FormDataNotifyTemplate} 强制各自申明，改一侧不会波及另一侧。</p>
     */
    @Override
    protected boolean isAccepted(boolean httpSuccessful, String responseBody) {
        return httpSuccessful;
    }

    /**
     * 推送行程给支付宝。
     *
     * <p>发出的 HTTP 报文（URL / form-data / bizData JSON key 与 value）严格保持原样，
     * 只判 HTTP 2xx 并留应答日志。支付宝契约已联调通过，**NEVER 改报文形状**。
     *
     * <p>返回值当前**被调用方丢弃**（本链路失败即丢、无补偿）。保留返回值是为了让
     * {@code notifyVerifyResult} 那种「未受理即挂补偿」的写法将来能直接接上，
     * <b>NEVER 因为「现在没人用」就把它改回 void</b>。
     */
    private boolean doNotifyAlipayTripData(String bizData) {
        return post(alipayPushTransDataUrl, bizData, "", "支付宝行程数据推送");
    }
}
