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
 *
 * <p>严格对齐《青岛地铁-ITP与APP接口规范R6》if8a_76：路径只有 {@code /app/changePhone}，
 * {@code bizData} 只认 {@code newPhone} + {@code thirdUserId}（表117），应答只有
 * {@code retCode} + {@code retMsg}（表118）。</p>
 *
 * <p><b>NEVER</b> 再加 {@code updatePhone} / {@code /ci/app} 路径别名或 {@code newMsisdn}
 * 等字段别名——2026-09-09 曾为兼容上游误传临时放宽过 4 条路径 + 11 个字段名，
 * 与规范核对后已全部收回。上游传错字段时看日志里的 {@code rawBizData} 定位，
 * 不要再靠放宽入参掩盖问题。</p>
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
