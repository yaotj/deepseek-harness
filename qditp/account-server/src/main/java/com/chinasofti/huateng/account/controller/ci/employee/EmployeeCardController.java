package com.chinasofti.huateng.account.controller.ci.employee;

import com.chinasofti.huateng.account.service.EmployeeCardService;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyResult;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;
import com.chinasofti.huateng.common.response.CommonResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class EmployeeCardController {
    private final EmployeeCardService employeeCardService;

    public EmployeeCardController(EmployeeCardService employeeCardService) {
        this.employeeCardService = employeeCardService;
    }


    @PostMapping("/employeeCard/notify")
    public EmployeeCardNotifyResult notifyEmployeeCardStatus(@RequestBody EmployeeCardNotifyReqDTO request) {
        return employeeCardService.notifyEmployeeCardStatus(request);
    }


    @PostMapping("/employeeCard/query")
    public EmployeeCardQueryResult queryEmployeeCard(@RequestBody EmployeeCardQueryReqDTO request) {
        return employeeCardService.queryEmployeeCard(request);
    }


    @PostMapping("/employeeCard/activate")
    public CommonResult activateEmployeeCard(@RequestBody EmployeeCardActivateReqDTO request) {
        return employeeCardService.activateEmployeeCard(request);
    }


    @PostMapping("/employeeCard/updateNotify")
    public CommonResult updateEmployeeInfo(@RequestBody EmployeeInfoUpdateNotifyReqDTO request) {
        return employeeCardService.updateEmployeeInfo(request);
    }
}
