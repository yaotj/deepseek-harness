package com.chinasofti.huateng.alipay.paysign.port;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link PayCenterPort} 的唯一实现：把 {@link PayCenterClient} 的 {@code PayCenterResponse}
 * 翻成 {@link PayCenterReply}（ADR-D131）。
 *
 * <p>翻译规则**逐字沿用改造前三个调用点里那份相同的判定**，一处都没有改：</p>
 * <ol>
 *   <li>响应为 {@code null} → {@link PayCenterReply.NoBizAnswer}（{@code code}/{@code msg} 全 null）；</li>
 *   <li>{@code code == 200 || Boolean.TRUE.equals(success)} 不成立 → {@code NoBizAnswer}，
 *       带上原始 {@code code}/{@code success}/{@code msg} 与响应体供落库留证；</li>
 *   <li>成立 → {@link PayCenterReply.Accepted}，data 解开一次，
 *       {@code retCode} 缺失时退到 {@code returnCode}、{@code retMsg} 退到 {@code returnMsg}。</li>
 * </ol>
 *
 * <p><b>本类不吞异常</b>：{@code PayCenterClient.callPayCenter} 已经把 {@code IOException} 与非 2xx
 * 吞成 {@code null}（这本身是既有缺陷、列进批次 6 待办），端口层再包一层 try-catch 等于两处沉默、
 * 排障时连栈都拿不到。与内部 rpc 方向的 {@code DebitSyncRpcAdapter} / {@code BlacklistRpcAdapter}
 * 刻意不同 —— 那两个的对端是本项目服务、翻 {@code Unreachable} 后有补偿队列可进。</p>
 *
 * <p><b>NEVER 在本类里判「业务成功」</b>：三个方向对 {@code retCode != SUCCESS} 的处置完全不同
 * （支付要加黑名单、退款落 FAIL、查询回写 payStatus），判定留在各自服务里。</p>
 */
@Component
public class PayCenterRpcAdapter implements PayCenterPort {

    @Autowired
    private PayCenterClient payCenterClient;

    @Override
    public PayCenterReply requestPay(Map<String, Object> bizData) {
        return translate(payCenterClient.requestPay(bizData));
    }

    @Override
    public PayCenterReply payQuery(Map<String, Object> bizData) {
        return translate(payCenterClient.payQuery(bizData));
    }

    @Override
    public PayCenterReply requestRefund(Map<String, Object> bizData) {
        return translate(payCenterClient.requestRefund(bizData));
    }

    private PayCenterReply translate(PayCenterResponse response) {
        if (response == null) {
            return new PayCenterReply.NoAnswer();
        }
        String rawBody = JSON.toJSONString(response);
        boolean transportOk = (response.getCode() != null && response.getCode() == 200)
                || Boolean.TRUE.equals(response.getSuccess());
        if (!transportOk) {
            return new PayCenterReply.Rejected(
                    response.getCode(), response.getSuccess(), response.getMsg(), rawBody);
        }
        Map<String, Object> data = payCenterClient.decodeDataMap(response);
        String retCode = firstNonNull(data.get("retCode"), data.get("returnCode"));
        String retMsg = firstNonNull(data.get("retMsg"), data.get("returnMsg"));
        return new PayCenterReply.Accepted(
                response.getCode(), response.getSuccess(), response.getMsg(), rawBody,
                retCode, retMsg, data);
    }

    /** {@code retCode -> returnCode} 兜底，与改造前三处逐字一致的写法。 */
    private String firstNonNull(Object primary, Object fallback) {
        if (primary != null) {
            return primary.toString();
        }
        return fallback == null ? null : fallback.toString();
    }
}
