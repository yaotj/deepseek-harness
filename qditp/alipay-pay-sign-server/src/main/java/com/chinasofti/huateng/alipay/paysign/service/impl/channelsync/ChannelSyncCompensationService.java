package com.chinasofti.huateng.alipay.paysign.service.impl.channelsync;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 「支付通道同步」的补偿扫描（ADR-D132）。
 *
 * <p>解决的问题：签约行落库后由 {@code AlipayContractServiceImpl.addContract} 提交后出网首推一次，
 * 首推不可达时那一行就停在 {@code CHANNEL_SYNC_STATUS != 'SUCCESS'} 上 ——
 * 在本类之前**没有任何人再看它一眼**，账户域缺一条支付通道、对上游完全不可见。</p>
 *
 * <p><b>本类刻意没有 {@code @Scheduled}</b>：按 AGENTS.md §2.2.1，本项目的补偿一律由 web-admin 的
 * Quartz 打 {@code /internal/**} 端点驱动，服务内自驱调度只剩 face-pay 与 collect-pay 两个模块。
 * <b>NEVER 在本类上加 {@code @Scheduled}</b> —— 加了就成了「同一份补偿两个驱动源」，
 * 而本模块多副本时没有任何分布式锁。</p>
 *
 * <p><b>本类也刻意不带 {@code @Transactional}</b>：整个方法体就是「扫一批 → 逐条出网 → 逐条回写」，
 * 出网绝不能被事务包住（§5.2 那条事故）。每条的回写各自自动提交，一条失败不影响其余。</p>
 */
@Slf4j
@Service
public class ChannelSyncCompensationService {

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private ChannelSyncDeliverer channelSyncDeliverer;

    /**
     * 重试次数上限（不含）。到顶的行不再自动重推、只等人工，避免对端长期不可用时无休止打它。
     */
    @Value("${alipay.channel-sync.max-retry-count:5}")
    private int maxRetryCount;

    /** 单轮取多少行。上限在 SQL 里，不是在 Java 里截断。 */
    @Value("${alipay.channel-sync.batch-size:200}")
    private int batchSize;

    /**
     * 扫一批未收口的支付通道同步并逐条重推。
     *
     * @return 本轮实际收口成 SUCCESS 的条数
     */
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
            // 出网入参全部取自签约行本身，因此扫描端不需要任何额外的载体表。
            // thirdUserId / channelUserAccount 缺失属数据缺陷：重推必然再失败，直接跳过并告警，
            // NEVER 拿空串去调对端 —— 那会把一条数据问题伪装成对端拒绝。
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
                // deliver 自身承诺不抛，这层只是兜住「承诺被后人改坏」的情况：
                // 一条炸掉不该让整批停下，否则前面几条的成果也拿不到。
                log.error("支付通道同步补偿：单条重推异常，本轮继续, agreementCode={}", agreementCode, e);
            }
        }
        log.info("支付通道同步补偿完成, scanned={}, succeeded={}", pending.size(), succeeded);
        return succeeded;
    }
}
