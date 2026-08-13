package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AccountAppService;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 更换第三方渠道码默认支付方式接口入口。
 *
 * <p>对应接口 IF8A-77，地址为 /app/requestUpdateChannelDefaultContract。</p>
 */
@RestController
@RequestMapping("/app")
public class AppChannelDefaultContractController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppChannelDefaultContractController.class);

    private final AccountAppService accountAppService;

    public AppChannelDefaultContractController(AccountAppService accountAppService) {
        this.accountAppService = accountAppService;
    }

    /**
     * IF8A-77 更换第三方渠道码默认支付方式。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestUpdateChannelDefaultContractReqDTO} JSON
     * @return 更换第三方渠道码默认支付方式结果
     */
    @PostMapping("/requestUpdateChannelDefaultContract")
    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-77 更换第三方渠道码默认支付方式, 请求参数: {}", request);
        return accountAppService.requestUpdateChannelDefaultContract(parseBizData(request, RequestUpdateChannelDefaultContractReqDTO.class));
    }
}
