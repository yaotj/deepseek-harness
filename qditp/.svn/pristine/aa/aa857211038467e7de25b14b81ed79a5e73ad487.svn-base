package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCardLog;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardLogMapper;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardMapper;
import com.chinasofti.huateng.account.service.EmployeeCardPersistenceService;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 员工码状态通知的单条事务落库实现。
 */
@Service
public class EmployeeCardPersistenceServiceImpl implements EmployeeCardPersistenceService {
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
            if (source.getCardStatus() == 4) {
                insertLog(source.getCardNo(), "CANCEL", source.getCardStatus(), "注销员工码不存在，无需新增");
                return;
            }

            UserAccEmployeeCard record = new UserAccEmployeeCard();
            applyAccInfo(record, source);
            record.setPhone(source.getPhone());
            record.setOpenTms(now);
            record.setCreateTms(now);
            record.setUpdateTms(now);
            employeeCardMapper.insert(record);
            insertLog(record.getCardNo(), "OPEN", record.getCardStatus(), "员工码开通成功");
            return;
        }

        if (!existing.getPhone().equals(source.getPhone())) {
            throw new IllegalArgumentException("手机号不可变更，请先注销后重新申请");
        }

        applyAccInfo(existing, source);
        if (source.getCardStatus() == 4) {
            existing.setCancelTms(now);
        }
        existing.setUpdateTms(now);
        employeeCardMapper.update(existing);
        insertLog(existing.getCardNo(), source.getCardStatus() == 4 ? "CANCEL" : "STATUS",
                existing.getCardStatus(), "员工码状态通知处理成功");
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

    private void insertLog(String cardNo, String eventType, Integer cardStatus, String remark) {
        UserAccEmployeeCardLog logRecord = new UserAccEmployeeCardLog();
        logRecord.setCardNo(cardNo);
        logRecord.setEventType(eventType);
        logRecord.setCardStatus(cardStatus);
        logRecord.setRemark(remark);
        logRecord.setCreateTms(LocalDateTime.now());
        employeeCardLogMapper.insert(logRecord);
    }
}
