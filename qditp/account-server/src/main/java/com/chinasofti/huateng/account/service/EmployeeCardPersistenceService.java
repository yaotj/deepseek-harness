package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;

/**
 * 员工码本地落库服务。
 */
public interface EmployeeCardPersistenceService {
    void saveFromStatusNotify(EmployeeCardInfoDTO source);
}
