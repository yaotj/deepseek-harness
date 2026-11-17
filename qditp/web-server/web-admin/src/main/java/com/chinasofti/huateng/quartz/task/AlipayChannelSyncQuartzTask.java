package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component("alipayChannelSyncQuartzTask")
public class AlipayChannelSyncQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(AlipayChannelSyncQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final AlipayPaySignClient alipayPaySignClient;

    public AlipayChannelSyncQuartzTask(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }


    public void compensate() {
        QuartzTraceUtils.runWithTrace(this::compensateInTrace);
    }

    private void compensateInTrace(String traceId) {
        AlipayCommonResponse response =
                alipayPaySignClient.compensateChannelSync(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("支付通道同步补偿接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getRetCode())) {
            throw new IllegalStateException("支付通道同步补偿失败, retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("支付通道同步补偿完成, retMsg={}", response.getRetMsg());
    }
}
