package com.chinasofti.huateng.fep.acc.controller;

import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyResult;
import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工码相关接口入口。
 */
@RestController
public class EmployeeCardController extends BaseAccController {
    private static final Logger log = LoggerFactory.getLogger(EmployeeCardController.class);
    private final AccountClient accountClient;

    public EmployeeCardController(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    /**
     * 接收员工码开卡通知。
     *
     * <p>请求以 {@code multipart/form-data} 提交 APP 同款公共字段，业务参数放在
     * {@code bizData} 中，例如：
     * {@code {"cardList":[{"phone":"13800138000","cardNo":"QD20240001","cardStatus":1}]}}。</p>
     *
     * @param request ACC 公共 FormData 请求
     */
    @PostMapping(path = "/employee_card/notify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeCardNotifyResult employeeCardNotify(@ModelAttribute ItpCommonFormRequest request) {
        log.info("接收员工码开卡通知，请求参数：{}", request);
        EmployeeCardNotifyReqDTO bizData = parseBizData(request, EmployeeCardNotifyReqDTO.class);
        return accountClient.notifyEmployeeCardStatus(bizData);
    }

    /**
     * 接收员工信息变更通知并剥离 ACC 公共消息头。
     */
    @PostMapping(path = "/employee_card/update_notify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult employeeInfoUpdateNotify(@ModelAttribute ItpCommonFormRequest request) {
        log.info("接收员工信息变更通知，请求参数：{}", request);
        EmployeeInfoUpdateNotifyReqDTO bizData = parseBizData(request, EmployeeInfoUpdateNotifyReqDTO.class);
        return accountClient.updateEmployeeInfo(bizData);
    }
}
