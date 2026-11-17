package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.entity.BlacklistReleased;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.mapper.BlacklistReleasedMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class BlacklistReleaseFinalizer {

    private static final Logger log = LoggerFactory.getLogger(BlacklistReleaseFinalizer.class);

    private static final String CHANNEL_SYNC_SUCCESS = "SUCCESS";

    private final BlacklistMapper blacklistMapper;
    private final BlacklistReleasedMapper blacklistReleasedMapper;

    public BlacklistReleaseFinalizer(BlacklistMapper blacklistMapper,
                                     BlacklistReleasedMapper blacklistReleasedMapper) {
        this.blacklistMapper = blacklistMapper;
        this.blacklistReleasedMapper = blacklistReleasedMapper;
    }


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
