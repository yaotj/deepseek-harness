package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.fep.app.service.PhoneChangeAppService;
import com.chinasofti.huateng.model.app.UpdatePhoneReqDTO;
import com.chinasofti.huateng.model.app.UpdatePhoneResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 更换手机号接口入口（if8a_76）。
 */
@RestController
public class PhoneChangeController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(PhoneChangeController.class);

    private final PhoneChangeAppService phoneChangeAppService;

    public PhoneChangeController(PhoneChangeAppService phoneChangeAppService) {
        this.phoneChangeAppService = phoneChangeAppService;
    }

    @PostMapping("/app/changePhone")
    public UpdatePhoneResult updatePhone(@ModelAttribute ItpCommonFormRequest request) {
        UpdatePhoneReqDTO bizData = parseBizData(request, UpdatePhoneReqDTO.class);
        String thirdUserId = bizData.getThirdUserId();
        String newPhone = bizData.getNewPhone() == null ? null : bizData.getNewPhone().trim();
        log.info("if8a_76 更换手机号, 请求参数: thirdUserId={}, newPhone={}, rawBizData={}",
                thirdUserId, newPhone, request == null ? null : request.getBizData());
        if (!StringUtils.hasText(newPhone)) {
            log.warn("if8a_76 缺少规范字段 newPhone, rawBizData={}",
                    request == null ? null : request.getBizData());
        }
        boolean success = phoneChangeAppService.updatePhone(thirdUserId, newPhone);
        UpdatePhoneResult result = new UpdatePhoneResult();
        if (success) {
            result.setRetCode("0000");
            result.setRetMsg("更换手机号成功");
        } else {
            result.setRetCode("9999");
            result.setRetMsg("更换手机号失败");
        }
        return result;
    }
}
