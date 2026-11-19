package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("paymentCompensateQuartzTask")
public class PaymentCompensateQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompensateQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final PaySignClient paySignClient;

    public PaymentCompensateQuartzTask(PaySignClient paySignClient) {
        this.paySignClient = paySignClient;
    }


    public void compensatePayStatus() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            CompensateNotifyRespDTO response = paySignClient.compensatePayStatus(
                    QuartzTraceUtils.traceHeaders(traceId));
            if (response == null) {
                throw new IllegalStateException("支付状态回查补偿接口未返回响应");
            }
            if (!SUCCESS_CODE.equals(response.getResultCode())) {
                throw new IllegalStateException("支付状态回查补偿失败, resultCode=" + response.getResultCode()
                        + ", resultMsg=" + response.getResultMsg());
            }
            if (response.getSkipped() > 0) {
                log.warn("支付状态回查本轮有未收口记录（中心仍在途或查询失败，下轮重扫）, scanned={}, settled={}, pendingAgain={}",
                        response.getScanned(), response.getSubmitted(), response.getSkipped());
            }
            log.info("支付状态回查补偿完成, scanned={}, settled={}, pendingAgain={}",
                    response.getScanned(), response.getSubmitted(), response.getSkipped());
        });
    }
}
