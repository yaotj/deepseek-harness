package com.chinasofti.huateng.alipay.paysign.service.impl.channelsync;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;


@Slf4j
@Service
public class ChannelSyncCompensationService {

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private ChannelSyncDeliverer channelSyncDeliverer;


    @Value("${alipay.channel-sync.max-retry-count:5}")
    private int maxRetryCount;

    @Value("${alipay.channel-sync.batch-size:200}")
    private int batchSize;


    public int compensate() {
        List<AlipaySignInfo> pending =
                alipaySignInfoMapper.selectCompensableChannelSync(maxRetryCount, batchSize);
        if (pending == null || pending.isEmpty()) {
            log.info("支付通道同步补偿：本轮无待处理行, maxRetryCount={}, batchSize={}", maxRetryCount, batchSize);
            return 0;
        }

        int succeeded = 0;
        for (AlipaySignInfo signInfo : pending) {
            String agreementCode = signInfo.getAgreementCode();
            if (!StringUtils.hasText(signInfo.getThirdUserId())
                    || !StringUtils.hasText(signInfo.getChannelUserAccount())) {
                log.error("支付通道同步补偿：签约行缺少出网必填字段，跳过并 MUST 人工核对, agreementCode={}, thirdUserId={}",
                        agreementCode, signInfo.getThirdUserId());
                continue;
            }
            try {
                if (channelSyncDeliverer.deliver(signInfo.getThirdUserId(),
                        signInfo.getChannelUserAccount(), agreementCode)) {
                    succeeded++;
                }
            } catch (Exception e) {
                log.error("支付通道同步补偿：单条重推异常，本轮继续, agreementCode={}", agreementCode, e);
            }
        }
        log.info("支付通道同步补偿完成, scanned={}, succeeded={}", pending.size(), succeeded);
        return succeeded;
    }
}
