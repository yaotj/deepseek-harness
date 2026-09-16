package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.employee.EmployeeCardNotifyReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyResult;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;
import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 员工码业务服务。
 */
public interface EmployeeCardService {
    EmployeeCardNotifyResult notifyEmployeeCardStatus(EmployeeCardNotifyReqDTO request);

    EmployeeCardQueryResult queryEmployeeCard(EmployeeCardQueryReqDTO request);

    /**
     * APP 请求激活 / 禁用电子员工卡：先调 ACC，ACC 成功后再回写本地状态与事件日志。
     *
     * <p>前置状态是<b>白名单</b>：{@code actionFlag=1}（激活）只接受 {@code CARD_STATUS=3} 未激活，
     * {@code actionFlag=0}（禁用）只接受 {@code CARD_STATUS=1} 正常，其余一律拒绝。</p>
     *
     * @param request 卡号 + 动作标志
     * @return {@code 0000} 成功；{@code 8001} 参数非法；{@code 8004} 员工码不存在；{@code 2002} 当前状态不允许；
     *         {@code 9998} ACC 已受理但本地回写失败（已开异常工单，需人工介入）；{@code 9999} 调 ACC 失败
     */
    CommonResult activateEmployeeCard(EmployeeCardActivateReqDTO request);

    CommonResult updateEmployeeInfo(EmployeeInfoUpdateNotifyReqDTO request);
}
