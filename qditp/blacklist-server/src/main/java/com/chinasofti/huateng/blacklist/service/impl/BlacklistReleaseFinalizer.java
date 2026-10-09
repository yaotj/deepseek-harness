package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.entity.BlacklistReleased;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.mapper.BlacklistReleasedMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解除黑名单的<b>阶段二</b>：解除通知确认推达渠道之后，才把行搬进历史表并从主表删除。
 *
 * <p><b>为什么单独一个 Bean</b>：这一步必须带事务（搬历史 + 删主表要原子），而它的调用方是
 * {@code BlacklistChannelSyncService}（无事务、负责出网）。若把它塞回 {@code BlacklistServiceImpl}，
 * 就成了 Service → SyncService → Service 的循环依赖 —— SyncService 是构造注入，循环会让容器起不来。
 * <b>NEVER 为了少一个类而把本方法搬回 BlacklistServiceImpl</b>。
 *
 * <p><b>删除与插入的顺序是刻意的</b>：先按 ID + STATUS='RELEASING' 做 CAS 删除，只有真删掉了才插历史。
 * 反过来写（先插历史再删）在「这行已被别处搬走」的并发下会往 BLACKLIST_RELEASED 里多插一条重复记录，
 * 而那张表没有唯一约束、拦不住。<b>NEVER 调换这两步。</b>
 */
@Service
public class BlacklistReleaseFinalizer {

    private static final Logger log = LoggerFactory.getLogger(BlacklistReleaseFinalizer.class);

    /** 搬进历史表时渠道同步已确认成功；该列在历史表里只作审计，不再驱动补偿。 */
    private static final String CHANNEL_SYNC_SUCCESS = "SUCCESS";

    private final BlacklistMapper blacklistMapper;
    private final BlacklistReleasedMapper blacklistReleasedMapper;

    public BlacklistReleaseFinalizer(BlacklistMapper blacklistMapper,
                                     BlacklistReleasedMapper blacklistReleasedMapper) {
        this.blacklistMapper = blacklistMapper;
        this.blacklistReleasedMapper = blacklistReleasedMapper;
    }

    /**
     * 收口一次解除：删主表行 + 写历史快照，两步同一个本地事务。
     *
     * <p><b>调用前提</b>：解除通知已拿到渠道成功应答。<b>NEVER 在通知未成功时调本方法</b> ——
     * 那等于退回「本地先解除、通知异步收敛」的乐观模式，本次改造就是为了消掉那个窗口。
     *
     * <p>本方法不出网、不做重试；`RELEASE_REASON` / `RELEASE_BY` 取阶段一暂存在主表上的值，
     * 因此即便中间经过了进程重启、由扫表补偿推成功，审计信息也不会丢。
     *
     * @param record 主表上那行 RELEASING 的快照（由调用方按 cardId 回查得到）
     * @return true 表示本次真的完成了收口；false 表示该行已被别处收口，本次无需处理
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean completeRelease(Blacklist record) {
        int deleted = blacklistMapper.deleteReleasedById(record.getId());
        if (deleted == 0) {
            log.info("解除收口时该行已被别处处理，跳过, id={}, cardId={}", record.getId(), record.getCardId());
            return false;
        }
        blacklistReleasedMapper.insert(toReleased(record));
        log.info("解除已收口：通知已推达渠道，主表行已删除并搬入历史, id={}, cardId={}",
                record.getId(), record.getCardId());
        return true;
    }

    /** 把主表行映射成历史快照，ORIGIN_ID 记住它原来的主键。 */
    private BlacklistReleased toReleased(Blacklist record) {
        BlacklistReleased released = new BlacklistReleased();
        released.setOriginId(record.getId());
        released.setCardId(record.getCardId());
        released.setThirdUserId(record.getThirdUserId());
        released.setCardType(record.getCardType());
        released.setChannelCode(record.getChannelCode());
        released.setBlackSource(record.getBlackSource());
        released.setBlackCause(record.getBlackCause());
        released.setBizNo(record.getBizNo());
        released.setReason(record.getReason());
        released.setCreateBy(record.getCreateBy());
        released.setCreateTime(record.getCreateTime());
        released.setReleaseReason(record.getReleaseReason());
        released.setReleaseBy(record.getReleaseBy());
        released.setChannelSyncStatus(CHANNEL_SYNC_SUCCESS);
        return released;
    }
}
