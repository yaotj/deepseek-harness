package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AccountAppService;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 员工码接口入口。
 */
@RestController
@RequestMapping("/ci/app")
public class AppEmployeeCardController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppEmployeeCardController.class);

    private final AccountAppService accountAppService;

    public AppEmployeeCardController(AccountAppService accountAppService) {
        this.accountAppService = accountAppService;
    }

    /**
     * 查询员工码信息。
     *
     * <p>FEP 仅解析 form-data 公共报文中的 {@code bizData}，不向 account-server 透传公共请求头字段。</p>
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link EmployeeCardQueryReqDTO} JSON
     * @return 员工码资料
     */
    @PostMapping("/employee_card/query")
    public EmployeeCardQueryResult queryEmployeeCard(@ModelAttribute CommonFormRequest request) {
        EmployeeCardQueryReqDTO bizData = parseBizData(request, EmployeeCardQueryReqDTO.class);
        log.info("员工码信息查询, cardNo={}", bizData.getCardNo());
        return accountAppService.queryEmployeeCard(bizData);
    }
}
