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

    /**
     * {@code CARD_STATUS=4} 注销，业务上是终态。
     */
    private static final EmployeeCardStatus CANCELED = EmployeeCardStatus.CANCELED;
    /**
     * {@code CARD_STATUS=3} 未启用，业务上是起始态。
     */
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
            log.warn("员工码激活结果回写影响0行（并发删除？）, cardNo={}, targetStatus={}", cardNo, targetStatus);
            return false;
        }
        insertLog(employeeCard.getCardNo(), EmployeeCardEvent.STATUS, targetStatus, remark);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshProfileFromAcc(UserAccEmployeeCard target, EmployeeCardInfoDTO source) {
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

    /**
     * 见 {@link EmployeeCardPersistenceService#attachEmployeeCardsQuietly}；NEVER 加 {@code @Transactional}。
     */
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
