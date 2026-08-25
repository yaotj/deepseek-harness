package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.PhoneChangeAppService;
import com.chinasofti.huateng.model.app.UpdatePhoneReqDTO;
import com.chinasofti.huateng.model.app.UpdatePhoneResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手机号更换接口入口。
 *
 * <p>支持 {@code /ci/app} 和 {@code /app} 两条路径。</p>
 */
@RestController
public class PhoneChangeController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(PhoneChangeController.class);

    private final PhoneChangeAppService phoneChangeAppService;

    public PhoneChangeController(PhoneChangeAppService phoneChangeAppService) {
        this.phoneChangeAppService = phoneChangeAppService;
    }

    @PostMapping({"/ci/app/updatePhone", "/app/updatePhone"})
    public UpdatePhoneResult updatePhone(@ModelAttribute CommonFormRequest request) {
        UpdatePhoneReqDTO bizData = parseBizData(request, UpdatePhoneReqDTO.class);
        log.info("IF8A-XX 更换手机号, 请求参数: thirdUserId={}, newMsisdn={}",
                bizData.getThirdUserId(), bizData.getNewMsisdn());
        boolean success = phoneChangeAppService.updatePhone(bizData.getThirdUserId(), bizData.getNewMsisdn());
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
