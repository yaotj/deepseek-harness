package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.domain.ArchiveDecision;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserItpRegLog;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegLogMapper;
import com.chinasofti.huateng.account.mapper.UserPayChannelMapper;
import com.chinasofti.huateng.account.service.AccountArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 销户归档实现。
 */
@Service
public class AccountArchiveServiceImpl implements AccountArchiveService {
    private static final Logger log = LoggerFactory.getLogger(AccountArchiveServiceImpl.class);

    /**
     * {@code USER_ITP_REG_LOG.OPER_TYPE} = 销户归档。
     */
    private static final int OPER_TYPE_USER_INFO_ARCHIVE = 3;

    private final UserItpRegInfoMapper userItpRegInfoMapper;
    private final UserItpRegLogMapper userItpRegLogMapper;
    private final UserPayChannelMapper userPayChannelMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 构造器注入（ADR-D37）。
     */
    public AccountArchiveServiceImpl(UserItpRegInfoMapper userItpRegInfoMapper,
                                     UserItpRegLogMapper userItpRegLogMapper,
                                     UserPayChannelMapper userPayChannelMapper,
                                     TransactionTemplate transactionTemplate) {
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userItpRegLogMapper = userItpRegLogMapper;
        this.userPayChannelMapper = userPayChannelMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * <b>三步的顺序不可调换。</b>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void archiveIfLastChannelRemoved(String thirdUserId) {
        List<UserItpRegInfo> allRegInfos = userItpRegInfoMapper.selectAnyListByThirdUserIdForUpdate(thirdUserId);
        if (allRegInfos == null || allRegInfos.isEmpty()) {
            log.info("该用户已无开户记录，无需销户归档, thirdUserId={}", thirdUserId);
            return;
        }

        int remainingChannels = userPayChannelMapper.countByThirdUserId(thirdUserId);
        ArchiveDecision.Result decision = ArchiveDecision.decide(allRegInfos, remainingChannels);
        if (!decision.shouldArchive()) {
            logSkipped(thirdUserId, allRegInfos.size(), remainingChannels, decision);
            return;
        }

        int deleted = userItpRegInfoMapper.deleteCanceledByThirdUserId(thirdUserId);
        if (deleted == 0) {
            log.warn("销户归档未删除任何行，已跳过归档日志，本次不算归档完成, thirdUserId={}, regInfoCount={}",
                    thirdUserId, allRegInfos.size());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (UserItpRegInfo regInfo : allRegInfos) {
            UserItpRegLog archiveLog = new UserItpRegLog();
            archiveLog.setCardId(regInfo.getCardId());
            archiveLog.setCardType(regInfo.getCardType());
            archiveLog.setThirdUserId(regInfo.getThirdUserId());
            archiveLog.setMsisdn(regInfo.getMsisdn());
            archiveLog.setOperDateTime(now);
            archiveLog.setOperType(OPER_TYPE_USER_INFO_ARCHIVE);
            userItpRegLogMapper.insert(archiveLog);
        }
        log.info("最后一个签约渠道已解绑，完成销户归档, thirdUserId={}, archived={}, deleted={}",
                thirdUserId, allRegInfos.size(), deleted);
    }

    /**
     * 不归档时把原因记清楚。
     */
    private void logSkipped(String thirdUserId, int regInfoCount, int remainingChannels,
                            ArchiveDecision.Result decision) {
        switch (decision.outcome()) {
            case CHANNEL_REMAINING -> log.info(
                    "解绑后该用户仍有支付通道，不做销户归档, thirdUserId={}, remainingChannels={}",
                    thirdUserId, remainingChannels);
            case NOT_ALL_CANCELED -> {
                log.info("该用户存在未注销的开户记录，不做销户归档, thirdUserId={}, count={}",
                        thirdUserId, regInfoCount);
                if (decision.hasGhostRows()) {
                    log.warn("开户记录的DEL_YN既非有效也非已注销，该用户归档将永久无法完成, thirdUserId={}, ids={}",
                            thirdUserId, decision.ghostIds());
                }
            }
            default -> log.info("该用户已无开户记录，无需销户归档, thirdUserId={}", thirdUserId);
        }
    }

    /**
     * IF8A-42 销户后顺带尝试归档；失败只记 WARN，不影响销户结果。
     */
    @Override
    public void tryArchiveAfterCancel(String thirdUserId) {
        try {
            transactionTemplate.executeWithoutResult(status -> archiveIfLastChannelRemoved(thirdUserId));
        } catch (Exception e) {
            log.warn("IF8A-42销户后归档尝试失败, 不影响销户结果, 下次调用会重试, thirdUserId={}", thirdUserId, e);
        }
    }
}
