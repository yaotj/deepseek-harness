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
 *
 * <p>2026-09-11 由 {@link AccountApplicationServiceImpl} 逐字搬迁而来（account-server 2.0.59）：
 * 三条件判定、日志文案、时间戳共用与两侧异常语义一并保留，<b>没有改任何行为</b>。
 * 搬过来的是 {@code archiveUserInfoIfLastChannelRemoved} / {@code tryArchiveAfterCancel}
 * 与它们独占的 {@code OPER_TYPE=3} 常量、三个只被归档用到的 mapper 方法
 * （{@code selectAnyListByThirdUserIdForUpdate} / {@code countByThirdUserId} / {@code deleteCanceledByThirdUserId}）。</p>
 *
 * <p><b>NEVER 让 Controller 直接依赖本类</b>：上游注入的是 {@code AccountApplicationService}，
 * 归档只是它两个业务动作的收尾步骤，暴露成第二个调用面会让「什么时候该归档」失去唯一判定处。</p>
 */
@Service
public class AccountArchiveServiceImpl implements AccountArchiveService {
    private static final Logger log = LoggerFactory.getLogger(AccountArchiveServiceImpl.class);

    /** {@code USER_ITP_REG_LOG.OPER_TYPE} = 销户归档。字典见 docs/business/account-employee-card.md。 */
    private static final int OPER_TYPE_USER_INFO_ARCHIVE = 3;

    private final UserItpRegInfoMapper userItpRegInfoMapper;
    private final UserItpRegLogMapper userItpRegLogMapper;
    private final UserPayChannelMapper userPayChannelMapper;
    private final TransactionTemplate transactionTemplate;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
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
     * <b>三步的顺序不可调换。</b>先用 {@code for update} 锁住该用户的开户记录，再数剩余支付通道：
     * 同一用户多渠道并发解绑时，后到的事务会阻塞在取锁这一步，等前一个提交后再 count，
     * 才能读到「全部渠道都已删除」的最新结果。若先 count 后取锁，两个事务会各自看到对方
     * 未提交的渠道仍存在、双方都跳过归档，用户信息永久残留且没有补偿路径。
     *
     * <p>为什么落在「最后一个渠道解绑完」而不是 IF8A-42：APP 顺序是 35 → 42 → 75，42 时支付通道还没解绑，
     * 那时删掉开户记录会让 75 的解约成功分支回调 {@code requestRemovePayChannel} 时查不到记录（返 8004），
     * 且 {@code selectAnyByThirdUserIdAndCardIdAndCardType} 兜底也失效。</p>
     * <p><b>{@code Propagation.MANDATORY} 不是可选项</b>：本方法第一步的 {@code for update} 只有在
     * 调用方已开事务时才持锁到提交；autocommit 下锁随语句结束即释放，上面那段并发保护会
     * <b>静默失效、不报任何错</b>。声明 MANDATORY 能让「没有外层事务」在运行时立刻暴露，
     * 而不是退化成没有互斥的版本。<b>NEVER 改成 REQUIRED</b> —— 那会让漏开事务的调用点
     * 自己开一个新事务、看起来正常，实际把 count 与锁拆到了两个事务里。</p>
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

        // MUST 先删再写日志、NEVER 反过来：反序时 deleted==0（口径不一致 / DEL_YN 幽灵值）会留下
        // N 条 OPER_TYPE=3 的归档日志却没删任何行，事后从日志看「已归档」、库里行还在，
        // 且这条路径与 logSkipped 的幽灵行 WARN 互斥、走不到一起，等于彻底无人知晓。
        int deleted = userItpRegInfoMapper.deleteCanceledByThirdUserId(thirdUserId);
        if (deleted == 0) {
            // 上面刚用 for update 锁住这些行、且 ArchiveDecision 已判定全部为已注销，
            // 到这里仍 0 行只能是 DEL_YN 取值与 deleteCanceledByThirdUserId 的条件不一致。
            // 这里 MUST 只 WARN、NEVER 抛：另一条入口 PayChannelServiceImpl.requestRemovePayChannel
            // 是带 @Transactional 的跨 Bean 调用，抛出会把「删支付通道」一起回滚 ——
            // 而那一步的远端（pay-sign 解约）已经收口，回滚只会制造新的不一致。
            log.warn("销户归档未删除任何行，已跳过归档日志，本次不算归档完成, thirdUserId={}, regInfoCount={}",
                    thirdUserId, allRegInfos.size());
            return;
        }

        // 同一次归档动作的多行共用一个时间戳，便于按 OPER_DATE_TIME 聚合出「这批是一次销户归档」
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
     *
     * <p><b>{@code NOT_ALL_CANCELED} 分支的两条日志级别不同，是刻意的</b>：
     * 「用户真的还有未注销的票卡」是正常业务分支（INFO），
     * 「{@code DEL_YN} 既非 1 也非 0」是数据缺陷 —— 那样的行对所有 {@code DEL_YN = 1} 的查询不可见、
     * {@code deleteCanceledByThirdUserId}（条件 {@code DEL_YN = 0}）也删不掉，
     * 该用户<b>永久无法归档且没有自愈路径</b>，因此 MUST 打 WARN 并留下主键（ADR-D41）。
     * 两者都表现为「有记录没全注销」，日志混在一起就再也分不出是哪一种。
     *
     * <p><b>NEVER 在这里顺手把幽灵行改成 0 或 1</b>：该按哪边算是业务口径问题，
     * 见 decisions.md ADR-D41 的待办（先查 {@code SELECT COUNT(*) ... WHERE DEL_YN IS NULL} 再谈约束）。
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
     *
     * <p><b>⚠️ 这里是类内自调用，{@link #archiveIfLastChannelRemoved} 上的
     * {@code Propagation.MANDATORY} 在这条路径上不生效</b>（ADR-D38 复核记录）：lambda 里的
     * {@code archiveIfLastChannelRemoved(...)} 是普通 Java 调用、不经过 AOP 代理，
     * 所以 MANDATORY 的「没有外层事务就抛 {@code IllegalTransactionStateException}」这道断言
     * <b>根本不会执行</b>。当前行为正确<b>纯粹因为 {@code transactionTemplate} 已经真的开了事务</b>，
     * 而不是因为那个注解在把关。</p>
     *
     * <p>因此 <b>NEVER 删掉这里的 {@code transactionTemplate}</b>，也 NEVER 改成直接调
     * {@code archiveIfLastChannelRemoved(thirdUserId)}：那样不会像预期的那样报错，而是
     * <b>静默退化成 autocommit</b> —— 被调方法第一步的 {@code for update} 锁随语句结束即释放，
     * 类注释里那段并发保护整段失效，且编译、单测、运行时都不报任何错。</p>
     *
     * <p>另一条入口 {@code PayChannelServiceImpl.requestRemovePayChannel} 是<b>跨 Bean</b> 调用，
     * 代理生效、MANDATORY 真实校验，那边不受本条影响。</p>
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
