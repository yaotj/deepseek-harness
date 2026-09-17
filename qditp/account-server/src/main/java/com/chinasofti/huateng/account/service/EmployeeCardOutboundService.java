package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;

import java.util.List;
import java.util.Map;

/**
 * 员工码链路的出网协作者：对 APP 的静默开户注册、对 ACC 的资料查询与激活/禁用请求。
 */
public interface EmployeeCardOutboundService {
    /**
     * APP 注册结果：只承载「哪些卡号失败、失败原因是什么」。
     */
    record AppRegisterResult(Map<String, String> failReasons) {
        /**
         * @return 该卡号的失败原因；{@code null} 表示这一张成功
         */
        public String failureReasonOf(String cardNo) {
            return failReasons.get(cardNo);
        }
    }

    /**
     * 向 APP 批量注册员工码（ACC 的 {@code cardNo} 是员工号，不是逻辑卡号；静默开户由 APP 侧完成）。
     */
    AppRegisterResult registerToApp(List<EmployeeCardInfoDTO> batch);

    /**
     * 向 ACC 查询员工码资料（用于本地 {@code EMPLOYEE_NAME} 为空时补全）。
     *
     * @return 查到的资料；<b>地址未配置 / ACC 返回失败 / 调用异常一律返回 {@code null} 并记日志</b>，
     * 调用方 MUST 自行决定「查不到」时是降级还是报错
     */
    EmployeeCardInfoDTO queryFromAcc(String cardNo);

    /**
     * ACC 激活接口地址是否已配置。
     */
    boolean isActivationUrlConfigured();

    /**
     * 向 ACC 发起激活 / 禁用请求，返回响应体原文。
     */
    String requestActivation(EmployeeCardActivateReqDTO request);
}
