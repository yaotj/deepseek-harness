package com.chinasofti.huateng.alipay.paysign.service.impl.channelsync;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.port.AccountChannelPort;
import com.chinasofti.huateng.model.domain.SyncStatus;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 「支付通道同步」的出网 + 回写，**全模块唯一一份**（ADR-D132）。
 *
 * <p>为什么要单独一个类：这段判定有两个调用点 —— 签约链路提交后的首推
 * （{@code AlipayContractServiceImpl.addContract}）与补偿扫描的重推
 * （{@code ChannelSyncCompensationService}）。**两处 MUST 走同一份三态映射**：
 * 一旦分成两份，「哪种失败值得重推」这条判断就会漂移，而漂移的表现是静默的
 * —— 业务拒绝被反复重推、或不可达被当成终态丢掉，两者都不报错。</p>
 *
 * <p>命名与 pay-sign 侧的 {@code ChannelSyncDeliverer} 一致（ADR-D48）：那边清理账户域通道、
 * 这边写入账户域通道，方向相反但形状同源，**看到同名类 NEVER 假设是同一个类**，两者不在同一个模块。</p>
 *
 * <p>本类原为包私有，2026-09-18 按域拆包后 contract / channelsync 分属两包，跨包注入需要 public。</p>
 */
@Slf4j
@Component
public class ChannelSyncDeliverer {

    /** 支付通道同步的唯一出网口（ADR-D131）。 */
    @Autowired
    private AccountChannelPort accountChannelPort;

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    /**
     * 出网同步一条签约的支付通道，并按三态回写 outbox 四列。
     *
     * <p><b>本方法 NEVER 抛异常</b>：首推场景下签约已成立，出网失败不该让上游收到失败、更不该引来重推；
     * 补偿场景下一条失败也不该打断整批。未成功的行留在
     * {@code CHANNEL_SYNC_STATUS != 'SUCCESS'} 上等下一轮。</p>
     *
     * <p>{@code BizRejected} 与 {@code Unreachable} **都落 {@code FAILED}**（那一列只有三个取值），
     * 靠结果文案前缀区分：{@code BIZ_REJECTED:} 的行会被扫描 SQL 排除掉、只等人工，
     * {@code UNREACHABLE:} 的行才会被重推。**改这两个前缀 MUST 同时改
     * {@code AlipaySignInfoMapper.selectCompensableChannelSync} 的 SQL。**</p>
     *
     * @return 是否已收口成 {@code SUCCESS}
     */
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

    /** 回写 outbox 本身再失败就只能记日志：它是补偿的入口，不是业务成败的判据。 */
    private void markChannelSync(String agreementCode, SyncStatus status, String result) {
        try {
            alipaySignInfoMapper.updateChannelSync(agreementCode, status.name(), result, LocalDateTime.now());
        } catch (Exception e) {
            log.error("回写支付通道同步状态失败, agreementCode={}, status={}", agreementCode, status, e);
        }
    }
}
