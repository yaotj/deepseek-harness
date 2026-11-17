package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component("alipayRefundQueryQuartzTask")
public class AlipayRefundQueryQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(AlipayRefundQueryQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final AlipayPaySignClient alipayPaySignClient;

    public AlipayRefundQueryQuartzTask(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }


    public void compensate() {
        QuartzTraceUtils.runWithTrace(this::compensateInTrace);
    }

    private void compensateInTrace(String traceId) {
        AlipayCommonResponse response =
                alipayPaySignClient.compensateRefundQuery(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("退款回查补偿接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getRetCode())) {
            throw new IllegalStateException("退款回查补偿失败, retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("退款回查补偿完成, retMsg={}", response.getRetMsg());
    }
}
