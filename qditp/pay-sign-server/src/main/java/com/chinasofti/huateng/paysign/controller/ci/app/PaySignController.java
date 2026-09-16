package com.chinasofti.huateng.paysign.controller.ci.app;

import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaySignController {
    private static final Logger log = LoggerFactory.getLogger(PaySignController.class);

    private final PaySignService paySignService;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public PaySignController(PaySignService paySignService) {
        this.paySignService = paySignService;
    }

    @PostMapping("/requestSignInfo")
    public RequestSignInfoResult requestSignInfo(@RequestBody RequestSignInfoReqDTO request) {
        log.info("接收到请求签约信息报文: {}", request);
        // 地铁APP专属入口，固定签约渠道为 METRO_APP
        return paySignService.requestSignInfo(request, SignChannelEnum.METRO_APP.getCode());
    }

    @GetMapping("/querySignInfoBySeq")
    public PaySignInfoDTO querySignInfoBySeq(@RequestParam String requestSignSeq) {
        log.info("接收到查询签约信息请求: requestSignSeq={}", requestSignSeq);
        return paySignService.querySignInfoBySeq(requestSignSeq);
    }
}
