package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.domain.EmployeeCardEvent;
import com.chinasofti.huateng.account.domain.EmployeeCardStatus;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCardLog;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardLogMapper;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardMapper;
import com.chinasofti.huateng.account.service.EmployeeCardPersistenceService;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 员工码状态通知的单条事务落库实现。
 */
@Service
public class EmployeeCardPersistenceServiceImpl implements EmployeeCardPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeCardPersistenceServiceImpl.class);

    /** {@code CARD_STATUS=4} 注销，业务上是终态。取值定义在 {@link EmployeeCardStatus}。 */
    private static final EmployeeCardStatus CANCELED = EmployeeCardStatus.CANCELED;
    /** {@code CARD_STATUS=3} 未启用，业务上是起始态。 */
    private static final EmployeeCardStatus NOT_ENABLED = EmployeeCardStatus.NOT_ENABLED;

    private final UserAccEmployeeCardMapper employeeCardMapper;
    private final UserAccEmployeeCardLogMapper employeeCardLogMapper;

    public EmployeeCardPersistenceServiceImpl(UserAccEmployeeCardMapper employeeCardMapper,
                                              UserAccEmployeeCardLogMapper employeeCardLogMapper) {
        this.employeeCardMapper = employeeCardMapper;
        this.employeeCardLogMapper = employeeCardLogMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFromStatusNotify(EmployeeCardInfoDTO source) {
        UserAccEmployeeCard existing = employeeCardMapper.selectByCardNo(source.getCardNo());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            if (CANCELED.is(source.getCardStatus())) {
                insertLog(source.getCardNo(), EmployeeCardEvent.CANCEL, source.getCardStatus(), "注销员工码不存在，无需新增");
                return;
            }

            UserAccEmployeeCard record = new UserAccEmployeeCard();
            applyAccInfo(record, source);
            record.setPhone(source.getPhone());
            record.setOpenTms(now);
            record.setCreateTms(now);
            record.setUpdateTms(now);
            employeeCardMapper.insert(record);
            insertLog(record.getCardNo(), EmployeeCardEvent.OPEN, record.getCardStatus(), "员工码开通成功");
            return;
        }

        if (!existing.getPhone().equals(source.getPhone())) {
            throw new IllegalArgumentException("手机号不可变更，请先注销后重新申请");
        }

        Integer previousStatus = existing.getCardStatus();
        String backwardMark = backwardTransitionMark(previousStatus, source.getCardStatus());
        if (backwardMark != null) {
            log.warn("ACC员工码状态通知出现逆向跃迁, 已按ACC权威放行, 需人工核对, cardNo={}, {}",
                    source.getCardNo(), backwardMark);
        }

        applyAccInfo(existing, source);
        if (CANCELED.is(source.getCardStatus())) {
            existing.setCancelTms(now);
        }
        existing.setUpdateTms(now);
        if (employeeCardMapper.update(existing) == 0) {
            // 上面已按 cardNo 查到行，UPDATE 仍 0 行只可能是这一瞬被并发删除。
            // 这里 MUST 抛而不是只记日志：调用方 EmployeeCardServiceImpl.processAppBatch 会捕获
            // RuntimeException、补一条 log 表记录并把该卡放进 failList，ACC 收到 PARTIAL_SUCCESS
            // 后重推，届时走上面 existing == null 的新增分支自愈。只记日志则本行状态永久停在旧值，
            // 而下面的 insertLog 还会写一条「处理成功」，事后连排查线索都是错的。
            throw new IllegalStateException("员工码状态落库影响0行（并发删除？）, cardNo=" + source.getCardNo());
        }
        String remark = backwardMark == null
                ? "员工码状态通知处理成功"
                : "员工码状态通知处理成功[逆向跃迁 " + backwardMark + "，已放行待核对]";
        insertLog(existing.getCardNo(), CANCELED.is(source.getCardStatus())
                        ? EmployeeCardEvent.CANCEL : EmployeeCardEvent.STATUS,
                existing.getCardStatus(), remark);
    }

    /**
     * 判断 ACC 下发的状态是否属于「逆向跃迁」，是则返回形如 {@code 4->1} 的标记、否则返回 {@code null}。
     *
     * <p>正常生命周期是 {@code 3 未启用 -> 1 启用 <-> 2 禁用 -> 4 注销}，因此两类可疑：
     * ①从终态 {@code 4} 回到任何非注销状态；②从已开通过的 {@code 1} / {@code 2} 回到起始态 {@code 3}。</p>
     *
     * <p><b>只留痕、NEVER 据此拒绝</b>：ACC 是权威发卡方，拒绝它的通知会造成「ACC 已注销、本地仍启用」
     * 这类永久不一致，比放行更糟（与 {@code applyActivationResult} 的白名单是两类语义，NEVER 混同）。
     * 本告警的用途是让这类通知**可被事后发现**，据此再去向甲方确认业务含义。</p>
     */
    private String backwardTransitionMark(Integer previousStatus, Integer newStatus) {
        if (previousStatus == null || newStatus == null || previousStatus.equals(newStatus)) {
            return null;
        }
        boolean fromCanceled = CANCELED.is(previousStatus);
        boolean backToNotEnabled = NOT_ENABLED.is(newStatus);
        if (fromCanceled || backToNotEnabled) {
            return previousStatus + "->" + newStatus;
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyActivationResult(String cardNo, int targetStatus, boolean markOpenTms, String remark) {
        UserAccEmployeeCard employeeCard = employeeCardMapper.selectByCardNo(cardNo);
        if (employeeCard == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        employeeCard.setCardStatus(targetStatus);
        if (markOpenTms && employeeCard.getOpenTms() == null) {
            employeeCard.setOpenTms(now);
        }
        employeeCard.setUpdateTms(now);
        if (employeeCardMapper.update(employeeCard) == 0) {
            // 与上面 selectByCardNo == null 是同一种结局（本地没写成），因此复用 false 这条出口：
            // 调用方 EmployeeCardServiceImpl.activateEmployeeCard 收到 false 会抛
            // IllegalStateException、开异常工单并返 9998「ACC 已受理但本地回写失败」。
            // NEVER 改成只记日志后 return true —— ACC 侧状态已变更，本地静默停在旧值就再也没人发现。
            log.warn("员工码激活结果回写影响0行（并发删除？）, cardNo={}, targetStatus={}", cardNo, targetStatus);
            return false;
        }
        insertLog(employeeCard.getCardNo(), EmployeeCardEvent.STATUS, targetStatus, remark);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshProfileFromAcc(UserAccEmployeeCard target, EmployeeCardInfoDTO source) {
        // 这条路径只为补齐姓名等资料列，入口（IF3A 查询）**没有**走 validateCard，
        // 因此 ACC 报文可能不带 cardNo / cardStatus。而 applyAccInfo 会无条件覆盖这两列，
        // 它们在 USER_ACC_EMPLOYEE_CARD 上都是 NOT NULL，覆盖成 null 会让 UPDATE 抛约束异常、
        // 一路冒到全局异常处理器（retCode 退化成 UUID）。MUST 保留原值。
        String originalCardNo = target.getCardNo();
        Integer originalCardStatus = target.getCardStatus();
        applyAccInfo(target, source);
        if (!StringUtils.hasText(target.getCardNo())) {
            target.setCardNo(originalCardNo);
        }
        if (target.getCardStatus() == null) {
            target.setCardStatus(originalCardStatus);
        }
        target.setUpdateTms(LocalDateTime.now());
        if (employeeCardMapper.update(target) == 0) {
            // 这里 MUST 只记日志、NEVER 抛：本方法挂在 IF3A 员工码查询这条只读链路上
            // （EmployeeCardServiceImpl.queryEmployeeCard），抛出会让一次查询退化成全局异常处理器的
            // UUID retCode。资料回填是尽力而为的旁路，失败不影响本次响应——响应用的是内存里已被
            // applyAccInfo 更新过的 target 对象，下次查询还会再试一次。
            log.warn("员工码资料回填影响0行（并发删除？），本次查询不受影响, cardNo={}", target.getCardNo());
        }
    }

    private void applyAccInfo(UserAccEmployeeCard target, EmployeeCardInfoDTO source) {
        target.setCardNo(source.getCardNo());
        target.setEmployeeName(source.getEmployeeName());
        target.setIdCardNo(source.getIdCardNo());
        target.setCompany(source.getCompany());
        target.setCenter(source.getCenter());
        target.setDepartment(source.getDepartment());
        target.setPosition(source.getPosition());
        target.setPhotoUrl(source.getPhotoUrl());
        target.setCardStatus(source.getCardStatus());
    }

    @Override
    public void recordEvent(String cardNo, EmployeeCardEvent eventType, Integer cardStatus, String remark) {
        insertLog(cardNo, eventType, cardStatus, remark);
    }

    /** 见 {@link EmployeeCardPersistenceService#attachEmployeeCardsQuietly}；NEVER 加 {@code @Transactional}。 */
    @Override
    public void attachEmployeeCardsQuietly(String thirdUserId, String msisdn) {
        if (!StringUtils.hasText(thirdUserId) || !StringUtils.hasText(msisdn)) {
            return;
        }
        try {
            var cards = employeeCardMapper.selectActiveByPhone(msisdn.trim());
            if (cards == null || cards.isEmpty()) {
                return;
            }
            for (var card : cards) {
                if (employeeCardMapper.updateThirdUserId(card.getCardNo(), thirdUserId) == 0) {
                    log.warn("员工码挂接ITP用户影响0行（已被他人占用或非正常态）, cardNo={}, thirdUserId={}",
                            card.getCardNo(), thirdUserId);
                } else {
                    log.info("员工码已挂接到ITP用户, cardNo={}, thirdUserId={}", card.getCardNo(), thirdUserId);
                }
            }
        } catch (Exception e) {
            log.error("按手机号挂接员工码异常，开户结果不受影响, thirdUserId={}", thirdUserId, e);
        }
    }

    private void insertLog(String cardNo, EmployeeCardEvent eventType, Integer cardStatus, String remark) {
        UserAccEmployeeCardLog logRecord = new UserAccEmployeeCardLog();
        logRecord.setCardNo(cardNo);
        logRecord.setEventType(eventType.name());
        logRecord.setCardStatus(cardStatus);
        logRecord.setRemark(remark);
        logRecord.setCreateTms(LocalDateTime.now());
        employeeCardLogMapper.insert(logRecord);
    }
}
