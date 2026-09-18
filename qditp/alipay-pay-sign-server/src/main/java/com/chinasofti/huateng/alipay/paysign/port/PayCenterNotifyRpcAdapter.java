package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link PayCenterNotifyPort} 的唯一实现：直连 {@code util.PayCenterClient} 的两条通知接口。
 *
 * <p>本类刻意**不吞异常、不做判定**：{@code PayCenterClient.callPayCenter} 已经把
 * {@code IOException} 与非 2xx 吞成 {@code null}，端口层再吞一层等于两处沉默
 * （与 {@link PayCenterRpcAdapter} 同口径）；成功判定在 {@code notify.PaymentNotifyAdapter} 内。
 */
@Component
public class PayCenterNotifyRpcAdapter implements PayCenterNotifyPort {

    private final PayCenterClient payCenterClient;

    public PayCenterNotifyRpcAdapter(PayCenterClient payCenterClient) {
        this.payCenterClient = payCenterClient;
    }

    @Override
    public PayCenterResponse blacklistNotify(Map<String, Object> bizData) {
        return payCenterClient.blacklistNotify(bizData);
    }

    @Override
    public PayCenterResponse closeResultNotify(Map<String, Object> bizData) {
        return payCenterClient.closeResultNotify(bizData);
    }
}
