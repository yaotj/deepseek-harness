package com.chinasofti.huateng.alipay.paysign.service.impl.channelsync;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.port.AccountChannelPort;
import com.chinasofti.huateng.model.domain.SyncStatus;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@Slf4j
@Component
public class ChannelSyncDeliverer {

    @Autowired
    private AccountChannelPort accountChannelPort;

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;


    public boolean deliver(String thirdUserId, String channelUserAccount, String agreementCode) {
        RpcOutcome outcome = accountChannelPort.updatePaymentChannel(thirdUserId, channelUserAccount, agreementCode);
        switch (outcome) {
            case RpcOutcome.Ok ok -> {
                markChannelSync(agreementCode, SyncStatus.SUCCESS, "支付通道已同步");
                return true;
            }
            case RpcOutcome.BizRejected rejected -> {
                markChannelSync(agreementCode, SyncStatus.FAILED, "BIZ_REJECTED: " + rejected.retCode());
                return false;
            }
            case RpcOutcome.Unreachable unreachable -> {
                markChannelSync(agreementCode, SyncStatus.FAILED,
                        "UNREACHABLE: " + unreachable.cause().getClass().getSimpleName());
                return false;
            }
        }
    }

    private void markChannelSync(String agreementCode, SyncStatus status, String result) {
        try {
            alipaySignInfoMapper.updateChannelSync(agreementCode, status.name(), result, LocalDateTime.now());
        } catch (Exception e) {
            log.error("回写支付通道同步状态失败, agreementCode={}, status={}", agreementCode, status, e);
        }
    }
}
